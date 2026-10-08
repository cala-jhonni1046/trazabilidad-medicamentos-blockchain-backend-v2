package com.medichain.modules.empresa;

import com.medichain.exceptions.ReglaNegocioException;
import com.medichain.exceptions.ResourceNotFoundException;
import com.medichain.modules.auth.UsuarioAutenticado;
import com.medichain.modules.enlacecuit.EnlaceCuit;
import com.medichain.modules.enlacecuit.EnlaceCuitRepository;
import com.medichain.modules.enlacecuit.EstadoEnlaceCuit;
import com.medichain.modules.inspectoranmat.EstadoInspector;
import com.medichain.modules.inspectoranmat.InspectorAnmat;
import com.medichain.modules.inspectoranmat.InspectorAnmatRepository;
import com.medichain.modules.trazabilidad.DatosEventos;
import com.medichain.modules.trazabilidad.RegistradorEventos;
import com.medichain.modules.trazabilidad.TipoEvento;
import com.medichain.modules.usuario.RolUsuario;
import com.medichain.modules.usuario.Usuario;
import com.medichain.modules.usuario.UsuarioRepository;
import com.medichain.utils.documentos.AlmacenDocumentos;
import com.medichain.utils.documentos.DocumentoGuardado;
import com.medichain.utils.seguridad.UsuarioActual;
import com.medichain.utils.validacion.CuitUtil;
import com.medichain.utils.validacion.Gs1Util;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Servicio EmpresaService en MediChain.
 * Consulta de empresas con filtrado por rol (C2), registro público
 * (empresa + administrador inicial + PDF) y acciones de habilitación:
 * tomar, asignar, habilitar, rechazar, suspender y rehabilitar.
 * D1: el inspector se verifica contra la base (InspectorAnmat ACTIVO de
 * su cuenta), nunca contra el claim del token. Una empresa de otra
 * provincia que no tiene asignada responde 404. Las transiciones las
 * valida la entidad (TRANSICION_INVALIDA → 409).
 */
@Service
public class EmpresaService {

    private final EmpresaRepository repository;
    private final UsuarioRepository usuarioRepository;
    private final InspectorAnmatRepository inspectorAnmatRepository;
    private final EnlaceCuitRepository enlaceCuitRepository;
    private final PasswordEncoder passwordEncoder;
    private final AlmacenDocumentos almacenDocumentos;
    private final UsuarioActual usuarioActual;
    private final RegistradorEventos registradorEventos;

    @Autowired
    public EmpresaService(EmpresaRepository repository, UsuarioRepository usuarioRepository,
                          InspectorAnmatRepository inspectorAnmatRepository,
                          EnlaceCuitRepository enlaceCuitRepository, PasswordEncoder passwordEncoder,
                          AlmacenDocumentos almacenDocumentos, UsuarioActual usuarioActual,
                          RegistradorEventos registradorEventos) {
        this.repository = repository;
        this.usuarioRepository = usuarioRepository;
        this.inspectorAnmatRepository = inspectorAnmatRepository;
        this.enlaceCuitRepository = enlaceCuitRepository;
        this.passwordEncoder = passwordEncoder;
        this.almacenDocumentos = almacenDocumentos;
        this.usuarioActual = usuarioActual;
        this.registradorEventos = registradorEventos;
    }

