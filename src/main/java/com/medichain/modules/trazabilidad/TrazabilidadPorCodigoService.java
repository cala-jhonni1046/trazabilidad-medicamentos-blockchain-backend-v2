package com.medichain.modules.trazabilidad;

import com.medichain.exceptions.ConsultaInvalidaException;
import com.medichain.exceptions.ResourceNotFoundException;
import com.medichain.modules.bulto.Bulto;
import com.medichain.modules.bulto.BultoService;
import com.medichain.modules.cuarentena.CuarentenaRepository;
import com.medichain.modules.despachologistico.DespachoLogisticoRepository;
import com.medichain.modules.lote.Lote;
import com.medichain.modules.lote.LoteService;
import com.medichain.modules.unidadtrazable.UnidadTrazable;
import com.medichain.modules.unidadtrazable.UnidadTrazableService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Servicio TrazabilidadPorCodigoService en MediChain (B12e).
 * Busca por código un lote, un bulto o una caja (GTIN + serie) y devuelve sus eventos de la cadena.
 * <p>
 * Alcance: lo que ya ve cada rol de ESA entidad. La encuentra por los services del lote, del bulto
 * y de la caja (la Sede y los inspectores, todo; cada empresa, lo suyo), así que lo ajeno no
 * aparece y responde 404 como en el resto de la API. Los eventos son los mismos que los de
 * {@code listarEventos} (con la lista blanca de datos: nada de pacientes, R13).
 * <p>
 * Qué eventos (por entidad, orden de número):
 * <ul>
 *   <li>lote: los suyos y los de sus medidas (cuarentena, recall);</li>
 *   <li>bulto: los suyos, los de cada viaje que lo llevó y los de las medidas que lo incluyen;</li>
 *   <li>caja: los suyos, los de su lote (y sus medidas) y los de su bulto actual (con sus viajes y medidas).
 *       Si la caja salió de un bulto que se desarmó, esos eventos ya no se alcanzan: la caja solo recuerda el actual.</li>
 * </ul>
 */
@Service
public class TrazabilidadPorCodigoService {

    /** Máximo de eventos por coincidencia (si hay más, el resultado trae truncado = true). */
    static final int MAXIMO_EVENTOS = 1000;

    private final EventoTrazabilidadRepository eventoRepository;
    private final EventoTrazabilidadMapper eventoMapper;
    private final CuarentenaRepository cuarentenaRepository;
    private final DespachoLogisticoRepository despachoRepository;
    private final LoteService loteService;
    private final BultoService bultoService;
    private final UnidadTrazableService unidadService;

    @Autowired
    public TrazabilidadPorCodigoService(EventoTrazabilidadRepository eventoRepository,
                                        EventoTrazabilidadMapper eventoMapper,
                                        CuarentenaRepository cuarentenaRepository,
                                        DespachoLogisticoRepository despachoRepository, LoteService loteService,
                                        BultoService bultoService, UnidadTrazableService unidadService) {
        this.eventoRepository = eventoRepository;
        this.eventoMapper = eventoMapper;
        this.cuarentenaRepository = cuarentenaRepository;
        this.despachoRepository = despachoRepository;
        this.loteService = loteService;
        this.bultoService = bultoService;
        this.unidadService = unidadService;
    }

