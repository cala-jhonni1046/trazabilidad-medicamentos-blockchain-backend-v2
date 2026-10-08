package com.medichain.modules.cuarentena;

import com.medichain.exceptions.ReglaNegocioException;
import com.medichain.exceptions.ResourceNotFoundException;
import com.medichain.modules.auth.UsuarioAutenticado;
import com.medichain.modules.bulto.Bulto;
import com.medichain.modules.bulto.BultoRepository;
import com.medichain.modules.despachologistico.TramoDespacho;
import com.medichain.modules.inspectoranmat.InspectorAnmat;
import com.medichain.modules.inspectoranmat.InspectorAnmatRepository;
import com.medichain.modules.lote.Lote;
import com.medichain.modules.lote.LoteRepository;
import com.medichain.modules.recepcion.MotivoRechazoRecepcion;
import com.medichain.modules.recepcion.Recepcion;
import com.medichain.modules.recepcion.RecepcionRepository;
import com.medichain.modules.reporteciudadano.ReporteCiudadano;
import com.medichain.modules.reporteciudadano.ReporteCiudadanoRepository;
import com.medichain.modules.trazabilidad.DatosEventos;
import com.medichain.modules.trazabilidad.RegistradorEventos;
import com.medichain.modules.trazabilidad.TipoEvento;
import com.medichain.modules.unidadtrazable.UnidadTrazable;
import com.medichain.modules.unidadtrazable.UnidadTrazableRepository;
import com.medichain.utils.seguridad.UsuarioActual;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Servicio CuarentenaService en MediChain (R8, R9, R12, R14).
 * <ul>
 *   <li>Abrir (manual, alcance LOTE): un inspector de la provincia del
 *       laboratorio, solo sobre un lote LIBERADO, que pasa a CUARENTENA. Queda
 *       como revisor. Puede nacer de un reporte ciudadano que investiga.</li>
 *   <li>Bandeja, tomar y dictaminar (solo un inspector, R12): la medida es de
 *       su provincia (D1, contra la base) o la tomó él.</li>
 *   <li>Levantar: LOTE → el lote vuelve a LIBERADO. BULTO (recepción
 *       rechazada) → "aceptación por dictamen" SOLO si el rechazo fue
 *       únicamente por PRECINTO_ROTO; si incluyó temperatura o cantidad →
 *       409 R8 (solo cabe recall). Ruptura de frío (R9) y robo (R14) no se
 *       levantan.</li>
 *   <li>Recall: LOTE → el lote pasa a RECALL (todas sus cajas bloqueadas,
 *       estén donde estén). DESPACHO o BULTO → solo esos bultos, para siempre;
 *       el lote no cambia.</li>
 * </ul>
 * Nada se edita hacia atrás: cada decisión es un evento nuevo.
 * TODO: la Sede asigna medidas de provincias sin inspectores.
 */
@Service
public class CuarentenaService {

    private final CuarentenaRepository repository;
    private final LoteRepository loteRepository;
    private final BultoRepository bultoRepository;
    private final UnidadTrazableRepository unidadTrazableRepository;
    private final RecepcionRepository recepcionRepository;
    private final ReporteCiudadanoRepository reporteCiudadanoRepository;
    private final InspectorAnmatRepository inspectorAnmatRepository;
    private final UsuarioActual usuarioActual;
    private final RegistradorEventos registradorEventos;

    @Autowired
    public CuarentenaService(CuarentenaRepository repository, LoteRepository loteRepository,
                             BultoRepository bultoRepository, UnidadTrazableRepository unidadTrazableRepository,
                             RecepcionRepository recepcionRepository,
                             ReporteCiudadanoRepository reporteCiudadanoRepository,
                             InspectorAnmatRepository inspectorAnmatRepository, UsuarioActual usuarioActual,
                             RegistradorEventos registradorEventos) {
        this.repository = repository;
        this.loteRepository = loteRepository;
        this.bultoRepository = bultoRepository;
        this.unidadTrazableRepository = unidadTrazableRepository;
        this.recepcionRepository = recepcionRepository;
        this.reporteCiudadanoRepository = reporteCiudadanoRepository;
        this.inspectorAnmatRepository = inspectorAnmatRepository;
        this.usuarioActual = usuarioActual;
        this.registradorEventos = registradorEventos;
    }

