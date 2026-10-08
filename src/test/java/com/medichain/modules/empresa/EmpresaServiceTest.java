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
import com.medichain.modules.trazabilidad.HashUtil;
import com.medichain.modules.trazabilidad.JsonCanonico;
import com.medichain.modules.trazabilidad.RegistradorEventos;
import com.medichain.modules.trazabilidad.TipoEvento;
import com.medichain.modules.usuario.RolUsuario;
import com.medichain.modules.usuario.Usuario;
import com.medichain.modules.usuario.UsuarioRepository;
import com.medichain.testutil.DatosDePrueba;
import com.medichain.utils.documentos.AlmacenDocumentos;
import com.medichain.utils.documentos.DocumentoGuardado;
import com.medichain.utils.enums.Provincia;
import com.medichain.utils.seguridad.UsuarioActual;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test unitario EmpresaServiceTest en MediChain.
 * Con Mockito: filtrado por rol (C2), registro público y acciones de
 * habilitación (caso feliz con su evento, provincia incorrecta → 404,
 * transición inválida → 409, inspector inactivo → 403, R2 en asignar,
 * cascada de circuitos al suspender y rehabilitar).
 */
@ExtendWith(MockitoExtension.class)
class EmpresaServiceTest {

    @Mock
    private RegistradorEventos registradorEventos;

    @Mock
    private EmpresaRepository repository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private InspectorAnmatRepository inspectorAnmatRepository;

    @Mock
    private EnlaceCuitRepository enlaceCuitRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AlmacenDocumentos almacenDocumentos;

    @Mock
    private UsuarioActual usuarioActual;

    private final Pageable pagina = PageRequest.of(0, 20);

    /** Construye el Service bajo prueba con los mocks. */
    private EmpresaService service() {
        return new EmpresaService(repository, usuarioRepository, inspectorAnmatRepository, enlaceCuitRepository,
                passwordEncoder, almacenDocumentos, usuarioActual, registradorEventos);
    }

    /** Deja autenticado al inspector dado (cuenta y entidad resueltas por los mocks) y guarda sin cambios. */
    private UsuarioAutenticado comoInspector(InspectorAnmat inspector) {
        UsuarioAutenticado actual = DatosDePrueba.autenticadoInspector(inspector);
        when(usuarioActual.obtener()).thenReturn(actual);
        when(inspectorAnmatRepository.findByUsuarioId(actual.getUsuarioId())).thenReturn(Optional.of(inspector));
        lenient().when(repository.save(any(Empresa.class))).thenAnswer(inv -> inv.getArgument(0));
        return actual;
    }

    /** Deja autenticada a la Sede y guarda sin cambios. */
    private UsuarioAutenticado comoSede() {
        UsuarioAutenticado actual = DatosDePrueba.autenticado(RolUsuario.SEDE_CENTRAL, null);
        when(usuarioActual.obtener()).thenReturn(actual);
        lenient().when(repository.save(any(Empresa.class))).thenAnswer(inv -> inv.getArgument(0));
        return actual;
    }

    /** Empresa PENDIENTE de Córdoba (provincia de DatosDePrueba) registrada en el mock. */
    private Empresa pendiente(TipoEmpresa tipo) {
        Empresa empresa = DatosDePrueba.empresa(tipo);
        lenient().when(repository.findById(empresa.getId())).thenReturn(Optional.of(empresa));
        return empresa;
    }

    /** Circuito APROBADO entre las tres empresas dadas. */
    private EnlaceCuit circuitoAprobado(Empresa laboratorio, Empresa distribuidor, Empresa farmacia) {
        return DatosDePrueba.circuitoAprobado(laboratorio, distribuidor, farmacia);
    }

    // ---------- Consulta (C2) ----------

    @Test
    @DisplayName("Ve lo suyo: una farmacia ve su propia empresa aunque esté PENDIENTE")
    void veSuPropiaEmpresa() {
        Empresa propia = pendiente(TipoEmpresa.FARMACIA);
        when(usuarioActual.obtener()).thenReturn(DatosDePrueba.autenticado(RolUsuario.FARMACIA, propia));

        assertSame(propia, service().getById(propia.getId()));
    }

