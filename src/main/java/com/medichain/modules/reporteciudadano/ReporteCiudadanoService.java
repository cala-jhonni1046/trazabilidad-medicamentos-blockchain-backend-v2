package com.medichain.modules.reporteciudadano;

import com.medichain.exceptions.ResourceNotFoundException;
import com.medichain.modules.auth.UsuarioAutenticado;
import com.medichain.modules.cuarentena.Cuarentena;
import com.medichain.modules.cuarentena.CuarentenaRepository;
import com.medichain.modules.inspectoranmat.InspectorAnmat;
import com.medichain.modules.inspectoranmat.InspectorAnmatRepository;
import com.medichain.modules.trazabilidad.DatosEventos;
import com.medichain.modules.trazabilidad.RegistradorEventos;
import com.medichain.modules.trazabilidad.TipoEvento;
import com.medichain.modules.unidadtrazable.UnidadTrazable;
import com.medichain.modules.unidadtrazable.UnidadTrazableRepository;
import com.medichain.modules.usuario.RolUsuario;
import com.medichain.utils.seguridad.UsuarioActual;
import com.medichain.utils.seguridad.VerificadorUsuario;
import com.medichain.utils.validacion.Gs1Util;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

/**
 * Servicio ReporteCiudadanoService en MediChain (R12, R13).
 * El PACIENTE reporta lo que escaneó (GTIN + serie), aunque la caja no
 * exista (sospecha de falsificación), con la provincia donde la consiguió.
 * Un inspector de esa provincia (D1, contra la base) lo toma y lo cierra con
 * una conclusión; desde un reporte EN_INVESTIGACION puede abrir una
 * cuarentena de LOTE (CuarentenaService.abrir con reporteId).
 * El paciente ve solo sus reportes (ajeno → 404). R13: ni el paciente ni la
 * descripción van a la cadena.
 * TODO: la Sede asigna reportes de provincias sin inspectores.
 */
@Service
public class ReporteCiudadanoService {

    private final ReporteCiudadanoRepository repository;
    private final UnidadTrazableRepository unidadTrazableRepository;
    private final CuarentenaRepository cuarentenaRepository;
    private final InspectorAnmatRepository inspectorAnmatRepository;
    private final UsuarioActual usuarioActual;
    private final VerificadorUsuario verificadorUsuario;
    private final RegistradorEventos registradorEventos;

    @Autowired
    public ReporteCiudadanoService(ReporteCiudadanoRepository repository,
                                   UnidadTrazableRepository unidadTrazableRepository,
                                   CuarentenaRepository cuarentenaRepository,
                                   InspectorAnmatRepository inspectorAnmatRepository, UsuarioActual usuarioActual,
                                   VerificadorUsuario verificadorUsuario, RegistradorEventos registradorEventos) {
        this.repository = repository;
        this.unidadTrazableRepository = unidadTrazableRepository;
        this.cuarentenaRepository = cuarentenaRepository;
        this.inspectorAnmatRepository = inspectorAnmatRepository;
        this.usuarioActual = usuarioActual;
        this.verificadorUsuario = verificadorUsuario;
        this.registradorEventos = registradorEventos;
    }

