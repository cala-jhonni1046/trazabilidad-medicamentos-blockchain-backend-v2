package com.medichain.modules.bulto;

import com.medichain.exceptions.ReglaNegocioException;
import com.medichain.exceptions.ResourceNotFoundException;
import com.medichain.modules.auth.UsuarioAutenticado;
import com.medichain.modules.cuarentena.EvaluadorBloqueo;
import com.medichain.modules.enlacecuit.EnlaceCuit;
import com.medichain.modules.enlacecuit.EnlaceCuitRepository;
import com.medichain.modules.lote.EstadoLote;
import com.medichain.modules.lote.Lote;
import com.medichain.modules.lote.LoteRepository;
import com.medichain.modules.trazabilidad.DatosEventos;
import com.medichain.modules.trazabilidad.HashUtil;
import com.medichain.modules.trazabilidad.RegistradorEventos;
import com.medichain.modules.trazabilidad.TipoEvento;
import com.medichain.modules.unidadtrazable.EstadoUnidad;
import com.medichain.modules.unidadtrazable.UnidadTrazable;
import com.medichain.modules.unidadtrazable.UnidadTrazableRepository;
import com.medichain.utils.seguridad.UsuarioActual;
import com.medichain.utils.seguridad.VerificadorEmpresa;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Servicio BultoService en MediChain.
 * Armado (R6) y desarmado de bultos, y consulta por rol: SEDE e INSPECTOR
 * ven todos; el LABORATORIO, los de sus lotes; DISTRIBUIDOR y FARMACIA, los
 * de circuitos donde participan (desde ARMADO).
 * R6: cajas de UN lote LIBERADO del laboratorio, todas EN_LABORATORIO, sin
 * bulto y no bloqueadas (R10); circuito APROBADO del laboratorio con sus
 * tres empresas HABILITADA. Un bulto despachado no cambia contenido ni destino.
 */
@Service
public class BultoService {

    private final BultoRepository repository;
    private final LoteRepository loteRepository;
    private final EnlaceCuitRepository enlaceCuitRepository;
    private final UnidadTrazableRepository unidadTrazableRepository;
    private final EvaluadorBloqueo evaluadorBloqueo;
    private final UsuarioActual usuarioActual;
    private final VerificadorEmpresa verificadorEmpresa;
    private final RegistradorEventos registradorEventos;

    @Autowired
    public BultoService(BultoRepository repository, LoteRepository loteRepository,
                        EnlaceCuitRepository enlaceCuitRepository, UnidadTrazableRepository unidadTrazableRepository,
                        EvaluadorBloqueo evaluadorBloqueo, UsuarioActual usuarioActual,
                        VerificadorEmpresa verificadorEmpresa, RegistradorEventos registradorEventos) {
        this.repository = repository;
        this.loteRepository = loteRepository;
        this.enlaceCuitRepository = enlaceCuitRepository;
        this.unidadTrazableRepository = unidadTrazableRepository;
        this.evaluadorBloqueo = evaluadorBloqueo;
        this.usuarioActual = usuarioActual;
        this.verificadorEmpresa = verificadorEmpresa;
        this.registradorEventos = registradorEventos;
    }