    /**
     * Busca por UNO de: código de lote (con laboratorio opcional), código de bulto o GTIN + serie.
     * Ninguno, más de uno o un GTIN sin serie (o al revés) → 400. Sin coincidencias visibles → 404.
     */
    @Transactional(readOnly = true)
    public List<ResultadoRecorridoDTO> buscar(String lote, UUID laboratorioId, String bulto, String gtin, String serie) {
        boolean porLote = hayTexto(lote);
        boolean porBulto = hayTexto(bulto);
        boolean porCaja = hayTexto(gtin) || hayTexto(serie);
        if ((porLote ? 1 : 0) + (porBulto ? 1 : 0) + (porCaja ? 1 : 0) != 1) {
            throw new ConsultaInvalidaException("Indicá UNO de: lote, bulto, o gtin y serie");
        }
        if (laboratorioId != null && !porLote) {
            throw new ConsultaInvalidaException("laboratorioId solo se usa junto con lote");
        }
        if (porCaja && !(hayTexto(gtin) && hayTexto(serie))) {
            throw new ConsultaInvalidaException("Para buscar una caja indicá gtin y serie");
        }
        List<ResultadoRecorridoDTO> resultados = new ArrayList<>();
        if (porLote) {
            for (Lote encontrado : loteService.visiblesPorCodigo(lote.trim(), laboratorioId)) {
                resultados.add(resultadoDeLote(encontrado));
            }
        } else if (porBulto) {
            bultoService.visiblePorCodigo(bulto.trim()).ifPresent(b -> resultados.add(resultadoDeBulto(b)));
        } else {
            unidadService.visiblePorSerie(gtin.trim(), serie.trim()).ifPresent(c -> resultados.add(resultadoDeCaja(c)));
        }
        if (resultados.isEmpty()) {
            throw new ResourceNotFoundException("No hay un lote, bulto o caja con ese código que puedas ver");
        }
        return resultados;
    }

    private ResultadoRecorridoDTO resultadoDeLote(Lote lote) {
        return armar(TipoCodigoBuscado.LOTE, lote.getCodigo(), lote.getId(), lote, idsDeLote(lote));
    }

    private ResultadoRecorridoDTO resultadoDeBulto(Bulto bulto) {
        return armar(TipoCodigoBuscado.BULTO, bulto.getCodigo(), bulto.getId(), bulto.getLote(), idsDeBulto(bulto));
    }

    private ResultadoRecorridoDTO resultadoDeCaja(UnidadTrazable caja) {
        Set<UUID> ids = new LinkedHashSet<>();
        ids.add(caja.getId());
        ids.addAll(idsDeLote(caja.getLote()));
        if (caja.getBulto() != null) {
            ids.addAll(idsDeBulto(caja.getBulto()));
        }
        return armar(TipoCodigoBuscado.CAJA, caja.getSerie(), caja.getId(), caja.getLote(), ids);
    }

    /** El lote y sus medidas (cuarentena, recall). */
    private Set<UUID> idsDeLote(Lote lote) {
        Set<UUID> ids = new LinkedHashSet<>();
        ids.add(lote.getId());
        ids.addAll(cuarentenaRepository.idsDelLote(lote.getId()));
        return ids;
    }

    /** El bulto, sus viajes y las medidas que lo incluyen. */
    private Set<UUID> idsDeBulto(Bulto bulto) {
        Set<UUID> ids = new LinkedHashSet<>();
        ids.add(bulto.getId());
        ids.addAll(despachoRepository.idsDelBulto(bulto.getId()));
        ids.addAll(cuarentenaRepository.idsDelBulto(bulto.getId()));
        return ids;
    }

    private ResultadoRecorridoDTO armar(TipoCodigoBuscado tipo, String codigo, UUID entidadId, Lote lote, Set<UUID> ids) {
        // Se pide uno más que el máximo para saber si había más.
        List<EventoTrazabilidad> eventos = eventoRepository.findByEntidadIdInOrderByNumeroAsc(ids,
                Limit.of(MAXIMO_EVENTOS + 1));
        boolean truncado = eventos.size() > MAXIMO_EVENTOS;
        ResultadoRecorridoDTO dto = new ResultadoRecorridoDTO();
        dto.setTipo(tipo);
        dto.setCodigo(codigo);
        dto.setEntidadId(entidadId);
        dto.setLoteCodigo(lote.getCodigo());
        dto.setMedicamentoNombre(lote.getMedicamento().getNombreComercial());
        dto.setLaboratorioRazonSocial(lote.getLaboratorio().getRazonSocial());
        dto.setEventos((truncado ? eventos.subList(0, MAXIMO_EVENTOS) : eventos).stream()
                .map(eventoMapper::toResponseDTO).toList());
        dto.setTruncado(truncado);
        return dto;
    }

    private static boolean hayTexto(String texto) {
        return texto != null && !texto.isBlank();
    }
}