    /** Página de reportes: SEDE e INSPECTOR todos; el PACIENTE, los suyos; empresas, ninguno. */
    @Transactional(readOnly = true)
    public Page<ReporteCiudadano> getAll(Pageable pageable) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        return switch (actual.getRol()) {
            case SEDE_CENTRAL, INSPECTOR -> repository.findAll(pageable);
            case PACIENTE -> repository.findByPacienteId(actual.getUsuarioId(), pageable);
            case LABORATORIO, DISTRIBUIDOR, FARMACIA -> Page.empty(pageable);
        };
    }

    /** Busca un reporte por id; un paciente que no es su autor recibe 404. */
    @Transactional(readOnly = true)
    public ReporteCiudadano getById(UUID id) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        ReporteCiudadano reporte = buscar(id);
        boolean veTodo = actual.getRol() == RolUsuario.SEDE_CENTRAL || actual.getRol() == RolUsuario.INSPECTOR;
        if (!veTodo && !reporte.getPaciente().getId().equals(actual.getUsuarioId())) {
            throw new ResourceNotFoundException("Reporte no encontrado con id: " + id);
        }
        return reporte;
    }

    /** Bandeja del inspector: ABIERTO de su provincia, más los que investiga él. */
    @Transactional(readOnly = true)
    public Page<ReporteCiudadano> bandeja(Pageable pageable) {
        InspectorAnmat inspector = inspectorActivo(usuarioActual.obtener());
        return repository.findBandeja(inspector.getProvincia(), inspector.getId(), pageable);
    }

    /**
     * El paciente presenta un reporte. Código REP-0001 por secuencia. Si GTIN +
     * serie existen, el reporte queda vinculado a la caja. Evento
     * REPORTE_CIUDADANO sin paciente ni descripción.
     */
    @Transactional
    public ReporteCiudadano reportar(ReporteCiudadanoRequestDTO dto) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        String gtin = Gs1Util.normalizar(dto.getGtin());
        UnidadTrazable caja = unidadTrazableRepository.findByGtinAndSerie(gtin, dto.getSerie()).orElse(null);
        String codigo = String.format("REP-%04d", repository.siguienteNumeroCodigo());
        ReporteCiudadano reporte = repository.save(new ReporteCiudadano(codigo,
                verificadorUsuario.obtener(actual.getUsuarioId()), gtin, dto.getSerie(), dto.getMotivo(),
                dto.getProvincia(), dto.getDescripcion(), caja));
        // El actor es un PACIENTE: RegistradorEventos lo omite (R13).
        registradorEventos.registrar(TipoEvento.REPORTE_CIUDADANO, "ReporteCiudadano", reporte.getId(),
                DatosEventos.reporteCiudadano(reporte), actual);
        return reporte;
    }

    /** Un inspector de la provincia del reporte lo toma: ABIERTO → EN_INVESTIGACION. */
    @Transactional
    public ReporteCiudadano tomar(UUID id) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        InspectorAnmat inspector = inspectorActivo(actual);
        ReporteCiudadano reporte = visibleParaInspector(id, inspector);
        reporte.tomar(inspector);
        ReporteCiudadano guardado = repository.save(reporte);
        registradorEventos.registrar(TipoEvento.REPORTE_TOMADO, "ReporteCiudadano", id,
                DatosEventos.reporteTomado(guardado, inspector), actual);
        return guardado;
    }

    /** El inspector que lo investiga lo cierra con su conclusión (al evento, solo el hash). */
    @Transactional
    public ReporteCiudadano cerrar(UUID id, String conclusion) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        InspectorAnmat inspector = inspectorActivo(actual);
        ReporteCiudadano reporte = visibleParaInspector(id, inspector);
        reporte.cerrar(inspector, conclusion);
        ReporteCiudadano guardado = repository.save(reporte);
        List<UUID> cuarentenas = cuarentenaRepository.findByReporteOrigenId(id).stream().map(Cuarentena::getId).toList();
        registradorEventos.registrar(TipoEvento.REPORTE_CERRADO, "ReporteCiudadano", id,
                DatosEventos.reporteCerrado(guardado, inspector, conclusion, cuarentenas), actual);
        return guardado;
    }

    /** Busca el reporte o lanza 404. */
    private ReporteCiudadano buscar(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reporte no encontrado con id: " + id));
    }

    /** D1: inspector ACTIVO de la cuenta autenticada, leído de la base (no del token). */
    private InspectorAnmat inspectorActivo(UsuarioAutenticado actual) {
        return inspectorAnmatRepository.findByUsuarioId(actual.getUsuarioId())
                .filter(InspectorAnmat::estaActivo)
                .orElseThrow(() -> new AccessDeniedException("La cuenta no corresponde a un inspector activo"));
    }

    /** D1: el reporte es de la provincia del inspector o él lo investiga; si no, 404. */
    private ReporteCiudadano visibleParaInspector(UUID id, InspectorAnmat inspector) {
        ReporteCiudadano reporte = buscar(id);
        if (reporte.getProvincia() != inspector.getProvincia() && !reporte.esInvestigador(inspector)) {
            throw new ResourceNotFoundException("Reporte no encontrado con id: " + id);
        }
        return reporte;
    }
}