    /** Devuelve una página de bultos según el rol del usuario. */
    @Transactional(readOnly = true)
    public Page<Bulto> getAll(Pageable pageable) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        return switch (actual.getRol()) {
            case SEDE_CENTRAL, INSPECTOR -> repository.findAll(pageable);
            case LABORATORIO -> repository.findByLoteLaboratorioId(actual.getEmpresaId(), pageable);
            case DISTRIBUIDOR -> repository.findByDestinoDistribuidorId(actual.getEmpresaId(), pageable);
            case FARMACIA -> repository.findByDestinoFarmaciaId(actual.getEmpresaId(), pageable);
            case PACIENTE -> Page.empty(pageable);
        };
    }

    /** Busca un bulto por id; si no le corresponde al usuario, 404. */
    @Transactional(readOnly = true)
    public Bulto getById(UUID id) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        Bulto bulto = buscar(id);
        if (!puedeVer(actual, bulto)) {
            throw new ResourceNotFoundException("Bulto no encontrado con id: " + id);
        }
        return bulto;
    }

    /**
     * Arma un bulto (R6). Lote o circuito de otro laboratorio → 404. Código
     * BUL-0001 por secuencia; cantidad = cajas elegidas.
     */
    @Transactional
    public Bulto armar(BultoRequestDTO dto) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        verificadorEmpresa.exigirHabilitada(actual.getEmpresaId());

        UUID loteId = dto.getLoteId();
        Lote lote = loteRepository.findById(loteId)
                .filter(l -> l.getLaboratorio().getId().equals(actual.getEmpresaId()))
                .orElseThrow(() -> new ResourceNotFoundException("Lote no encontrado con id: " + loteId));
        if (lote.getEstado() != EstadoLote.LIBERADO) {
            throw new ReglaNegocioException("R6", "El lote " + lote.getCodigo() + " no está LIBERADO (estado "
                    + lote.getEstado() + ")");
        }
        Optional<String> bloqueo = evaluadorBloqueo.bloqueoDeLote(lote);
        if (bloqueo.isPresent()) {
            throw new ReglaNegocioException("R10", "No se puede armar el bulto: " + bloqueo.get());
        }

        UUID circuitoId = dto.getCircuitoId();
        EnlaceCuit circuito = enlaceCuitRepository.findById(circuitoId)
                .filter(c -> c.getLaboratorio().getId().equals(actual.getEmpresaId()))
                .orElseThrow(() -> new ResourceNotFoundException("Circuito no encontrado con id: " + circuitoId));
        if (!circuito.estaAprobado() || !circuito.puedeRehabilitarse()) {
            throw new ReglaNegocioException("R6", "El circuito " + circuito.getCodigo()
                    + " debe estar APROBADO y con sus tres empresas HABILITADA");
        }

        List<UnidadTrazable> cajas = dto.getSeries() != null
                ? cajasPorSerie(lote, dto.getSeries())
                : cajasDisponibles(lote, dto.getCantidad());

        String codigo = String.format("BUL-%04d", repository.siguienteNumeroCodigo());
        Bulto bulto = repository.save(new Bulto(codigo, cajas.size(), dto.getPrecinto(), lote, circuito));
        List<String> series = new ArrayList<>(cajas.size());
        for (UnidadTrazable caja : cajas) {
            caja.asignarABulto(bulto);
            series.add(caja.getSerie());
        }
        unidadTrazableRepository.saveAll(cajas);
        registradorEventos.registrar(TipoEvento.BULTO_ARMADO, "Bulto", bulto.getId(),
                DatosEventos.bultoArmado(bulto, HashUtil.seriesHash(series)), actual);
        return bulto;
    }

    /** Desarma un bulto ARMADO sin viaje: las cajas vuelven a quedar libres en el laboratorio. */
    @Transactional
    public Bulto desarmar(UUID id) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        Bulto bulto = buscar(id);
        if (!bulto.getLote().getLaboratorio().getId().equals(actual.getEmpresaId())) {
            throw new ResourceNotFoundException("Bulto no encontrado con id: " + id);
        }
        verificadorEmpresa.exigirHabilitada(actual.getEmpresaId());
        bulto.desarmar();
        List<UnidadTrazable> cajas = unidadTrazableRepository.findByBultoId(id);
        for (UnidadTrazable caja : cajas) {
            caja.liberarDeBulto();
        }
        unidadTrazableRepository.saveAll(cajas);
        Bulto guardado = repository.save(bulto);
        registradorEventos.registrar(TipoEvento.BULTO_DESARMADO, "Bulto", id,
                DatosEventos.bultoDesarmado(guardado), actual);
        return guardado;
    }

    /**
     * Cajas por serie (R6): sin repetidas, todas de ESE lote, EN_LABORATORIO
     * y sin bulto. Informa cuáles fallan.
     */
    private List<UnidadTrazable> cajasPorSerie(Lote lote, List<String> series) {
        Set<String> unicas = new HashSet<>(series);
        if (unicas.size() != series.size()) {
            throw new ReglaNegocioException("R6", "La lista de series tiene cajas repetidas");
        }
        List<UnidadTrazable> cajas = unidadTrazableRepository.findByLoteIdAndSerieIn(lote.getId(), unicas);
        if (cajas.size() != unicas.size()) {
            Set<String> encontradas = new HashSet<>();
            cajas.forEach(c -> encontradas.add(c.getSerie()));
            List<String> faltan = series.stream().filter(s -> !encontradas.contains(s)).limit(10).toList();
            throw new ReglaNegocioException("R6", "Estas series no son cajas del lote " + lote.getCodigo() + ": " + faltan);
        }
        List<String> noDisponibles = cajas.stream()
                .filter(c -> c.getEstado() != EstadoUnidad.EN_LABORATORIO || c.getBulto() != null)
                .map(UnidadTrazable::getSerie).limit(10).toList();
        if (!noDisponibles.isEmpty()) {
            throw new ReglaNegocioException("R6", "Estas cajas ya están en otro bulto o salieron del laboratorio: "
                    + noDisponibles);
        }
        return cajas;
    }

    /** Primeras N cajas disponibles del lote (EN_LABORATORIO, sin bulto), por orden de serie. */
    private List<UnidadTrazable> cajasDisponibles(Lote lote, int cantidad) {
        List<UnidadTrazable> cajas = unidadTrazableRepository.findByLoteIdAndEstadoAndBultoIsNullOrderBySerieAsc(
                lote.getId(), EstadoUnidad.EN_LABORATORIO, Limit.of(cantidad));
        if (cajas.size() < cantidad) {
            throw new ReglaNegocioException("R6", "El lote " + lote.getCodigo() + " solo tiene " + cajas.size()
                    + " cajas disponibles (pediste " + cantidad + ")");
        }
        return cajas;
    }

    /** Busca el bulto o lanza 404. */
    private Bulto buscar(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bulto no encontrado con id: " + id));
    }

    /** SEDE e INSPECTOR ven todo; el laboratorio, sus bultos; DIST y FARM, los de sus circuitos. */
    private boolean puedeVer(UsuarioAutenticado actual, Bulto bulto) {
        UUID empresaId = actual.getEmpresaId();
        return switch (actual.getRol()) {
            case SEDE_CENTRAL, INSPECTOR -> true;
            case LABORATORIO -> bulto.getLote().getLaboratorio().getId().equals(empresaId);
            case DISTRIBUIDOR -> bulto.getDestino().getDistribuidor().getId().equals(empresaId);
            case FARMACIA -> bulto.getDestino().getFarmacia().getId().equals(empresaId);
            case PACIENTE -> false;
        };
    }
}
