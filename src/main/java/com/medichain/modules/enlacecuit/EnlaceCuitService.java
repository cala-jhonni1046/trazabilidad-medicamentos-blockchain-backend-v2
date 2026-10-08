package com.medichain.modules.enlacecuit;

import com.medichain.exceptions.ReglaNegocioException;
import com.medichain.exceptions.ResourceNotFoundException;
import com.medichain.modules.auth.UsuarioAutenticado;
import com.medichain.modules.empresa.Empresa;
import com.medichain.modules.empresa.EmpresaRepository;
import com.medichain.modules.empresa.TipoEmpresa;
import com.medichain.modules.inspectoranmat.EstadoInspector;
import com.medichain.modules.inspectoranmat.InspectorAnmat;
import com.medichain.modules.inspectoranmat.InspectorAnmatRepository;
import com.medichain.modules.trazabilidad.DatosEventos;
import com.medichain.modules.trazabilidad.RegistradorEventos;
import com.medichain.modules.trazabilidad.TipoEvento;
import com.medichain.modules.usuario.RolUsuario;
import com.medichain.modules.usuario.Usuario;
import com.medichain.utils.RestriccionUnica;
import com.medichain.utils.seguridad.UsuarioActual;
import com.medichain.utils.seguridad.VerificadorEmpresa;
import com.medichain.utils.seguridad.VerificadorUsuario;
import com.medichain.utils.validacion.CuitUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/**
 * Servicio EnlaceCuitService en MediChain (circuitos).
 * Flujo R5: el DT del laboratorio propone por CUIT; la distribuidora y la
 * farmacia (admin) aceptan o rechazan su parte; un inspector de la
 * provincia de la FARMACIA (D1, verificado contra la base) toma y aprueba
 * o rechaza; la Sede asigna si esa provincia no tiene inspectores ACTIVO.
 * Suspensión y rehabilitación manual: inspector de la provincia de la
 * farmacia o Sede. Un solo circuito VIGENTE por par laboratorio–farmacia.
 * Las transiciones las valida la entidad (TRANSICION_INVALIDA → 409) y
 * cada acción emite su evento en la misma transacción.
 */
@Service
public class EnlaceCuitService {

    private final EnlaceCuitRepository repository;
    private final EmpresaRepository empresaRepository;
    private final InspectorAnmatRepository inspectorAnmatRepository;
    private final UsuarioActual usuarioActual;
    private final VerificadorEmpresa verificadorEmpresa;
    private final VerificadorUsuario verificadorUsuario;
    private final RegistradorEventos registradorEventos;

    @Autowired
    public EnlaceCuitService(EnlaceCuitRepository repository, EmpresaRepository empresaRepository,
                             InspectorAnmatRepository inspectorAnmatRepository, UsuarioActual usuarioActual,
                             VerificadorEmpresa verificadorEmpresa, VerificadorUsuario verificadorUsuario,
                             RegistradorEventos registradorEventos) {
        this.repository = repository;
        this.empresaRepository = empresaRepository;
        this.inspectorAnmatRepository = inspectorAnmatRepository;
        this.usuarioActual = usuarioActual;
        this.verificadorEmpresa = verificadorEmpresa;
        this.verificadorUsuario = verificadorUsuario;
        this.registradorEventos = registradorEventos;
    }