    /** Página de medidas: SEDE e INSPECTOR todas; una empresa, las que la afectan. */
    @Transactional(readOnly = true)
    public Page<Cuarentena> getAll(EstadoCuarentena estado, Pageable pageable) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        return switch (actual.getRol()) {
            case SEDE_CENTRAL, INSPECTOR -> repository.findPorEstado(estado, pageable);
            case LABORATORIO, DISTRIBUIDOR, FARMACIA ->
                    repository.findVisiblesParaEmpresa(actual.getEmpresaId(), estado, pageable);
            case PACIENTE -> Page.empty(pageable);
        };
    }

    /** Busca una medida por id; si no afecta a algo de la empresa del usuario, 404. */
    @Transactional(readOnly = true)
    public Cuarentena getById(UUID id) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        Cuarentena cuarentena = buscar(id);
        if (!puedeVer(actual, cuarentena)) {
            throw new ResourceNotFoundException("Cuarentena no encontrada con id: " + id);
        }
        return cuarentena;
    }

    /** Bandeja del inspector: medidas ACTIVA de su provincia sin tomar, más las que tomó él. */
    @Transactional(readOnly = true)
    public Page<Cuarentena> bandeja(Pageable pageable) {
        InspectorAnmat inspector = inspectorActivo(usuarioActual.obtener());
        return repository.findBandeja(inspector.getProvincia(), inspector.getId(), pageable);
    }

    /**
     * Abre una cuarentena manual de LOTE. Lote de otra provincia → 404; lote
     * que no está LIBERADO (pendiente, ya en cuarentena o en recall) →
     * TRANSICION_INVALIDA. Si nace de un reporte, el reporte tiene que estar
     * EN_INVESTIGACION tomado por este inspector y su caja ser de este lote.
     */
    @Transactional
    public Cuarentena abrir(CuarentenaRequestDTO dto) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        InspectorAnmat inspector = inspectorActivo(actual);
        UUID loteId = dto.getLoteId();
        Lote lote = loteRepository.findById(loteId)
                .filter(l -> l.getLaboratorio().getProvincia() == inspector.getProvincia())
                .orElseThrow(() -> new ResourceNotFoundException("Lote no encontrado con id: " + loteId));
        ReporteCiudadano reporte = null;
        if (dto.getReporteId() != null) {
            UUID reporteId = dto.getReporteId();
            reporte = reporteCiudadanoRepository.findById(reporteId)
                    .orElseThrow(() -> new ResourceNotFoundException("Reporte no encontrado con id: " + reporteId));
            reporte.exigirEnInvestigacionPor(inspector, "abrir una cuarentena desde el reporte");
            if (reporte.getUnidadTrazable() == null || !reporte.getUnidadTrazable().getLote().getId().equals(loteId)) {
                throw new ReglaNegocioException("TRANSICION_INVALIDA",
                        "La caja del reporte " + reporte.getCodigo() + " no es de este lote");
            }
        }
        lote.entrarEnCuarentena();
        loteRepository.save(lote);
        Cuarentena cuarentena = repository.save(
                Cuarentena.manualDeLote(lote, dto.getMotivo(), dto.getDescripcion(), inspector, reporte));
        registradorEventos.registrar(TipoEvento.CUARENTENA, "Cuarentena", cuarentena.getId(),
                DatosEventos.cuarentena(cuarentena), actual);
        return cuarentena;
    }

    /** Un inspector toma la medida para dictaminarla (D1). Evento CUARENTENA_TOMADA. */
    @Transactional
    public Cuarentena tomar(UUID id) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        InspectorAnmat inspector = inspectorActivo(actual);
        Cuarentena cuarentena = visibleParaInspector(id, inspector);
        cuarentena.tomar(inspector);
        Cuarentena guardada = repository.save(cuarentena);
        registradorEventos.registrar(TipoEvento.CUARENTENA_TOMADA, "Cuarentena", id,
                DatosEventos.cuarentenaTomada(guardada, inspector), actual);
        return guardada;
    }

    /** Levanta la medida con fundamento (R12). Ver efectos y restricciones en la descripción de la clase. */
    @Transactional
    public Cuarentena levantar(UUID id, String fundamento) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        InspectorAnmat inspector = inspectorActivo(actual);
        Cuarentena cuarentena = visibleParaInspector(id, inspector);
        Recepcion rechazo = null;
        // R8 se evalúa con la medida ya en condiciones de dictaminarse (si no, levantar() da TRANSICION_INVALIDA).
        if (cuarentena.getAlcance() == AlcanceCuarentena.BULTO && cuarentena.getEstado() == EstadoCuarentena.ACTIVA
                && cuarentena.esRevisor(inspector)) {
            rechazo = rechazoDelBulto(cuarentena);
            if (!rechazo.motivos().equals(List.of(MotivoRechazoRecepcion.PRECINTO_ROTO))) {
                throw new ReglaNegocioException("R8", "El rechazo fue por " + rechazo.motivos()
                        + ": solo se acepta por dictamen un rechazo únicamente por precinto roto. Solo cabe recall.");
            }
        }
        cuarentena.levantar(inspector, fundamento);
        String efecto = null;
        if (cuarentena.getAlcance() == AlcanceCuarentena.LOTE) {
            Lote lote = cuarentena.getLote();
            lote.levantarCuarentena();
            loteRepository.save(lote);
            efecto = "LOTE_LIBERADO";
        } else if (rechazo != null) {
            aceptarPorDictamen(cuarentena, rechazo.getDespacho().getTramo());
            efecto = "BULTOS_ACEPTADOS";
        }
        Cuarentena guardada = repository.save(cuarentena);
        registradorEventos.registrar(TipoEvento.CUARENTENA_LEVANTADA, "Cuarentena", id,
                DatosEventos.cuarentenaLevantada(guardada, fundamento, efecto), actual);
        return guardada;
    }

    /** Convierte la medida en recall con fundamento (R12). Ver efectos en la descripción de la clase. */
    @Transactional
    public Cuarentena recall(UUID id, String fundamento) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        InspectorAnmat inspector = inspectorActivo(actual);
        Cuarentena cuarentena = visibleParaInspector(id, inspector);
        cuarentena.convertirEnRecall(inspector, fundamento);
        long cajasAfectadas;
        if (cuarentena.getAlcance() == AlcanceCuarentena.LOTE) {
            Lote lote = cuarentena.getLote();
            lote.pasarARecall();
            loteRepository.save(lote);
            cajasAfectadas = unidadTrazableRepository.countByLoteId(lote.getId());
        } else {
            cajasAfectadas = unidadTrazableRepository.findByBultoIdIn(
                    cuarentena.getBultos().stream().map(Bulto::getId).toList()).size();
        }
        Cuarentena guardada = repository.save(cuarentena);
        registradorEventos.registrar(TipoEvento.RECALL, "Cuarentena", id,
                DatosEventos.recall(guardada, fundamento, cajasAfectadas), actual);
        return guardada;
    }

    /** La recepción que rechazó el bulto de una medida BULTO (la última no conforme). */
    private Recepcion rechazoDelBulto(Cuarentena cuarentena) {
        Bulto bulto = cuarentena.getBultos().iterator().next();
        List<Recepcion> recepciones = recepcionRepository.findByBultoIdOrderByFechaHoraAsc(bulto.getId());
        for (int i = recepciones.size() - 1; i >= 0; i--) {
            if (!recepciones.get(i).getConforme()) {
                return recepciones.get(i);
            }
        }
        throw new IllegalStateException("La medida BULTO no tiene una recepción rechazada asociada");
    }

    /**
     * Aceptación por dictamen: el bulto y sus cajas quedan como si la recepción
     * hubiera sido conforme, en la empresa que ya los tiene (tramo 1 →
     * EN_DEPOSITO; tramo 2 → RECIBIDO / EN_STOCK).
     */
    private void aceptarPorDictamen(Cuarentena cuarentena, TramoDespacho tramo) {
        boolean enDeposito = tramo == TramoDespacho.LAB_A_DISTRIBUIDOR;
        List<Bulto> bultos = new ArrayList<>(cuarentena.getBultos());
        for (Bulto bulto : bultos) {
            bulto.aceptarPorDictamen(tramo);
        }
        List<UnidadTrazable> cajas = unidadTrazableRepository.findByBultoIdIn(bultos.stream().map(Bulto::getId).toList());
        for (UnidadTrazable caja : cajas) {
            caja.aceptarPorDictamen(enDeposito);
        }
        bultoRepository.saveAll(bultos);
        unidadTrazableRepository.saveAll(cajas);
    }

    /** Busca la medida o lanza 404. */
    private Cuarentena buscar(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cuarentena no encontrada con id: " + id));
    }

    /** D1: inspector ACTIVO de la cuenta autenticada, leído de la base (no del token). */
    private InspectorAnmat inspectorActivo(UsuarioAutenticado actual) {
        return inspectorAnmatRepository.findByUsuarioId(actual.getUsuarioId())
                .filter(InspectorAnmat::estaActivo)
                .orElseThrow(() -> new AccessDeniedException("La cuenta no corresponde a un inspector activo"));
    }

    /** D1: la medida es de la provincia del inspector o él es su revisor; si no, 404. */
    private Cuarentena visibleParaInspector(UUID id, InspectorAnmat inspector) {
        Cuarentena cuarentena = buscar(id);
        if (cuarentena.getProvincia() != inspector.getProvincia() && !cuarentena.esRevisor(inspector)) {
            throw new ResourceNotFoundException("Cuarentena no encontrada con id: " + id);
        }
        return cuarentena;
    }

    /**
     * SEDE e INSPECTOR ven todo; una empresa, las medidas sobre sus lotes,
     * sobre los viajes que origina o sobre bultos de circuitos donde participa.
     */
    private boolean puedeVer(UsuarioAutenticado actual, Cuarentena cuarentena) {
        UUID empresaId = actual.getEmpresaId();
        boolean esSuLote = cuarentena.getLote() != null
                && cuarentena.getLote().getLaboratorio().getId().equals(empresaId);
        boolean esSuDespacho = cuarentena.getDespacho() != null
                && cuarentena.getDespacho().getOrigen().getId().equals(empresaId);
        boolean esDeSusCircuitos = cuarentena.getBultos().stream().anyMatch(b ->
                b.getDestino().getLaboratorio().getId().equals(empresaId)
                        || b.getDestino().getDistribuidor().getId().equals(empresaId)
                        || b.getDestino().getFarmacia().getId().equals(empresaId));
        return switch (actual.getRol()) {
            case SEDE_CENTRAL, INSPECTOR -> true;
            case LABORATORIO, DISTRIBUIDOR, FARMACIA -> esSuLote || esSuDespacho || esDeSusCircuitos;
            case PACIENTE -> false;
        };
    }
}
