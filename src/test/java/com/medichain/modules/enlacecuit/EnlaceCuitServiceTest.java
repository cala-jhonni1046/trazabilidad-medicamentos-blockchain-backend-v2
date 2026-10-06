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
import com.medichain.modules.trazabilidad.HashUtil;
import com.medichain.modules.trazabilidad.RegistradorEventos;
import com.medichain.modules.trazabilidad.TipoEvento;
import com.medichain.modules.usuario.RolUsuario;
import com.medichain.modules.usuario.Usuario;
import com.medichain.testutil.DatosDePrueba;
import com.medichain.utils.enums.Provincia;
import com.medichain.utils.seguridad.UsuarioActual;
import com.medichain.utils.seguridad.VerificadorEmpresa;
import com.medichain.utils.seguridad.VerificadorUsuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test unitario EnlaceCuitServiceTest en MediChain.
 * Circuitos con Mockito: visibilidad, proponer (R5 completa), aceptar y
 * rechazar por empresa, tomar/asignar/aprobar/rechazar por inspector (D1)
 * y suspender/rehabilitar manual. Cada acción: caso feliz con su evento,
 * marca o rol incorrecto, empresa no habilitada, provincia incorrecta y
 * transición inválida.
 */
@ExtendWith(MockitoExtension.class)
class EnlaceCuitServiceTest {

    @Mock
    private RegistradorEventos registradorEventos;

    @Mock
    private EnlaceCuitRepository repository;

    @Mock
    private UsuarioActual usuarioActual;

    @Mock
    private EmpresaRepository empresaRepository;

    @Mock
    private InspectorAnmatRepository inspectorAnmatRepository;

    @Mock
    private VerificadorEmpresa verificadorEmpresa;

    @Mock
    private VerificadorUsuario verificadorUsuario;

    private final Empresa laboratorio = DatosDePrueba.empresaHabilitada(TipoEmpresa.LABORATORIO);
    private final Empresa distribuidora = DatosDePrueba.empresaHabilitada(TipoEmpresa.DISTRIBUIDOR);
    private final Empresa farmacia = DatosDePrueba.empresaHabilitada(TipoEmpresa.FARMACIA);

    /** Construye el Service bajo prueba con los mocks; save devuelve lo mismo. */
    private EnlaceCuitService service() {
        lenient().when(repository.save(any(EnlaceCuit.class))).thenAnswer(inv -> inv.getArgument(0));
        return new EnlaceCuitService(repository, empresaRepository, inspectorAnmatRepository, usuarioActual,
                verificadorEmpresa, verificadorUsuario, registradorEventos);
    }

    /** Registra el circuito en el mock del repositorio. */
    private EnlaceCuit enRepositorio(EnlaceCuit circuito) {
        lenient().when(repository.findById(circuito.getId())).thenReturn(Optional.of(circuito));
        return circuito;
    }

    /** Autentica como admin de la empresa dada (exigirAdminEmpresa y exigirHabilitada pasan). */
    private UsuarioAutenticado comoAdminDe(Empresa empresa, RolUsuario rol) {
        UsuarioAutenticado actual = DatosDePrueba.autenticado(rol, empresa);
        when(usuarioActual.obtener()).thenReturn(actual);
        lenient().when(verificadorEmpresa.exigirHabilitada(empresa.getId())).thenReturn(empresa);
        return actual;
    }

    /** Autentica como el inspector dado (resuelto contra la base por el mock). */
    private UsuarioAutenticado comoInspector(InspectorAnmat inspector) {
        UsuarioAutenticado actual = DatosDePrueba.autenticadoInspector(inspector);
        when(usuarioActual.obtener()).thenReturn(actual);
        when(inspectorAnmatRepository.findByUsuarioId(actual.getUsuarioId())).thenReturn(Optional.of(inspector));
        return actual;
    }