    @Test
    @DisplayName("C2 / no ve lo ajeno: una empresa ajena NO habilitada responde 404")
    void noVeEmpresaAjenaNoHabilitada() {
        Empresa propia = DatosDePrueba.empresaHabilitada(TipoEmpresa.FARMACIA);
        Empresa ajena = pendiente(TipoEmpresa.LABORATORIO);
        when(usuarioActual.obtener()).thenReturn(DatosDePrueba.autenticado(RolUsuario.FARMACIA, propia));

        assertThrows(ResourceNotFoundException.class, () -> service().getById(ajena.getId()));
    }

    @Test
    @DisplayName("C2: el listado de una empresa solo trae las HABILITADA")
    void listadoSoloHabilitadasParaEmpresas() {
        Empresa propia = DatosDePrueba.empresaHabilitada(TipoEmpresa.FARMACIA);
        when(usuarioActual.obtener()).thenReturn(DatosDePrueba.autenticado(RolUsuario.FARMACIA, propia));
        when(repository.findByEstado(EstadoHabilitacion.HABILITADA, pagina)).thenReturn(Page.empty(pagina));

        service().getAll(null, pagina);

        verify(repository, never()).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("Bandeja: consulta por la provincia y el id del inspector leídos de la base")
    void bandejaUsaProvinciaDeLaBase() {
        InspectorAnmat inspector = DatosDePrueba.inspector(Provincia.CORDOBA);
        comoInspector(inspector);
        when(repository.findBandeja(Provincia.CORDOBA, inspector.getId(), pagina)).thenReturn(Page.empty(pagina));

        service().bandeja(pagina);

        verify(repository).findBandeja(Provincia.CORDOBA, inspector.getId(), pagina);
    }

    // ---------- Tomar ----------

    @Test
    @DisplayName("Tomar: el inspector de la provincia queda como revisor y se emite SOLICITUD_TOMADA")
    void tomarCasoFeliz() {
        InspectorAnmat inspector = DatosDePrueba.inspector(Provincia.CORDOBA);
        UsuarioAutenticado actual = comoInspector(inspector);
        Empresa empresa = pendiente(TipoEmpresa.FARMACIA);

        service().tomar(empresa.getId());

        assertTrue(empresa.esRevisor(inspector));
        verify(registradorEventos).registrar(eq(TipoEvento.SOLICITUD_TOMADA), eq("Empresa"), eq(empresa.getId()),
                anyMap(), eq(actual));
    }

    @Test
    @DisplayName("Tomar: inspector de otra provincia → 404 (D1)")
    void tomarOtraProvinciaDevuelve404() {
        comoInspector(DatosDePrueba.inspector(Provincia.MENDOZA));
        Empresa empresa = pendiente(TipoEmpresa.FARMACIA);

        assertThrows(ResourceNotFoundException.class, () -> service().tomar(empresa.getId()));
        verify(registradorEventos, never()).registrar(any(), any(), any(), anyMap(), any(UsuarioAutenticado.class));
    }

    @Test
    @DisplayName("Tomar: una solicitud ya tomada por otro → 409 TRANSICION_INVALIDA")
    void tomarYaTomadaDevuelve409() {
        comoInspector(DatosDePrueba.inspector(Provincia.CORDOBA));
        Empresa empresa = pendiente(TipoEmpresa.FARMACIA);
        empresa.tomar(DatosDePrueba.inspector(Provincia.CORDOBA));

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class, () -> service().tomar(empresa.getId()));
        assertEquals("TRANSICION_INVALIDA", ex.getCodigoRegla());
    }

    @Test
    @DisplayName("Tomar: la cuenta no es de un inspector ACTIVO → 403")
    void tomarInspectorDeBajaDevuelve403() {
        InspectorAnmat inspector = DatosDePrueba.inspector(Provincia.CORDOBA);
        inspector.darDeBaja();
        comoInspector(inspector);
        Empresa empresa = pendiente(TipoEmpresa.FARMACIA);

        assertThrows(AccessDeniedException.class, () -> service().tomar(empresa.getId()));
    }

    // ---------- Habilitar / rechazar ----------