    /** Devuelve una página de circuitos según el rol del usuario. */
    @Transactional(readOnly = true)
    public Page<EnlaceCuit> getAll(EstadoEnlaceCuit estado, Pageable pageable) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        return switch (actual.getRol()) {
            case SEDE_CENTRAL, INSPECTOR -> repository.findPorEstado(estado, pageable);
            case LABORATORIO -> repository.findDelLaboratorioPorEstado(actual.getEmpresaId(), estado, pageable);
            case DISTRIBUIDOR -> repository.findDeLaDistribuidoraPorEstado(actual.getEmpresaId(), estado, pageable);
            case FARMACIA -> repository.findDeLaFarmaciaPorEstado(actual.getEmpresaId(), estado, pageable);
            case PACIENTE -> Page.empty(pageable);
        };
    }

    /** Busca un circuito por id; si el usuario no participa en él, 404. */
    @Transactional(readOnly = true)
    public EnlaceCuit getById(UUID id) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        EnlaceCuit circuito = buscar(id);
        if (!puedeVer(actual, circuito)) {
            throw new ResourceNotFoundException("Circuito no encontrado con id: " + id);
        }
        return circuito;
    }

    /** Circuitos que esperan la aceptación de la empresa del usuario (admin de DIST o FARM). */
    @Transactional(readOnly = true)
    public Page<EnlaceCuit> pendientesDeAceptacion(Pageable pageable) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        verificadorUsuario.exigirAdminEmpresa(actual.getUsuarioId());
        return repository.findPendientesDeAceptacion(actual.getEmpresaId(), pageable);
    }

    /**
     * Tablero de la Sede (R5): circuitos PENDIENTE_INSPECTOR sin revisor cuya
     * farmacia es de una provincia sin inspectores ACTIVO, que la Sede tiene que asignar.
     */
    @Transactional(readOnly = true)
    public Page<EnlaceCuit> sinInspector(Pageable pageable) {
        return repository.findSinInspector(pageable);
    }

    /** Bandeja del inspector: PENDIENTE_INSPECTOR de farmacias de su provincia sin tomar, más los suyos. */
    @Transactional(readOnly = true)
    public Page<EnlaceCuit> bandeja(Pageable pageable) {
        InspectorAnmat inspector = inspectorActivo(usuarioActual.obtener());
        return repository.findBandeja(inspector.getProvincia(), inspector.getId(), pageable);
    }

    /**
     * El DT del laboratorio propone un circuito con los CUIT de la
     * distribuidora y de la farmacia (R5): las tres HABILITADA, tipos
     * correctos y sin otro circuito vigente para el par laboratorio–farmacia.
     * El código CIR-0001 sale de la secuencia de PostgreSQL. Dos propuestas
     * simultáneas del mismo par: entra una y la otra recibe R5 (índice único
     * parcial ux_circuito_par_vigente); su número de secuencia queda sin usar.
     */
    @Transactional
    public EnlaceCuit proponer(String cuitDistribuidor, String cuitFarmacia) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        Usuario proponente = verificadorUsuario.exigirDirectorTecnico(actual.getUsuarioId());
        Empresa laboratorio = verificadorEmpresa.exigirHabilitada(actual.getEmpresaId());
        Empresa distribuidor = habilitadaDelTipo(cuitDistribuidor, TipoEmpresa.DISTRIBUIDOR, "distribuidora");
        Empresa farmacia = habilitadaDelTipo(cuitFarmacia, TipoEmpresa.FARMACIA, "farmacia");
        if (repository.existsByLaboratorioIdAndFarmaciaIdAndEstadoIn(laboratorio.getId(), farmacia.getId(),
                EnlaceCuitRepository.ESTADOS_VIGENTES)) {
            throw new ReglaNegocioException("R5",
                    "Ya existe un circuito vigente entre este laboratorio y la farmacia " + farmacia.getCuit());
        }
        String codigo = String.format("CIR-%04d", repository.siguienteNumeroCodigo());
        EnlaceCuit guardado;
        try {
            // saveAndFlush: si otra propuesta del mismo par entró al mismo tiempo (las dos pasaron el
            // control de arriba), el índice único parcial la rechaza ACÁ y se responde R5, no un 409 genérico.
            guardado = repository.saveAndFlush(new EnlaceCuit(codigo, laboratorio, distribuidor, farmacia, proponente));
        } catch (DataIntegrityViolationException e) {
            if (RestriccionUnica.es(e, "ux_circuito_par_vigente")) {
                throw new ReglaNegocioException("R5",
                        "Ya existe un circuito vigente entre este laboratorio y la farmacia " + farmacia.getCuit());
            }
            throw e;
        }
        registradorEventos.registrar(TipoEvento.CIRCUITO_PROPUESTO, "EnlaceCuit", guardado.getId(),
                DatosEventos.circuitoPropuesto(guardado), actual);
        return guardado;
    }

    /**
     * El admin de la distribuidora o de la farmacia acepta su parte. Se
     * vuelve a verificar que las tres empresas sigan HABILITADA. Con las
     * dos aceptaciones el circuito pasa a PENDIENTE_INSPECTOR.
     */
    @Transactional
    public EnlaceCuit aceptar(UUID id) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        verificadorUsuario.exigirAdminEmpresa(actual.getUsuarioId());
        Empresa empresa = verificadorEmpresa.exigirHabilitada(actual.getEmpresaId());
        EnlaceCuit circuito = participanteDe(id, empresa);
        circuito.exigirTresHabilitadas();
        String parte = circuito.esDistribuidor(empresa) ? "DISTRIBUIDOR" : "FARMACIA";
        boolean completo = circuito.aceptar(empresa);
        EnlaceCuit guardado = repository.save(circuito);
        registradorEventos.registrar(TipoEvento.CIRCUITO_ACEPTADO, "EnlaceCuit", id,
                DatosEventos.circuitoAceptado(empresa, parte, completo), actual);
        return guardado;
    }

    /** El admin de la distribuidora o de la farmacia rechaza el circuito (definitivo), con motivo. */
    @Transactional
    public EnlaceCuit rechazarPorEmpresa(UUID id, String motivo) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        verificadorUsuario.exigirAdminEmpresa(actual.getUsuarioId());
        Empresa empresa = verificadorEmpresa.exigirHabilitada(actual.getEmpresaId());
        EnlaceCuit circuito = participanteDe(id, empresa);
        circuito.rechazarPorEmpresa(empresa, motivo);
        EnlaceCuit guardado = repository.save(circuito);
        registradorEventos.registrar(TipoEvento.CIRCUITO_RECHAZADO, "EnlaceCuit", id,
                DatosEventos.circuitoRechazado(guardado, empresa.getId(), null, motivo), actual);
        return guardado;
    }

    /** Un inspector de la provincia de la farmacia toma el circuito (D1). */
    @Transactional
    public EnlaceCuit tomar(UUID id) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        InspectorAnmat inspector = inspectorActivo(actual);
        EnlaceCuit circuito = visibleParaInspector(id, inspector);
        circuito.tomar(inspector);
        EnlaceCuit guardado = repository.save(circuito);
        registradorEventos.registrar(TipoEvento.CIRCUITO_TOMADO, "EnlaceCuit", id,
                DatosEventos.revisorAsignado(inspector), actual);
        return guardado;
    }

    /**
     * La Sede asigna el circuito a un inspector ACTIVO cuando la provincia
     * de la farmacia no tiene inspectores ACTIVO (R5).
     */
    @Transactional
    public EnlaceCuit asignar(UUID id, UUID inspectorId) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        EnlaceCuit circuito = buscar(id);
        if (inspectorAnmatRepository.existsByProvinciaAndEstado(circuito.getFarmacia().getProvincia(),
                EstadoInspector.ACTIVO)) {
            throw new ReglaNegocioException("R5", "La provincia " + circuito.getFarmacia().getProvincia()
                    + " tiene inspectores activos: el circuito lo toma uno de ellos");
        }
        InspectorAnmat inspector = inspectorAnmatRepository.findById(inspectorId)
                .orElseThrow(() -> new ResourceNotFoundException("InspectorAnmat no encontrado con id: " + inspectorId));
        if (!inspector.estaActivo()) {
            throw new ReglaNegocioException("R5", "El inspector asignado debe estar ACTIVO");
        }
        circuito.asignar(inspector);
        EnlaceCuit guardado = repository.save(circuito);
        registradorEventos.registrar(TipoEvento.CIRCUITO_ASIGNADO, "EnlaceCuit", id,
                DatosEventos.revisorAsignado(inspector), actual);
        return guardado;
    }

    /** El revisor aprueba: vuelve a verificar que las tres empresas sigan HABILITADA (R5). */
    @Transactional
    public EnlaceCuit aprobar(UUID id) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        InspectorAnmat inspector = inspectorActivo(actual);
        EnlaceCuit circuito = visibleParaInspector(id, inspector);
        circuito.exigirTresHabilitadas();
        circuito.aprobar(inspector);
        EnlaceCuit guardado = repository.save(circuito);
        registradorEventos.registrar(TipoEvento.CIRCUITO_APROBADO, "EnlaceCuit", id,
                DatosEventos.circuitoAprobado(inspector), actual);
        return guardado;
    }

    /** El revisor rechaza (definitivo), con motivo. */
    @Transactional
    public EnlaceCuit rechazarPorInspector(UUID id, String motivo) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        InspectorAnmat inspector = inspectorActivo(actual);
        EnlaceCuit circuito = visibleParaInspector(id, inspector);
        circuito.rechazarPorInspector(inspector, motivo);
        EnlaceCuit guardado = repository.save(circuito);
        registradorEventos.registrar(TipoEvento.CIRCUITO_RECHAZADO, "EnlaceCuit", id,
                DatosEventos.circuitoRechazado(guardado, null, inspector.getId(), motivo), actual);
        return guardado;
    }

    /** Suspensión manual (inspector de la provincia de la farmacia o Sede): APROBADO → SUSPENDIDO. */
    @Transactional
    public EnlaceCuit suspender(UUID id, String motivo) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        InspectorAnmat inspector = inspectorSiNoEsSede(actual);
        EnlaceCuit circuito = inspector == null ? buscar(id) : deLaProvincia(id, inspector);
        circuito.suspenderManual(motivo);
        EnlaceCuit guardado = repository.save(circuito);
        registradorEventos.registrar(TipoEvento.CIRCUITO_SUSPENDIDO, "EnlaceCuit", id,
                DatosEventos.circuitoManual(inspector != null ? inspector.getId() : null, motivo), actual);
        return guardado;
    }

    /** Rehabilitación manual de un circuito suspendido a mano, con las tres empresas HABILITADA. */
    @Transactional
    public EnlaceCuit rehabilitar(UUID id) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        InspectorAnmat inspector = inspectorSiNoEsSede(actual);
        EnlaceCuit circuito = inspector == null ? buscar(id) : deLaProvincia(id, inspector);
        circuito.rehabilitarManual();
        EnlaceCuit guardado = repository.save(circuito);
        registradorEventos.registrar(TipoEvento.CIRCUITO_REHABILITADO, "EnlaceCuit", id,
                DatosEventos.circuitoManual(inspector != null ? inspector.getId() : null, null), actual);
        return guardado;
    }

    /** Busca el circuito o lanza 404. */
    private EnlaceCuit buscar(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Circuito no encontrado con id: " + id));
    }

    /**
     * Resuelve una empresa por CUIT para proponer (R5): debe existir, estar
     * HABILITADA y ser del tipo esperado; si no, 409 R5 diciendo cuál falló.
     */
    private Empresa habilitadaDelTipo(String cuit, TipoEmpresa tipo, String rol) {
        String normalizado = CuitUtil.normalizar(cuit);
        Empresa empresa = empresaRepository.findByCuit(normalizado).orElse(null);
        if (empresa == null || empresa.getTipo() != tipo || !empresa.estaHabilitada()) {
            throw new ReglaNegocioException("R5", "No hay una " + rol + " HABILITADA con CUIT " + normalizado);
        }
        return empresa;
    }

    /** Circuito en el que la empresa es la distribuidora o la farmacia; si no participa, 404. */
    private EnlaceCuit participanteDe(UUID id, Empresa empresa) {
        EnlaceCuit circuito = buscar(id);
        if (!circuito.esDistribuidor(empresa) && !circuito.esFarmacia(empresa)) {
            throw new ResourceNotFoundException("Circuito no encontrado con id: " + id);
        }
        return circuito;
    }

    /** D1: inspector ACTIVO de la cuenta autenticada, leído de la base (no del token). */
    private InspectorAnmat inspectorActivo(UsuarioAutenticado actual) {
        return inspectorAnmatRepository.findByUsuarioId(actual.getUsuarioId())
                .filter(InspectorAnmat::estaActivo)
                .orElseThrow(() -> new AccessDeniedException("La cuenta no corresponde a un inspector activo"));
    }

    /** Para acciones de Sede o inspector: null si es la Sede; el inspector activo si es INSPECTOR. */
    private InspectorAnmat inspectorSiNoEsSede(UsuarioAutenticado actual) {
        return actual.getRol() == RolUsuario.SEDE_CENTRAL ? null : inspectorActivo(actual);
    }

    /** D1: el circuito es de una farmacia de la provincia del inspector o él es su revisor; si no, 404. */
    private EnlaceCuit visibleParaInspector(UUID id, InspectorAnmat inspector) {
        EnlaceCuit circuito = buscar(id);
        if (circuito.getFarmacia().getProvincia() != inspector.getProvincia() && !circuito.esRevisor(inspector)) {
            throw new ResourceNotFoundException("Circuito no encontrado con id: " + id);
        }
        return circuito;
    }

    /** D1 estricto (suspender/rehabilitar): la farmacia es de la provincia del inspector; si no, 404. */
    private EnlaceCuit deLaProvincia(UUID id, InspectorAnmat inspector) {
        EnlaceCuit circuito = buscar(id);
        if (circuito.getFarmacia().getProvincia() != inspector.getProvincia()) {
            throw new ResourceNotFoundException("Circuito no encontrado con id: " + id);
        }
        return circuito;
    }

    /** SEDE e INSPECTOR ven todo; una empresa, solo los circuitos donde participa. */
    private boolean puedeVer(UsuarioAutenticado actual, EnlaceCuit circuito) {
        return switch (actual.getRol()) {
            case SEDE_CENTRAL, INSPECTOR -> true;
            case LABORATORIO -> esDe(circuito.getLaboratorio(), actual.getEmpresaId());
            case DISTRIBUIDOR -> esDe(circuito.getDistribuidor(), actual.getEmpresaId());
            case FARMACIA -> esDe(circuito.getFarmacia(), actual.getEmpresaId());
            case PACIENTE -> false;
        };
    }

    /** Indica si la empresa dada es la del usuario. */
    private boolean esDe(Empresa empresa, UUID empresaId) {
        return empresa != null && empresaId != null && empresaId.equals(empresa.getId());
    }
}