    /** Verifica que la acción falle con el código dado. */
    private void fallaCon(String codigo, Runnable accion) {
        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class, accion::run);
        assertEquals(codigo, ex.getCodigoRegla());
    }

    // ---------- Visibilidad ----------

    @Test
    @DisplayName("Ve lo suyo: la farmacia ve un circuito donde participa")
    void veCircuitoDondeParticipa() {
        EnlaceCuit circuito = enRepositorio(DatosDePrueba.circuito(laboratorio, distribuidora, farmacia));
        when(usuarioActual.obtener()).thenReturn(DatosDePrueba.autenticado(RolUsuario.FARMACIA, farmacia));

        assertSame(circuito, service().getById(circuito.getId()));
    }

    @Test
    @DisplayName("No ve lo ajeno: un circuito donde no participa responde 404")
    void noVeCircuitoAjeno() {
        EnlaceCuit circuito = enRepositorio(DatosDePrueba.circuito(laboratorio, distribuidora, farmacia));
        Empresa otra = DatosDePrueba.empresaHabilitada(TipoEmpresa.FARMACIA);
        when(usuarioActual.obtener()).thenReturn(DatosDePrueba.autenticado(RolUsuario.FARMACIA, otra));

        assertThrows(ResourceNotFoundException.class, () -> service().getById(circuito.getId()));
    }

    // ---------- Proponer ----------

    /** Prepara un DT del laboratorio y las búsquedas por CUIT de distribuidora y farmacia. */
    private UsuarioAutenticado prepararPropuesta(String cuitDist, Empresa dist, String cuitFarm, Empresa farm) {
        UsuarioAutenticado actual = DatosDePrueba.autenticado(RolUsuario.LABORATORIO, laboratorio);
        when(usuarioActual.obtener()).thenReturn(actual);
        Usuario dt = new Usuario("dt@lab.demo", "hash", "Elena", "Sosa", "12345678", RolUsuario.LABORATORIO);
        lenient().when(verificadorUsuario.exigirDirectorTecnico(actual.getUsuarioId())).thenReturn(dt);
        lenient().when(verificadorEmpresa.exigirHabilitada(laboratorio.getId())).thenReturn(laboratorio);
        lenient().when(empresaRepository.findByCuit(cuitDist)).thenReturn(Optional.ofNullable(dist));
        lenient().when(empresaRepository.findByCuit(cuitFarm)).thenReturn(Optional.ofNullable(farm));
        lenient().when(repository.siguienteNumeroCodigo()).thenReturn(7L);
        return actual;
    }

    @Test
    @DisplayName("Proponer: CUIT sin guiones se normaliza; código CIR-0007 de la secuencia; evento CIRCUITO_PROPUESTO")
    void proponerCasoFeliz() {
        UsuarioAutenticado actual = prepararPropuesta("30-71000002-2", distribuidora, "30-71000003-0", farmacia);

        EnlaceCuit circuito = service().proponer("30710000022", "30710000030");

        assertEquals("CIR-0007", circuito.getCodigo());
        assertEquals(EstadoEnlaceCuit.PENDIENTE_EMPRESAS, circuito.getEstado());
        assertSame(laboratorio, circuito.getLaboratorio());
        verify(registradorEventos).registrar(eq(TipoEvento.CIRCUITO_PROPUESTO), eq("EnlaceCuit"), any(),
                anyMap(), eq(actual));
    }

    @Test
    @DisplayName("Proponer sin ser DT → 403")
    void proponerSinSerDt() {
        UsuarioAutenticado actual = prepararPropuesta("30-71000002-2", distribuidora, "30-71000003-0", farmacia);
        when(verificadorUsuario.exigirDirectorTecnico(actual.getUsuarioId()))
                .thenThrow(new AccessDeniedException("no es DT"));

        assertThrows(AccessDeniedException.class, () -> service().proponer("30-71000002-2", "30-71000003-0"));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Proponer con laboratorio no habilitado → R2")
    void proponerLaboratorioNoHabilitado() {
        prepararPropuesta("30-71000002-2", distribuidora, "30-71000003-0", farmacia);
        when(verificadorEmpresa.exigirHabilitada(laboratorio.getId()))
                .thenThrow(new ReglaNegocioException("R2", "no habilitada"));

        fallaCon("R2", () -> service().proponer("30-71000002-2", "30-71000003-0"));
    }

    @Test
    @DisplayName("Proponer con farmacia no habilitada, inexistente o tipo cruzado → R5")
    void proponerEmpresaInvalida() {
        Empresa farmaciaPendiente = DatosDePrueba.empresa(TipoEmpresa.FARMACIA);
        prepararPropuesta("30-71000002-2", distribuidora, "30-71000003-0", farmaciaPendiente);
        fallaCon("R5", () -> service().proponer("30-71000002-2", "30-71000003-0"));

        when(empresaRepository.findByCuit("30-71000003-0")).thenReturn(Optional.empty());
        fallaCon("R5", () -> service().proponer("30-71000002-2", "30-71000003-0"));

        // CUIT de una farmacia pasado como distribuidora.
        when(empresaRepository.findByCuit("30-71000002-2")).thenReturn(Optional.of(farmacia));
        fallaCon("R5", () -> service().proponer("30-71000002-2", "30-71000003-0"));
    }

    @Test
    @DisplayName("Proponer con un circuito vigente para el mismo par laboratorio–farmacia → R5")
    void proponerParVigente() {
        prepararPropuesta("30-71000002-2", distribuidora, "30-71000003-0", farmacia);
        when(repository.existsByLaboratorioIdAndFarmaciaIdAndEstadoIn(eq(laboratorio.getId()), eq(farmacia.getId()),
                anyCollection())).thenReturn(true);

        fallaCon("R5", () -> service().proponer("30-71000002-2", "30-71000003-0"));
        verify(repository, never()).siguienteNumeroCodigo();
    }

    @Test
    @DisplayName("El control de par vigente usa solo los estados vigentes (RECHAZADO no cuenta)")
    void estadosVigentesNoIncluyenRechazado() {
        assertEquals(4, EnlaceCuitRepository.ESTADOS_VIGENTES.size());
        assertTrue(!EnlaceCuitRepository.ESTADOS_VIGENTES.contains(EstadoEnlaceCuit.RECHAZADO));
    }

    // ---------- Aceptar / rechazar (empresa) ----------

    @Test
    @DisplayName("Aceptar: la distribuidora acepta (parcial) y después la farmacia completa → PENDIENTE_INSPECTOR")
    @SuppressWarnings("unchecked")
    void aceptarParcialYCompleto() {
        EnlaceCuit circuito = enRepositorio(DatosDePrueba.circuito(laboratorio, distribuidora, farmacia));
        comoAdminDe(distribuidora, RolUsuario.DISTRIBUIDOR);
        service().aceptar(circuito.getId());
        assertEquals(EstadoEnlaceCuit.PENDIENTE_EMPRESAS, circuito.getEstado());

        comoAdminDe(farmacia, RolUsuario.FARMACIA);
        service().aceptar(circuito.getId());

        assertEquals(EstadoEnlaceCuit.PENDIENTE_INSPECTOR, circuito.getEstado());
        ArgumentCaptor<Map<String, Object>> datos = ArgumentCaptor.forClass(Map.class);
        verify(registradorEventos, org.mockito.Mockito.times(2)).registrar(eq(TipoEvento.CIRCUITO_ACEPTADO),
                eq("EnlaceCuit"), eq(circuito.getId()), datos.capture(), any(UsuarioAutenticado.class));
        assertEquals(false, datos.getAllValues().get(0).get("completo"));
        assertEquals("FARMACIA", datos.getAllValues().get(1).get("parte"));
        assertEquals(true, datos.getAllValues().get(1).get("completo"));
    }

    @Test
    @DisplayName("Aceptar sin ser admin → 403")
    void aceptarSinSerAdmin() {
        EnlaceCuit circuito = enRepositorio(DatosDePrueba.circuito(laboratorio, distribuidora, farmacia));
        UsuarioAutenticado actual = comoAdminDe(distribuidora, RolUsuario.DISTRIBUIDOR);
        when(verificadorUsuario.exigirAdminEmpresa(actual.getUsuarioId())).thenThrow(new AccessDeniedException("no admin"));

        assertThrows(AccessDeniedException.class, () -> service().aceptar(circuito.getId()));
    }

    @Test
    @DisplayName("Aceptar un circuito donde la empresa no participa → 404")
    void aceptarCircuitoAjeno() {
        EnlaceCuit circuito = enRepositorio(DatosDePrueba.circuito(laboratorio, distribuidora, farmacia));
        comoAdminDe(DatosDePrueba.empresaHabilitada(TipoEmpresa.FARMACIA), RolUsuario.FARMACIA);

        assertThrows(ResourceNotFoundException.class, () -> service().aceptar(circuito.getId()));
    }

    @Test
    @DisplayName("Aceptar dos veces → TRANSICION_INVALIDA")
    void aceptarDosVeces() {
        EnlaceCuit circuito = enRepositorio(DatosDePrueba.circuito(laboratorio, distribuidora, farmacia));
        comoAdminDe(distribuidora, RolUsuario.DISTRIBUIDOR);
        EnlaceCuitService service = service();
        service.aceptar(circuito.getId());

        fallaCon("TRANSICION_INVALIDA", () -> service.aceptar(circuito.getId()));
    }

    @Test
    @DisplayName("Aceptar cuando otra empresa del circuito se suspendió desde la propuesta → R5")
    void aceptarConEmpresaSuspendida() {
        EnlaceCuit circuito = enRepositorio(DatosDePrueba.circuito(laboratorio, distribuidora, farmacia));
        comoAdminDe(distribuidora, RolUsuario.DISTRIBUIDOR);
        laboratorio.suspender("inspección");

        fallaCon("R5", () -> service().aceptar(circuito.getId()));
    }

    @Test
    @DisplayName("Rechazar (empresa): RECHAZADO por FARMACIA, evento con hash del motivo")
    @SuppressWarnings("unchecked")
    void rechazarPorEmpresa() {
        EnlaceCuit circuito = enRepositorio(DatosDePrueba.circuito(laboratorio, distribuidora, farmacia));
        comoAdminDe(farmacia, RolUsuario.FARMACIA);

        service().rechazarPorEmpresa(circuito.getId(), "No trabajamos con esa distribuidora");

        assertEquals(EstadoEnlaceCuit.RECHAZADO, circuito.getEstado());
        assertEquals(OrigenRechazo.FARMACIA, circuito.getRechazadoPor());
        ArgumentCaptor<Map<String, Object>> datos = ArgumentCaptor.forClass(Map.class);
        verify(registradorEventos).registrar(eq(TipoEvento.CIRCUITO_RECHAZADO), eq("EnlaceCuit"),
                eq(circuito.getId()), datos.capture(), any(UsuarioAutenticado.class));
        assertEquals(HashUtil.sha256Hex("No trabajamos con esa distribuidora"), datos.getValue().get("motivoHash"));
    }

    @Test
    @DisplayName("Rechazar (empresa) un circuito ya PENDIENTE_INSPECTOR → TRANSICION_INVALIDA")
    void rechazarPorEmpresaTarde() {
        EnlaceCuit circuito = enRepositorio(DatosDePrueba.circuitoPendienteInspector(laboratorio, distribuidora, farmacia));
        comoAdminDe(farmacia, RolUsuario.FARMACIA);

        fallaCon("TRANSICION_INVALIDA", () -> service().rechazarPorEmpresa(circuito.getId(), "tarde"));
    }

    // ---------- Inspector: tomar / aprobar / rechazar ----------

    @Test
    @DisplayName("Tomar y aprobar: inspector de la provincia de la farmacia; eventos CIRCUITO_TOMADO y CIRCUITO_APROBADO")
    void tomarYAprobar() {
        EnlaceCuit circuito = enRepositorio(DatosDePrueba.circuitoPendienteInspector(laboratorio, distribuidora, farmacia));
        InspectorAnmat inspector = DatosDePrueba.inspector(Provincia.CORDOBA);
        comoInspector(inspector);
        EnlaceCuitService service = service();

        service.tomar(circuito.getId());
        service.aprobar(circuito.getId());

        assertEquals(EstadoEnlaceCuit.APROBADO, circuito.getEstado());
        verify(registradorEventos).registrar(eq(TipoEvento.CIRCUITO_TOMADO), eq("EnlaceCuit"), eq(circuito.getId()),
                anyMap(), any(UsuarioAutenticado.class));
        verify(registradorEventos).registrar(eq(TipoEvento.CIRCUITO_APROBADO), eq("EnlaceCuit"), eq(circuito.getId()),
                anyMap(), any(UsuarioAutenticado.class));
    }

    @Test
    @DisplayName("Tomar un circuito de una farmacia de otra provincia → 404 (D1)")
    void tomarOtraProvincia() {
        EnlaceCuit circuito = enRepositorio(DatosDePrueba.circuitoPendienteInspector(laboratorio, distribuidora, farmacia));
        comoInspector(DatosDePrueba.inspector(Provincia.MENDOZA));

        assertThrows(ResourceNotFoundException.class, () -> service().tomar(circuito.getId()));
    }

    @Test
    @DisplayName("Tomar un circuito todavía PENDIENTE_EMPRESAS → TRANSICION_INVALIDA")
    void tomarAntesDeLasAceptaciones() {
        EnlaceCuit circuito = enRepositorio(DatosDePrueba.circuito(laboratorio, distribuidora, farmacia));
        comoInspector(DatosDePrueba.inspector(Provincia.CORDOBA));

        fallaCon("TRANSICION_INVALIDA", () -> service().tomar(circuito.getId()));
    }

    @Test
    @DisplayName("Aprobar sin haber tomado → TRANSICION_INVALIDA")
    void aprobarSinTomar() {
        EnlaceCuit circuito = enRepositorio(DatosDePrueba.circuitoPendienteInspector(laboratorio, distribuidora, farmacia));
        comoInspector(DatosDePrueba.inspector(Provincia.CORDOBA));

        fallaCon("TRANSICION_INVALIDA", () -> service().aprobar(circuito.getId()));
    }

    @Test
    @DisplayName("Aprobar cuando la farmacia se suspendió entre la propuesta y la aprobación → R5")
    void aprobarConEmpresaSuspendida() {
        EnlaceCuit circuito = enRepositorio(DatosDePrueba.circuitoPendienteInspector(laboratorio, distribuidora, farmacia));
        InspectorAnmat inspector = DatosDePrueba.inspector(Provincia.CORDOBA);
        circuito.tomar(inspector);
        comoInspector(inspector);
        farmacia.suspender("inspección");

        fallaCon("R5", () -> service().aprobar(circuito.getId()));
        assertEquals(EstadoEnlaceCuit.PENDIENTE_INSPECTOR, circuito.getEstado());
    }

    @Test
    @DisplayName("Inspector dado de baja → 403")
    void inspectorDeBaja() {
        EnlaceCuit circuito = enRepositorio(DatosDePrueba.circuitoPendienteInspector(laboratorio, distribuidora, farmacia));
        InspectorAnmat inspector = DatosDePrueba.inspector(Provincia.CORDOBA);
        inspector.darDeBaja();
        comoInspector(inspector);

        assertThrows(AccessDeniedException.class, () -> service().tomar(circuito.getId()));
    }

    @Test
    @DisplayName("Rechazar (inspector): definitivo, rechazadoPor INSPECTOR")
    void rechazarPorInspector() {
        EnlaceCuit circuito = enRepositorio(DatosDePrueba.circuitoPendienteInspector(laboratorio, distribuidora, farmacia));
        InspectorAnmat inspector = DatosDePrueba.inspector(Provincia.CORDOBA);
        circuito.tomar(inspector);
        comoInspector(inspector);

        service().rechazarPorInspector(circuito.getId(), "Documentación incompleta");

        assertEquals(EstadoEnlaceCuit.RECHAZADO, circuito.getEstado());
        assertEquals(OrigenRechazo.INSPECTOR, circuito.getRechazadoPor());
    }

    // ---------- Asignar (Sede) ----------

    @Test
    @DisplayName("Asignar: provincia de la farmacia sin inspectores ACTIVO → asignado, evento CIRCUITO_ASIGNADO")
    void asignarSinInspectoresEnLaProvincia() {
        EnlaceCuit circuito = enRepositorio(DatosDePrueba.circuitoPendienteInspector(laboratorio, distribuidora, farmacia));
        when(usuarioActual.obtener()).thenReturn(DatosDePrueba.autenticado(RolUsuario.SEDE_CENTRAL, null));
        InspectorAnmat asignado = DatosDePrueba.inspector(Provincia.MENDOZA);
        when(inspectorAnmatRepository.existsByProvinciaAndEstado(Provincia.CORDOBA, EstadoInspector.ACTIVO))
                .thenReturn(false);
        when(inspectorAnmatRepository.findById(asignado.getId())).thenReturn(Optional.of(asignado));

        service().asignar(circuito.getId(), asignado.getId());

        assertTrue(circuito.esRevisor(asignado));
        verify(registradorEventos).registrar(eq(TipoEvento.CIRCUITO_ASIGNADO), eq("EnlaceCuit"), eq(circuito.getId()),
                anyMap(), any(UsuarioAutenticado.class));
    }

    @Test
    @DisplayName("Asignar cuando la provincia de la farmacia SÍ tiene inspectores ACTIVO → R5")
    void asignarConInspectoresEnLaProvincia() {
        EnlaceCuit circuito = enRepositorio(DatosDePrueba.circuitoPendienteInspector(laboratorio, distribuidora, farmacia));
        when(usuarioActual.obtener()).thenReturn(DatosDePrueba.autenticado(RolUsuario.SEDE_CENTRAL, null));
        when(inspectorAnmatRepository.existsByProvinciaAndEstado(Provincia.CORDOBA, EstadoInspector.ACTIVO))
                .thenReturn(true);

        fallaCon("R5", () -> service().asignar(circuito.getId(), UUID.randomUUID()));
    }

    @Test
    @DisplayName("El inspector asignado de otra provincia puede aprobar (es el revisor)")
    void asignadoDeOtraProvinciaAprueba() {
        EnlaceCuit circuito = enRepositorio(DatosDePrueba.circuitoPendienteInspector(laboratorio, distribuidora, farmacia));
        InspectorAnmat asignado = DatosDePrueba.inspector(Provincia.MENDOZA);
        circuito.asignar(asignado);
        comoInspector(asignado);

        service().aprobar(circuito.getId());

        assertEquals(EstadoEnlaceCuit.APROBADO, circuito.getEstado());
    }

    // ---------- Suspender / rehabilitar manual ----------

    @Test
    @DisplayName("Suspender (Sede) y rehabilitar (inspector de la provincia) un circuito APROBADO")
    void suspenderYRehabilitarManual() {
        EnlaceCuit circuito = enRepositorio(DatosDePrueba.circuitoAprobado(laboratorio, distribuidora, farmacia));
        when(usuarioActual.obtener()).thenReturn(DatosDePrueba.autenticado(RolUsuario.SEDE_CENTRAL, null));
        service().suspender(circuito.getId(), "Denuncia en investigación");
        assertEquals(EstadoEnlaceCuit.SUSPENDIDO, circuito.getEstado());

        comoInspector(DatosDePrueba.inspector(Provincia.CORDOBA));
        service().rehabilitar(circuito.getId());

        assertEquals(EstadoEnlaceCuit.APROBADO, circuito.getEstado());
        verify(registradorEventos).registrar(eq(TipoEvento.CIRCUITO_SUSPENDIDO), eq("EnlaceCuit"), eq(circuito.getId()),
                anyMap(), any(UsuarioAutenticado.class));
        verify(registradorEventos).registrar(eq(TipoEvento.CIRCUITO_REHABILITADO), eq("EnlaceCuit"),
                eq(circuito.getId()), anyMap(), any(UsuarioAutenticado.class));
    }

    @Test
    @DisplayName("Suspender: inspector de otra provincia → 404; circuito PENDIENTE → TRANSICION_INVALIDA")
    void suspenderInvalido() {
        EnlaceCuit aprobado = enRepositorio(DatosDePrueba.circuitoAprobado(laboratorio, distribuidora, farmacia));
        comoInspector(DatosDePrueba.inspector(Provincia.MENDOZA));
        assertThrows(ResourceNotFoundException.class, () -> service().suspender(aprobado.getId(), "x"));

        EnlaceCuit pendiente = enRepositorio(DatosDePrueba.circuito(laboratorio, distribuidora, farmacia));
        when(usuarioActual.obtener()).thenReturn(DatosDePrueba.autenticado(RolUsuario.SEDE_CENTRAL, null));
        fallaCon("TRANSICION_INVALIDA", () -> service().suspender(pendiente.getId(), "x"));
    }

    @Test
    @DisplayName("Rehabilitar a mano un circuito suspendido por cascada → TRANSICION_INVALIDA")
    void rehabilitarManualDeCascada() {
        EnlaceCuit circuito = enRepositorio(DatosDePrueba.circuitoAprobado(laboratorio, distribuidora, farmacia));
        circuito.suspenderPorEmpresa();
        when(usuarioActual.obtener()).thenReturn(DatosDePrueba.autenticado(RolUsuario.SEDE_CENTRAL, null));

        fallaCon("TRANSICION_INVALIDA", () -> service().rehabilitar(circuito.getId()));
    }

    @Test
    @DisplayName("Rehabilitar a mano con una empresa del circuito suspendida → R5")
    void rehabilitarManualConEmpresaSuspendida() {
        EnlaceCuit circuito = enRepositorio(DatosDePrueba.circuitoAprobado(laboratorio, distribuidora, farmacia));
        circuito.suspenderManual("x");
        distribuidora.suspender("inspección");
        when(usuarioActual.obtener()).thenReturn(DatosDePrueba.autenticado(RolUsuario.SEDE_CENTRAL, null));

        fallaCon("R5", () -> service().rehabilitar(circuito.getId()));
    }
}