    @Test
    @DisplayName("Habilitar: el revisor la habilita y se emite HABILITACION_APROBADA")
    void habilitarCasoFeliz() {
        InspectorAnmat inspector = DatosDePrueba.inspector(Provincia.CORDOBA);
        UsuarioAutenticado actual = comoInspector(inspector);
        Empresa empresa = pendiente(TipoEmpresa.FARMACIA);
        empresa.tomar(inspector);

        service().habilitar(empresa.getId());

        assertEquals(EstadoHabilitacion.HABILITADA, empresa.getEstado());
        verify(registradorEventos).registrar(eq(TipoEvento.HABILITACION_APROBADA), eq("Empresa"),
                eq(empresa.getId()), anyMap(), eq(actual));
    }

    @Test
    @DisplayName("Habilitar sin haber tomado la solicitud → 409 TRANSICION_INVALIDA")
    void habilitarSinTomarDevuelve409() {
        comoInspector(DatosDePrueba.inspector(Provincia.CORDOBA));
        Empresa empresa = pendiente(TipoEmpresa.FARMACIA);

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> service().habilitar(empresa.getId()));
        assertEquals("TRANSICION_INVALIDA", ex.getCodigoRegla());
    }

    @Test
    @DisplayName("Habilitar una empresa de otra provincia sin tenerla asignada → 404")
    void habilitarOtraProvinciaDevuelve404() {
        comoInspector(DatosDePrueba.inspector(Provincia.MENDOZA));
        Empresa empresa = pendiente(TipoEmpresa.FARMACIA);

        assertThrows(ResourceNotFoundException.class, () -> service().habilitar(empresa.getId()));
    }

    @Test
    @DisplayName("Habilitar: el inspector ASIGNADO de otra provincia sí puede (es el revisor)")
    void habilitarAsignadoDeOtraProvincia() {
        InspectorAnmat asignado = DatosDePrueba.inspector(Provincia.MENDOZA);
        comoInspector(asignado);
        Empresa empresa = pendiente(TipoEmpresa.FARMACIA);
        empresa.asignar(asignado);

        service().habilitar(empresa.getId());

        assertEquals(EstadoHabilitacion.HABILITADA, empresa.getEstado());
    }

    @Test
    @DisplayName("Rechazar: guarda el motivo en la empresa y al evento solo va su hash")
    @SuppressWarnings("unchecked")
    void rechazarGuardaMotivoYEventoConHash() {
        InspectorAnmat inspector = DatosDePrueba.inspector(Provincia.CORDOBA);
        comoInspector(inspector);
        Empresa empresa = pendiente(TipoEmpresa.FARMACIA);
        empresa.tomar(inspector);
        String motivo = "Falta la firma del director técnico";

        service().rechazar(empresa.getId(), motivo);

        assertEquals(EstadoHabilitacion.RECHAZADA, empresa.getEstado());
        assertEquals(motivo, empresa.getMotivoRechazo());
        ArgumentCaptor<Map<String, Object>> datos = ArgumentCaptor.forClass(Map.class);
        verify(registradorEventos).registrar(eq(TipoEvento.HABILITACION_RECHAZADA), eq("Empresa"),
                eq(empresa.getId()), datos.capture(), any(UsuarioAutenticado.class));
        assertEquals(HashUtil.sha256Hex(motivo), datos.getValue().get("motivoHash"));
        assertFalse(JsonCanonico.escribir(datos.getValue()).contains("firma"), "el texto del motivo no va al evento");
    }

    @Test
    @DisplayName("Rechazar una empresa ya HABILITADA → 409 TRANSICION_INVALIDA")
    void rechazarHabilitadaDevuelve409() {
        InspectorAnmat inspector = DatosDePrueba.inspector(Provincia.CORDOBA);
        comoInspector(inspector);
        Empresa empresa = pendiente(TipoEmpresa.FARMACIA);
        empresa.tomar(inspector);
        empresa.habilitar(inspector);

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> service().rechazar(empresa.getId(), "motivo"));
        assertEquals("TRANSICION_INVALIDA", ex.getCodigoRegla());
    }

    // ---------- Asignar ----------

    @Test
    @DisplayName("Asignar: provincia sin inspectores activos → queda asignada y se emite SOLICITUD_ASIGNADA")
    void asignarCasoFeliz() {
        UsuarioAutenticado actual = comoSede();
        Empresa empresa = pendiente(TipoEmpresa.FARMACIA);
        InspectorAnmat asignado = DatosDePrueba.inspector(Provincia.MENDOZA);
        when(inspectorAnmatRepository.existsByProvinciaAndEstado(Provincia.CORDOBA, EstadoInspector.ACTIVO))
                .thenReturn(false);
        when(inspectorAnmatRepository.findById(asignado.getId())).thenReturn(Optional.of(asignado));

        service().asignar(empresa.getId(), asignado.getId());

        assertTrue(empresa.esRevisor(asignado));
        verify(registradorEventos).registrar(eq(TipoEvento.SOLICITUD_ASIGNADA), eq("Empresa"), eq(empresa.getId()),
                anyMap(), eq(actual));
    }

    @Test
    @DisplayName("Asignar cuando la provincia SÍ tiene inspectores activos → 409 R2")
    void asignarConInspectoresEnLaProvinciaDevuelveR2() {
        comoSede();
        Empresa empresa = pendiente(TipoEmpresa.FARMACIA);
        when(inspectorAnmatRepository.existsByProvinciaAndEstado(Provincia.CORDOBA, EstadoInspector.ACTIVO))
                .thenReturn(true);

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> service().asignar(empresa.getId(), UUID.randomUUID()));
        assertEquals("R2", ex.getCodigoRegla());
    }

    @Test
    @DisplayName("Asignar a un inspector dado de baja → 409 R2")
    void asignarInspectorDeBajaDevuelveR2() {
        comoSede();
        Empresa empresa = pendiente(TipoEmpresa.FARMACIA);
        InspectorAnmat deBaja = DatosDePrueba.inspector(Provincia.MENDOZA);
        deBaja.darDeBaja();
        when(inspectorAnmatRepository.existsByProvinciaAndEstado(Provincia.CORDOBA, EstadoInspector.ACTIVO))
                .thenReturn(false);
        when(inspectorAnmatRepository.findById(deBaja.getId())).thenReturn(Optional.of(deBaja));

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> service().asignar(empresa.getId(), deBaja.getId()));
        assertEquals("R2", ex.getCodigoRegla());
    }

    // ---------- Suspender / rehabilitar ----------

    @Test
    @DisplayName("Suspender: empresa SUSPENDIDA, sus circuitos APROBADO pasan a SUSPENDIDO, un evento por circuito")
    void suspenderEnCascada() {
        comoSede();
        Empresa laboratorio = DatosDePrueba.empresaHabilitada(TipoEmpresa.LABORATORIO);
        when(repository.findById(laboratorio.getId())).thenReturn(Optional.of(laboratorio));
        EnlaceCuit uno = circuitoAprobado(laboratorio, DatosDePrueba.empresaHabilitada(TipoEmpresa.DISTRIBUIDOR),
                DatosDePrueba.empresaHabilitada(TipoEmpresa.FARMACIA));
        EnlaceCuit dos = circuitoAprobado(laboratorio, DatosDePrueba.empresaHabilitada(TipoEmpresa.DISTRIBUIDOR),
                DatosDePrueba.empresaHabilitada(TipoEmpresa.FARMACIA));
        when(enlaceCuitRepository.findByEstadoYEmpresa(EstadoEnlaceCuit.APROBADO, laboratorio.getId()))
                .thenReturn(List.of(uno, dos));

        service().suspender(laboratorio.getId(), "Inspección con hallazgos");

        assertEquals(EstadoHabilitacion.SUSPENDIDA, laboratorio.getEstado());
        assertEquals(EstadoEnlaceCuit.SUSPENDIDO, uno.getEstado());
        assertTrue(uno.getSuspendidoPorEmpresa());
        assertEquals(EstadoEnlaceCuit.SUSPENDIDO, dos.getEstado());
        verify(registradorEventos).registrar(eq(TipoEvento.EMPRESA_SUSPENDIDA), eq("Empresa"),
                eq(laboratorio.getId()), anyMap(), any(UsuarioAutenticado.class));
        verify(registradorEventos, times(2)).registrar(eq(TipoEvento.CIRCUITO_SUSPENDIDO), eq("EnlaceCuit"),
                any(), anyMap(), any(UsuarioAutenticado.class));
    }

    @Test
    @DisplayName("Suspender una empresa PENDIENTE → 409 TRANSICION_INVALIDA")
    void suspenderPendienteDevuelve409() {
        comoSede();
        Empresa empresa = pendiente(TipoEmpresa.FARMACIA);

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> service().suspender(empresa.getId(), "motivo"));
        assertEquals("TRANSICION_INVALIDA", ex.getCodigoRegla());
    }

    @Test
    @DisplayName("Rehabilitar: vuelven a APROBADO solo los circuitos de la cascada con sus tres empresas HABILITADA")
    void rehabilitarSoloCircuitosDeLaCascada() {
        comoSede();
        Empresa laboratorio = DatosDePrueba.empresaHabilitada(TipoEmpresa.LABORATORIO);
        when(repository.findById(laboratorio.getId())).thenReturn(Optional.of(laboratorio));
        Empresa farmaciaSuspendida = DatosDePrueba.empresaHabilitada(TipoEmpresa.FARMACIA);
        EnlaceCuit vuelve = circuitoAprobado(laboratorio, DatosDePrueba.empresaHabilitada(TipoEmpresa.DISTRIBUIDOR),
                DatosDePrueba.empresaHabilitada(TipoEmpresa.FARMACIA));
        EnlaceCuit noVuelve = circuitoAprobado(laboratorio, DatosDePrueba.empresaHabilitada(TipoEmpresa.DISTRIBUIDOR),
                farmaciaSuspendida);
        EnlaceCuit suspendidoAMano = circuitoAprobado(laboratorio,
                DatosDePrueba.empresaHabilitada(TipoEmpresa.DISTRIBUIDOR), DatosDePrueba.empresaHabilitada(TipoEmpresa.FARMACIA));
        vuelve.suspenderPorEmpresa();
        noVuelve.suspenderPorEmpresa();
        suspendidoAMano.suspenderManual("decisión del inspector");
        laboratorio.suspender("motivo");
        farmaciaSuspendida.suspender("otro motivo");
        when(enlaceCuitRepository.findByEstadoYEmpresa(EstadoEnlaceCuit.SUSPENDIDO, laboratorio.getId()))
                .thenReturn(List.of(vuelve, noVuelve, suspendidoAMano));

        service().rehabilitar(laboratorio.getId());

        assertEquals(EstadoHabilitacion.HABILITADA, laboratorio.getEstado());
        assertEquals(EstadoEnlaceCuit.APROBADO, vuelve.getEstado());
        assertEquals(EstadoEnlaceCuit.SUSPENDIDO, noVuelve.getEstado());
        assertEquals(EstadoEnlaceCuit.SUSPENDIDO, suspendidoAMano.getEstado());
        verify(registradorEventos, times(1)).registrar(eq(TipoEvento.CIRCUITO_REHABILITADO), eq("EnlaceCuit"),
                eq(vuelve.getId()), anyMap(), any(UsuarioAutenticado.class));
    }

    // ---------- Registro público ----------

    /** DTO de registro válido para una farmacia. */
    private RegistroEmpresaRequestDTO registroFarmacia() {
        RegistroEmpresaRequestDTO dto = new RegistroEmpresaRequestDTO();
        dto.setTipo(TipoEmpresa.FARMACIA);
        dto.setCuit("30710000030");
        dto.setRazonSocial("Farmacia Nueva");
        dto.setGln("7799000000037");
        dto.setProvincia(Provincia.MENDOZA);
        dto.setLocalidad("Mendoza");
        dto.setDomicilio("Calle 1");
        dto.setAdminEmail("admin@nueva.demo");
        dto.setAdminPassword("clave-segura");
        dto.setAdminNombre("Ana");
        dto.setAdminApellido("Perez");
        dto.setAdminDni("12345678");
        return dto;
    }

    @Test
    @DisplayName("Registro: crea empresa PENDIENTE + admin, guarda el hash del PDF y el evento no lleva el email")
    @SuppressWarnings("unchecked")
    void registrarCasoFeliz() {
        byte[] pdf = "%PDF-1.4 prueba".getBytes();
        String hash = "a".repeat(64);
        when(almacenDocumentos.guardarPdf(pdf, "hab.pdf")).thenReturn(new DocumentoGuardado(hash, "hab.pdf"));
        when(passwordEncoder.encode("clave-segura")).thenReturn("hash-bcrypt");
        when(repository.save(any(Empresa.class))).thenAnswer(inv -> {
            Empresa e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        Empresa empresa = service().registrar(registroFarmacia(), pdf, "hab.pdf");

        assertEquals(EstadoHabilitacion.PENDIENTE, empresa.getEstado());
        assertEquals("30-71000003-0", empresa.getCuit());
        assertEquals(hash, empresa.getDocumentoHash());
        ArgumentCaptor<Usuario> admin = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(admin.capture());
        assertEquals(RolUsuario.FARMACIA, admin.getValue().getRol());
        assertTrue(admin.getValue().getEsAdminEmpresa());
        assertFalse(admin.getValue().getEsDirectorTecnico());
        assertSame(empresa, admin.getValue().getEmpresa());
        ArgumentCaptor<Map<String, Object>> datos = ArgumentCaptor.forClass(Map.class);
        verify(registradorEventos).registrar(eq(TipoEvento.SOLICITUD_HABILITACION), eq("Empresa"),
                eq(empresa.getId()), datos.capture(), eq(admin.getValue().getId()), eq(empresa.getId()));
        assertEquals(hash, datos.getValue().get("documentoHash"));
        assertFalse(JsonCanonico.escribir(datos.getValue()).contains("admin@nueva.demo"));
    }

    @Test
    @DisplayName("Registro con CUIT existente → 409 EMPRESA_DUPLICADA y no guarda el PDF")
    void registrarCuitDuplicado() {
        when(repository.existsByCuit("30-71000003-0")).thenReturn(true);

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> service().registrar(registroFarmacia(), new byte[0], "x.pdf"));
        assertEquals("EMPRESA_DUPLICADA", ex.getCodigoRegla());
        verify(almacenDocumentos, never()).guardarPdf(any(), any());
    }

    @Test
    @DisplayName("Registro con email existente → 409 REGISTRO_NO_COMPLETADO con mensaje genérico")
    void registrarEmailDuplicado() {
        when(usuarioRepository.existsByEmail("admin@nueva.demo")).thenReturn(true);

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> service().registrar(registroFarmacia(), new byte[0], "x.pdf"));
        assertEquals("REGISTRO_NO_COMPLETADO", ex.getCodigoRegla());
        assertFalse(ex.getMessage().toLowerCase().contains("email"), "el mensaje no revela que el email existe");
        verify(repository, never()).save(any(Empresa.class));
    }

    @Test
    @DisplayName("Liberar: una solicitud tomada vuelve a la bandeja sin revisor")
    void liberarRevisor() {
        Empresa empresa = DatosDePrueba.empresa(TipoEmpresa.FARMACIA);
        empresa.tomar(DatosDePrueba.inspector(Provincia.CORDOBA));

        empresa.liberarRevisor();

        assertNull(empresa.getInspectorRevisor());
    }

    // ---------- Buscar por CUIT ----------

    @Test
    @DisplayName("Buscar por CUIT: acepta el CUIT sin guiones y devuelve la empresa HABILITADA del tipo pedido")
    void buscarPorCuitCasoFeliz() {
        Empresa farmacia = DatosDePrueba.empresaHabilitada(TipoEmpresa.FARMACIA);
        when(repository.findByCuit("30-71234567-1")).thenReturn(Optional.of(farmacia));

        assertSame(farmacia, service().buscarPorCuit("30712345671", TipoEmpresa.FARMACIA));
    }

    @Test
    @DisplayName("Buscar por CUIT: no habilitada, de otro tipo o LABORATORIO → 404")
    void buscarPorCuitNoEncontrada() {
        Empresa pendiente = DatosDePrueba.empresa(TipoEmpresa.FARMACIA);
        when(repository.findByCuit("30-71234567-1")).thenReturn(Optional.of(pendiente));
        assertThrows(ResourceNotFoundException.class, () -> service().buscarPorCuit("30-71234567-1", TipoEmpresa.FARMACIA));

        Empresa distribuidora = DatosDePrueba.empresaHabilitada(TipoEmpresa.DISTRIBUIDOR);
        when(repository.findByCuit("30-71234567-1")).thenReturn(Optional.of(distribuidora));
        assertThrows(ResourceNotFoundException.class, () -> service().buscarPorCuit("30-71234567-1", TipoEmpresa.FARMACIA));
        assertThrows(ResourceNotFoundException.class,
                () -> service().buscarPorCuit("30-71234567-1", TipoEmpresa.LABORATORIO));
    }
}