    /** Devuelve una página de empresas según el rol del usuario autenticado. */
    @Transactional(readOnly = true)
    public Page<Empresa> getAll(EstadoHabilitacion estado, Pageable pageable) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        if (veTodas(actual)) {
            return repository.findPorEstado(estado, pageable);
        }
        // Las empresas solo ven las HABILITADA: filtrar por otro estado no devuelve nada.
        if (estado != null && estado != EstadoHabilitacion.HABILITADA) {
            return Page.empty(pageable);
        }
        return repository.findByEstado(EstadoHabilitacion.HABILITADA, pageable);
    }

    /**
     * Busca una empresa por id. Si existe pero el usuario no puede verla
     * (no habilitada y no es la suya) responde 404, igual que si no
     * existiera, para no revelar su existencia.
     */
    @Transactional(readOnly = true)
    public Empresa getById(UUID id) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        Empresa empresa = buscar(id);
        boolean esLaPropia = id.equals(actual.getEmpresaId());
        if (!veTodas(actual) && !empresa.estaHabilitada() && !esLaPropia) {
            throw new ResourceNotFoundException("Empresa no encontrada con id: " + id);
        }
        return empresa;
    }

    /**
     * Búsqueda por CUIT para armar un circuito (cualquier usuario LABORATORIO):
     * CUIT normalizado, solo HABILITADA y del tipo pedido (DISTRIBUIDOR o
     * FARMACIA). Inexistente, no habilitada o de otro tipo → el mismo 404:
     * devuelve una sola empresa, no sirve para listar.
     * TODO: rate limiting por usuario.
     */
    @Transactional(readOnly = true)
    public Empresa buscarPorCuit(String cuit, TipoEmpresa tipo) {
        String normalizado = CuitUtil.normalizar(cuit);
        return repository.findByCuit(normalizado)
                // Solo DISTRIBUIDOR o FARMACIA: buscar un LABORATORIO no tiene sentido al armar un circuito.
                .filter(empresa -> tipo != TipoEmpresa.LABORATORIO && empresa.getTipo() == tipo
                        && empresa.estaHabilitada())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No hay una empresa " + tipo + " HABILITADA con CUIT " + normalizado));
    }

    /** Bandeja del inspector: PENDIENTE de su provincia sin tomar, más las que tiene tomadas o asignadas. */
    @Transactional(readOnly = true)
    public Page<Empresa> bandeja(Pageable pageable) {
        InspectorAnmat inspector = inspectorActivo(usuarioActual.obtener());
        return repository.findBandeja(inspector.getProvincia(), inspector.getId(), pageable);
    }

    /**
     * Registro público: crea la empresa PENDIENTE, su administrador inicial
     * y guarda el PDF (su SHA-256 queda en la empresa y en el evento).
     * CUIT o GLN repetidos → 409 EMPRESA_DUPLICADA (son datos públicos);
     * email repetido → 409 REGISTRO_NO_COMPLETADO genérico (no revela cuentas).
     */
    @Transactional
    public Empresa registrar(RegistroEmpresaRequestDTO dto, byte[] pdf, String nombreArchivo) {
        String cuit = CuitUtil.normalizar(dto.getCuit());
        String gln = Gs1Util.normalizar(dto.getGln());
        if (repository.existsByCuit(cuit)) {
            throw new ReglaNegocioException("EMPRESA_DUPLICADA", "Ya existe una empresa registrada con ese CUIT");
        }
        if (repository.existsByGln(gln)) {
            throw new ReglaNegocioException("EMPRESA_DUPLICADA", "Ya existe una empresa registrada con ese GLN");
        }
        if (usuarioRepository.existsByEmail(dto.getAdminEmail())) {
            throw new ReglaNegocioException("REGISTRO_NO_COMPLETADO", "No se pudo completar el registro con esos datos");
        }
        // El PDF se guarda después de los controles, para no dejar archivos de registros rechazados.
        DocumentoGuardado documento = almacenDocumentos.guardarPdf(pdf, nombreArchivo);

        Empresa empresa = new Empresa(dto.getTipo(), cuit, dto.getRazonSocial(), dto.getProvincia(),
                dto.getLocalidad(), dto.getDomicilio());
        empresa.setGln(gln);
        empresa.setNumeroHabilitacion(dto.getNumeroHabilitacion());
        empresa.setDirectorTecnico(dto.getDirectorTecnico());
        empresa.setDocumentoNombre(documento.getNombre());
        empresa.setDocumentoHash(documento.getHash());
        Empresa guardada = repository.save(empresa);

        Usuario admin = new Usuario(dto.getAdminEmail(), passwordEncoder.encode(dto.getAdminPassword()),
                dto.getAdminNombre(), dto.getAdminApellido(), dto.getAdminDni(), rolSegunTipo(dto.getTipo()));
        admin.setEmpresa(guardada);
        admin.setEsAdminEmpresa(true);
        admin.setEsDirectorTecnico(Boolean.TRUE.equals(dto.getAdminEsDirectorTecnico()));
        Usuario adminGuardado = usuarioRepository.save(admin);

        registradorEventos.registrar(TipoEvento.SOLICITUD_HABILITACION, "Empresa", guardada.getId(),
                DatosEventos.solicitudHabilitacion(guardada), adminGuardado.getId(), guardada.getId());
        return guardada;
    }

    /** El inspector toma una solicitud PENDIENTE de su provincia (D1). */
    @Transactional
    public Empresa tomar(UUID id) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        InspectorAnmat inspector = inspectorActivo(actual);
        Empresa empresa = visibleParaInspector(id, inspector);
        empresa.tomar(inspector);
        Empresa guardada = repository.save(empresa);
        registradorEventos.registrar(TipoEvento.SOLICITUD_TOMADA, "Empresa", id,
                DatosEventos.revisorAsignado(inspector), actual);
        return guardada;
    }

    /**
     * La Sede asigna la solicitud a un inspector ACTIVO (de otra provincia),
     * solo si la provincia de la empresa no tiene inspectores activos (R2).
     */
    @Transactional
    public Empresa asignar(UUID id, UUID inspectorId) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        Empresa empresa = buscar(id);
        if (inspectorAnmatRepository.existsByProvinciaAndEstado(empresa.getProvincia(), EstadoInspector.ACTIVO)) {
            throw new ReglaNegocioException("R2", "La provincia " + empresa.getProvincia()
                    + " tiene inspectores activos: la solicitud la toma uno de ellos");
        }
        InspectorAnmat inspector = inspectorAnmatRepository.findById(inspectorId)
                .orElseThrow(() -> new ResourceNotFoundException("InspectorAnmat no encontrado con id: " + inspectorId));
        if (!inspector.estaActivo()) {
            throw new ReglaNegocioException("R2", "El inspector asignado debe estar ACTIVO");
        }
        empresa.asignar(inspector);
        Empresa guardada = repository.save(empresa);
        registradorEventos.registrar(TipoEvento.SOLICITUD_ASIGNADA, "Empresa", id,
                DatosEventos.revisorAsignado(inspector), actual);
        return guardada;
    }

    /** El inspector revisor habilita la empresa: PENDIENTE → HABILITADA (R2, D1). */
    @Transactional
    public Empresa habilitar(UUID id) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        InspectorAnmat inspector = inspectorActivo(actual);
        Empresa empresa = visibleParaInspector(id, inspector);
        empresa.habilitar(inspector);
        Empresa guardada = repository.save(empresa);
        registradorEventos.registrar(TipoEvento.HABILITACION_APROBADA, "Empresa", id,
                DatosEventos.habilitacionAprobada(guardada, inspector), actual);
        return guardada;
    }

    /** El inspector revisor rechaza la solicitud con motivo: PENDIENTE → RECHAZADA. */
    @Transactional
    public Empresa rechazar(UUID id, String motivo) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        InspectorAnmat inspector = inspectorActivo(actual);
        Empresa empresa = visibleParaInspector(id, inspector);
        empresa.rechazar(inspector, motivo);
        Empresa guardada = repository.save(empresa);
        registradorEventos.registrar(TipoEvento.HABILITACION_RECHAZADA, "Empresa", id,
                DatosEventos.habilitacionRechazada(inspector, motivo), actual);
        return guardada;
    }

    /**
     * La Sede suspende la empresa (HABILITADA → SUSPENDIDA) y, en cascada,
     * sus circuitos APROBADO (cada uno con su evento CIRCUITO_SUSPENDIDO).
     * Los circuitos en otros estados no se tocan.
     */
    @Transactional
    public Empresa suspender(UUID id, String motivo) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        Empresa empresa = buscar(id);
        empresa.suspender(motivo);
        Empresa guardada = repository.save(empresa);
        List<EnlaceCuit> circuitos = enlaceCuitRepository.findByEstadoYEmpresa(EstadoEnlaceCuit.APROBADO, id);
        registradorEventos.registrar(TipoEvento.EMPRESA_SUSPENDIDA, "Empresa", id,
                DatosEventos.empresaSuspendida(motivo, circuitos.size()), actual);
        for (EnlaceCuit circuito : circuitos) {
            circuito.suspenderPorEmpresa();
            enlaceCuitRepository.save(circuito);
            registradorEventos.registrar(TipoEvento.CIRCUITO_SUSPENDIDO, "EnlaceCuit", circuito.getId(),
                    DatosEventos.circuitoPorEmpresa(circuito, guardada, "EMPRESA_SUSPENDIDA"), actual);
        }
        return guardada;
    }

    /**
     * La Sede rehabilita la empresa (SUSPENDIDA → HABILITADA). Vuelven a
     * APROBADO solo los circuitos suspendidos por cascada cuyas tres
     * empresas quedan HABILITADA (cada uno con CIRCUITO_REHABILITADO).
     */
    @Transactional
    public Empresa rehabilitar(UUID id) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        Empresa empresa = buscar(id);
        empresa.rehabilitar();
        Empresa guardada = repository.save(empresa);
        List<EnlaceCuit> aRehabilitar = new ArrayList<>();
        for (EnlaceCuit circuito : enlaceCuitRepository.findByEstadoYEmpresa(EstadoEnlaceCuit.SUSPENDIDO, id)) {
            if (Boolean.TRUE.equals(circuito.getSuspendidoPorEmpresa()) && circuito.puedeRehabilitarse()) {
                aRehabilitar.add(circuito);
            }
        }
        registradorEventos.registrar(TipoEvento.EMPRESA_REHABILITADA, "Empresa", id,
                DatosEventos.empresaRehabilitada(aRehabilitar.size()), actual);
        for (EnlaceCuit circuito : aRehabilitar) {
            circuito.rehabilitarPorEmpresa();
            enlaceCuitRepository.save(circuito);
            registradorEventos.registrar(TipoEvento.CIRCUITO_REHABILITADO, "EnlaceCuit", circuito.getId(),
                    DatosEventos.circuitoPorEmpresa(circuito, guardada, "EMPRESA_REHABILITADA"), actual);
        }
        return guardada;
    }

    /** Devuelve el PDF de habilitación: SEDE, o INSPECTOR de la provincia o revisor. */
    @Transactional(readOnly = true)
    public byte[] documento(UUID id) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        Empresa empresa = actual.getRol() == RolUsuario.SEDE_CENTRAL
                ? buscar(id)
                : visibleParaInspector(id, inspectorActivo(actual));
        return almacenDocumentos.leer(empresa.getDocumentoHash());
    }

    /** Busca la empresa o lanza 404. */
    private Empresa buscar(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa no encontrada con id: " + id));
    }

    /** D1: inspector ACTIVO de la cuenta autenticada, leído de la base (no del token). */
    private InspectorAnmat inspectorActivo(UsuarioAutenticado actual) {
        return inspectorAnmatRepository.findByUsuarioId(actual.getUsuarioId())
                .filter(InspectorAnmat::estaActivo)
                .orElseThrow(() -> new AccessDeniedException("La cuenta no corresponde a un inspector activo"));
    }

    /** D1: la empresa es de la provincia del inspector o él es su revisor; si no, 404. */
    private Empresa visibleParaInspector(UUID id, InspectorAnmat inspector) {
        Empresa empresa = buscar(id);
        if (empresa.getProvincia() != inspector.getProvincia() && !empresa.esRevisor(inspector)) {
            throw new ResourceNotFoundException("Empresa no encontrada con id: " + id);
        }
        return empresa;
    }

    /** SEDE_CENTRAL e INSPECTOR (fiscalización federal) ven todas las empresas. */
    private boolean veTodas(UsuarioAutenticado actual) {
        return actual.getRol() == RolUsuario.SEDE_CENTRAL || actual.getRol() == RolUsuario.INSPECTOR;
    }

    /** Rol del administrador inicial según el tipo de empresa. */
    private RolUsuario rolSegunTipo(TipoEmpresa tipo) {
        return switch (tipo) {
            case LABORATORIO -> RolUsuario.LABORATORIO;
            case DISTRIBUIDOR -> RolUsuario.DISTRIBUIDOR;
            case FARMACIA -> RolUsuario.FARMACIA;
        };
    }
}
