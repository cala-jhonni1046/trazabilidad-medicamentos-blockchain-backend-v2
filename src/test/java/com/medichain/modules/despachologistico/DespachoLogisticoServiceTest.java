package com.medichain.modules.despachologistico;

import com.medichain.exceptions.ReglaNegocioException;
import com.medichain.exceptions.ResourceNotFoundException;
import com.medichain.modules.auth.UsuarioAutenticado;
import com.medichain.modules.bulto.Bulto;
import com.medichain.modules.bulto.BultoRepository;
import com.medichain.modules.bulto.EstadoBulto;
import com.medichain.modules.cuarentena.AperturaCuarentenas;
import com.medichain.modules.cuarentena.Bloqueo;
import com.medichain.modules.cuarentena.CausaBloqueo;
import com.medichain.modules.cuarentena.EvaluadorBloqueo;
import com.medichain.modules.cuarentena.MotivoBloqueo;
import com.medichain.modules.empresa.Empresa;
import com.medichain.modules.empresa.TipoEmpresa;
import com.medichain.modules.enlacecuit.EnlaceCuit;
import com.medichain.modules.lote.Lote;
import com.medichain.modules.trazabilidad.JsonCanonico;
import com.medichain.modules.trazabilidad.RegistradorEventos;
import com.medichain.modules.trazabilidad.TipoEvento;
import com.medichain.modules.unidadtrazable.EstadoUnidad;
import com.medichain.modules.unidadtrazable.UnidadTrazable;
import com.medichain.modules.unidadtrazable.UnidadTrazableRepository;
import com.medichain.modules.usuario.RolUsuario;
import com.medichain.testutil.DatosDePrueba;
import com.medichain.utils.seguridad.UsuarioActual;
import com.medichain.utils.seguridad.VerificadorEmpresa;
import com.medichain.utils.seguridad.VerificadorUsuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
 * Test unitario DespachoLogisticoServiceTest en MediChain.
 * Viajes con Mockito: creación por tramo (R7), bultos bloqueados (R10),
 * salida, cancelación, robo (R14) y visibilidad.
 */
@ExtendWith(MockitoExtension.class)
class DespachoLogisticoServiceTest {

    @Mock
    private DespachoLogisticoRepository repository;

    @Mock
    private BultoRepository bultoRepository;

    @Mock
    private UnidadTrazableRepository unidadTrazableRepository;

    @Mock
    private EvaluadorBloqueo evaluadorBloqueo;

    @Mock
    private AperturaCuarentenas aperturaCuarentenas;

    @Mock
    private UsuarioActual usuarioActual;

    @Mock
    private VerificadorEmpresa verificadorEmpresa;

    @Mock
    private VerificadorUsuario verificadorUsuario;

    @Mock
    private RegistradorEventos registradorEventos;

    private Empresa laboratorio;
    private Empresa distribuidora;
    private Empresa farmacia;
    private Lote lote;
    private EnlaceCuit circuito;

    @BeforeEach
    void setUp() {
        laboratorio = DatosDePrueba.empresaHabilitada(TipoEmpresa.LABORATORIO);
        distribuidora = DatosDePrueba.empresaHabilitada(TipoEmpresa.DISTRIBUIDOR);
        farmacia = DatosDePrueba.empresaHabilitada(TipoEmpresa.FARMACIA);
        lote = DatosDePrueba.loteDe(laboratorio);
        lote.liberar(null);
        circuito = DatosDePrueba.circuitoAprobado(laboratorio, distribuidora, farmacia);
    }

    /** Construye el Service bajo prueba con los mocks. */
    private DespachoLogisticoService service() {
        lenient().when(repository.save(any(DespachoLogistico.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(evaluadorBloqueo.bloqueosDeBultos(anyCollection())).thenReturn(Map.of());
        return new DespachoLogisticoService(repository, bultoRepository, unidadTrazableRepository, evaluadorBloqueo,
                aperturaCuarentenas, usuarioActual, verificadorEmpresa, verificadorUsuario, registradorEventos);
    }

    /** Autentica como la empresa dada con el rol dado (HABILITADA). */
    private UsuarioAutenticado como(RolUsuario rol, Empresa empresa) {
        UsuarioAutenticado actual = DatosDePrueba.autenticado(rol, empresa);
        when(usuarioActual.obtener()).thenReturn(actual);
        lenient().when(verificadorEmpresa.exigirHabilitada(empresa.getId())).thenReturn(empresa);
        lenient().when(repository.siguienteNumeroCodigo()).thenReturn(1L);
        return actual;
    }

    /** DTO de viaje con los códigos dados. */
    private DespachoLogisticoRequestDTO dto(String... codigos) {
        DespachoLogisticoRequestDTO dto = new DespachoLogisticoRequestDTO();
        dto.setPatente("AB123CD");
        dto.setChofer("Juan Pérez");
        dto.setFechaEstimadaEntrega(OffsetDateTime.now().plusDays(1));
        dto.setBultos(List.of(codigos));
        return dto;
    }

    /** Pone el bulto EN_DEPOSITO en la empresa dada (estado que en 7e deja la recepción del tramo 1). */
    private Bulto enDeposito(Bulto bulto, Empresa deposito) {
        ReflectionTestUtils.setField(bulto, "estado", EstadoBulto.EN_DEPOSITO);
        ReflectionTestUtils.setField(bulto, "ubicacion", deposito);
        return bulto;
    }

    /** Viaje del tramo 1 PROGRAMADO del laboratorio con el bulto dado, registrado en el mock. */
    private DespachoLogistico viajeProgramado(Bulto bulto) {
        DespachoLogistico viaje = DatosDePrueba.viaje(TramoDespacho.LAB_A_DISTRIBUIDOR, laboratorio);
        viaje.agregarBulto(bulto);
        lenient().when(repository.findById(viaje.getId())).thenReturn(Optional.of(viaje));
        return viaje;
    }

    /** Verifica que la acción falle con el código dado. */
    private void fallaCon(String codigo, Runnable accion) {
        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class, accion::run);
        assertEquals(codigo, ex.getCodigoRegla());
    }

    // ---------- Crear: tramo 1 ----------

    @Test
    @DisplayName("Tramo 1 (LAB): VJ-0001 PROGRAMADO, bultos asignados; el evento no lleva patente ni chofer")
    @SuppressWarnings("unchecked")
    void crearTramoUno() {
        UsuarioAutenticado actual = como(RolUsuario.LABORATORIO, laboratorio);
        Bulto bulto = DatosDePrueba.bulto("BUL-0001", lote, circuito);
        when(bultoRepository.findByCodigoIn(anyCollection())).thenReturn(List.of(bulto));

        DespachoLogistico viaje = service().crear(dto("BUL-0001"));

        assertEquals("VJ-0001", viaje.getCodigo());
        assertEquals(TramoDespacho.LAB_A_DISTRIBUIDOR, viaje.getTramo());
        assertEquals(EstadoDespacho.PROGRAMADO, viaje.getEstado());
        assertSame(viaje, bulto.getViajeActual());
        assertEquals(EstadoBulto.ARMADO, bulto.getEstado());
        ArgumentCaptor<Map<String, Object>> datos = ArgumentCaptor.forClass(Map.class);
        verify(registradorEventos).registrar(eq(TipoEvento.VIAJE_CREADO), eq("DespachoLogistico"), any(),
                datos.capture(), eq(actual));
        String json = JsonCanonico.escribir(datos.getValue());
        assertFalse(json.contains("AB123CD"), "la patente no va al evento");
        assertFalse(json.contains("Juan"), "el chofer no va al evento");
        assertEquals(List.of(distribuidora.getId()), datos.getValue().get("paradas"));
    }

    @Test
    @DisplayName("R7: tramo 1 con bultos hacia dos distribuidoras distintas")
    void tramoUnoDosDistribuidoras() {
        como(RolUsuario.LABORATORIO, laboratorio);
        EnlaceCuit otroCircuito = DatosDePrueba.circuitoAprobado(laboratorio,
                DatosDePrueba.empresaHabilitada(TipoEmpresa.DISTRIBUIDOR), DatosDePrueba.empresaHabilitada(TipoEmpresa.FARMACIA));
        when(bultoRepository.findByCodigoIn(anyCollection())).thenReturn(List.of(
                DatosDePrueba.bulto("BUL-0001", lote, circuito), DatosDePrueba.bulto("BUL-0002", lote, otroCircuito)));

        fallaCon("R7", () -> service().crear(dto("BUL-0001", "BUL-0002")));
    }

    @Test
    @DisplayName("Tramo 1 con un bulto de otro laboratorio → 404; bulto inexistente → 404")
    void tramoUnoBultoAjeno() {
        como(RolUsuario.LABORATORIO, DatosDePrueba.empresaHabilitada(TipoEmpresa.LABORATORIO));
        when(bultoRepository.findByCodigoIn(anyCollection())).thenReturn(List.of(DatosDePrueba.bulto("BUL-0001", lote, circuito)));
        assertThrows(ResourceNotFoundException.class, () -> service().crear(dto("BUL-0001")));

        when(bultoRepository.findByCodigoIn(anyCollection())).thenReturn(List.of());
        assertThrows(ResourceNotFoundException.class, () -> service().crear(dto("BUL-9999")));
    }

    @Test
    @DisplayName("R7: un bulto que ya está en otro viaje")
    void bultoYaEnOtroViaje() {
        como(RolUsuario.LABORATORIO, laboratorio);
        Bulto bulto = DatosDePrueba.bulto("BUL-0001", lote, circuito);
        viajeProgramado(bulto);
        when(bultoRepository.findByCodigoIn(anyCollection())).thenReturn(List.of(bulto));

        fallaCon("R7", () -> service().crear(dto("BUL-0001")));
    }

    @Test
    @DisplayName("R10: no se crea un viaje con un bulto bloqueado")
    void crearConBultoBloqueado() {
        como(RolUsuario.LABORATORIO, laboratorio);
        Bulto bloqueado = DatosDePrueba.bulto("BUL-0001", lote, circuito);
        when(bultoRepository.findByCodigoIn(anyCollection())).thenReturn(List.of(bloqueado));
        DespachoLogisticoService service = service();
        when(evaluadorBloqueo.bloqueosDeBultos(anyCollection()))
                .thenReturn(Map.of(bloqueado.getId(), new Bloqueo(CausaBloqueo.BULTO_CON_MEDIDA_VIGENTE, "cuarentena vigente")));

        fallaCon("R10", () -> service.crear(dto("BUL-0001")));
        verify(repository, never()).save(any());
    }

    // ---------- Crear: tramo 2 ----------

    @Test
    @DisplayName("Tramo 2 (DIST): bultos EN_DEPOSITO en su depósito, varias farmacias como paradas")
    void crearTramoDos() {
        como(RolUsuario.DISTRIBUIDOR, distribuidora);
        Empresa otraFarmacia = DatosDePrueba.empresaHabilitada(TipoEmpresa.FARMACIA);
        EnlaceCuit otroCircuito = DatosDePrueba.circuitoAprobado(laboratorio, distribuidora, otraFarmacia);
        Bulto uno = enDeposito(DatosDePrueba.bulto("BUL-0001", lote, circuito), distribuidora);
        Bulto dos = enDeposito(DatosDePrueba.bulto("BUL-0002", lote, otroCircuito), distribuidora);
        when(bultoRepository.findByCodigoIn(anyCollection())).thenReturn(List.of(uno, dos));

        DespachoLogistico viaje = service().crear(dto("BUL-0001", "BUL-0002"));

        assertEquals(TramoDespacho.DISTRIBUIDOR_A_FARMACIA, viaje.getTramo());
        assertEquals(List.of(farmacia, otraFarmacia), viaje.paradas());
    }

    @Test
    @DisplayName("R7: tramo 2 con un bulto que no está en su depósito o es de otra distribuidora")
    void tramoDosBultoAjeno() {
        como(RolUsuario.DISTRIBUIDOR, distribuidora);
        Bulto armado = DatosDePrueba.bulto("BUL-0001", lote, circuito);
        when(bultoRepository.findByCodigoIn(anyCollection())).thenReturn(List.of(armado));
        fallaCon("R7", () -> service().crear(dto("BUL-0001")));

        EnlaceCuit deOtra = DatosDePrueba.circuitoAprobado(laboratorio,
                DatosDePrueba.empresaHabilitada(TipoEmpresa.DISTRIBUIDOR), farmacia);
        Bulto ajeno = enDeposito(DatosDePrueba.bulto("BUL-0002", lote, deOtra), distribuidora);
        when(bultoRepository.findByCodigoIn(anyCollection())).thenReturn(List.of(ajeno));
        fallaCon("R7", () -> service().crear(dto("BUL-0002")));
    }

    // ---------- Salida ----------

    @Test
    @DisplayName("Salida: viaje, bultos y cajas EN_TRANSITO; evento VIAJE_SALIDA")
    void salida() {
        Bulto bulto = DatosDePrueba.bulto("BUL-0001", lote, circuito);
        DespachoLogistico viaje = viajeProgramado(bulto);
        UnidadTrazable caja = DatosDePrueba.caja(lote, "S1");
        caja.asignarABulto(bulto);
        UsuarioAutenticado actual = como(RolUsuario.LABORATORIO, laboratorio);
        when(unidadTrazableRepository.findByBultoIdIn(anyCollection())).thenReturn(List.of(caja));

        service().salida(viaje.getId());

        assertEquals(EstadoDespacho.EN_TRANSITO, viaje.getEstado());
        assertEquals(EstadoBulto.EN_TRANSITO, bulto.getEstado());
        assertNull(bulto.getUbicacion());
        assertEquals(EstadoUnidad.EN_TRANSITO, caja.getEstado());
        verify(registradorEventos).registrar(eq(TipoEvento.VIAJE_SALIDA), eq("DespachoLogistico"), eq(viaje.getId()),
                anyMap(), eq(actual));
    }

    @Test
    @DisplayName("R10: salida con un bulto bloqueado → nada cambia")
    void salidaConBultoBloqueado() {
        Bulto bulto = DatosDePrueba.bulto("BUL-0001", lote, circuito);
        DespachoLogistico viaje = viajeProgramado(bulto);
        como(RolUsuario.LABORATORIO, laboratorio);
        DespachoLogisticoService service = service();
        when(evaluadorBloqueo.bloqueosDeBultos(anyCollection()))
                .thenReturn(Map.of(bulto.getId(), new Bloqueo(CausaBloqueo.BULTO_CON_MEDIDA_VIGENTE, "cuarentena vigente")));

        fallaCon("R10", () -> service.salida(viaje.getId()));
        assertEquals(EstadoDespacho.PROGRAMADO, viaje.getEstado());
        assertEquals(EstadoBulto.ARMADO, bulto.getEstado());
    }

    @Test
    @DisplayName("Salida dos veces → TRANSICION_INVALIDA; empresa que no es la origen → 404")
    void salidaInvalida() {
        DespachoLogistico viaje = viajeProgramado(DatosDePrueba.bulto("BUL-0001", lote, circuito));
        como(RolUsuario.LABORATORIO, laboratorio);
        DespachoLogisticoService service = service();
        service.salida(viaje.getId());
        fallaCon("TRANSICION_INVALIDA", () -> service.salida(viaje.getId()));

        como(RolUsuario.DISTRIBUIDOR, distribuidora);
        assertThrows(ResourceNotFoundException.class, () -> service.salida(viaje.getId()));
    }

    // ---------- Cancelar ----------

    @Test
    @DisplayName("Cancelar: CANCELADO, los bultos quedan sin viaje y se pueden desarmar después")
    void cancelar() {
        Bulto bulto = DatosDePrueba.bulto("BUL-0001", lote, circuito);
        DespachoLogistico viaje = viajeProgramado(bulto);
        UsuarioAutenticado actual = como(RolUsuario.LABORATORIO, laboratorio);

        service().cancelar(viaje.getId(), "Camión averiado");

        assertEquals(EstadoDespacho.CANCELADO, viaje.getEstado());
        assertNull(bulto.getViajeActual());
        assertEquals(EstadoBulto.ARMADO, bulto.getEstado());
        verify(registradorEventos).registrar(eq(TipoEvento.VIAJE_CANCELADO), eq("DespachoLogistico"),
                eq(viaje.getId()), anyMap(), eq(actual));
        bulto.desarmar();
        assertEquals(EstadoBulto.DESARMADO, bulto.getEstado());
    }

    @Test
    @DisplayName("Cancelar un viaje EN_TRANSITO → TRANSICION_INVALIDA; empresa que no es la origen → 404")
    void cancelarInvalido() {
        DespachoLogistico viaje = viajeProgramado(DatosDePrueba.bulto("BUL-0001", lote, circuito));
        como(RolUsuario.LABORATORIO, laboratorio);
        DespachoLogisticoService service = service();
        service.salida(viaje.getId());
        fallaCon("TRANSICION_INVALIDA", () -> service.cancelar(viaje.getId(), "tarde"));

        como(RolUsuario.LABORATORIO, DatosDePrueba.empresaHabilitada(TipoEmpresa.LABORATORIO));
        assertThrows(ResourceNotFoundException.class, () -> service.cancelar(viaje.getId(), "x"));
    }

    // ---------- Robo ----------

    @Test
    @DisplayName("Robo (R14): viaje ROBADO, bultos ROBADO, cajas ROBADA y cuarentena DESPACHO de todos los bultos")
    @SuppressWarnings("unchecked")
    void robo() {
        Bulto bulto = DatosDePrueba.bulto("BUL-0001", lote, circuito);
        DespachoLogistico viaje = viajeProgramado(bulto);
        UnidadTrazable caja = DatosDePrueba.caja(lote, "S1");
        caja.asignarABulto(bulto);
        como(RolUsuario.LABORATORIO, laboratorio);
        when(unidadTrazableRepository.findByBultoIdIn(anyCollection())).thenReturn(List.of(caja));
        DespachoLogisticoService service = service();
        service.salida(viaje.getId());

        service.robo(viaje.getId(), "Asalto en ruta");

        assertEquals(EstadoDespacho.ROBADO, viaje.getEstado());
        assertEquals(EstadoBulto.ROBADO, bulto.getEstado());
        assertEquals(EstadoUnidad.ROBADA, caja.getEstado());
        ArgumentCaptor<Collection<Bulto>> bultos = ArgumentCaptor.forClass(Collection.class);
        verify(aperturaCuarentenas).abrirPorDespacho(eq(MotivoBloqueo.ROBO), eq(viaje), bultos.capture());
        assertTrue(bultos.getValue().contains(bulto));
        verify(registradorEventos).registrar(eq(TipoEvento.ROBO_EXTRAVIO), eq("DespachoLogistico"), eq(viaje.getId()),
                anyMap(), any(UsuarioAutenticado.class));
    }

    @Test
    @DisplayName("Robo de un viaje PROGRAMADO → TRANSICION_INVALIDA")
    void roboProgramado() {
        DespachoLogistico viaje = viajeProgramado(DatosDePrueba.bulto("BUL-0001", lote, circuito));
        como(RolUsuario.LABORATORIO, laboratorio);

        fallaCon("TRANSICION_INVALIDA", () -> service().robo(viaje.getId(), "x"));
    }

    // ---------- Visibilidad ----------

    @Test
    @DisplayName("La farmacia ve un viaje del tramo 2 con parada en ella; otra farmacia → 404")
    void visibilidadFarmacia() {
        Bulto bulto = enDeposito(DatosDePrueba.bulto("BUL-0001", lote, circuito), distribuidora);
        DespachoLogistico viaje = DatosDePrueba.viaje(TramoDespacho.DISTRIBUIDOR_A_FARMACIA, distribuidora);
        viaje.agregarBulto(bulto);
        when(repository.findById(viaje.getId())).thenReturn(Optional.of(viaje));

        when(usuarioActual.obtener()).thenReturn(DatosDePrueba.autenticado(RolUsuario.FARMACIA, farmacia));
        assertSame(viaje, service().getById(viaje.getId()));

        when(usuarioActual.obtener()).thenReturn(DatosDePrueba.autenticado(RolUsuario.FARMACIA,
                DatosDePrueba.empresaHabilitada(TipoEmpresa.FARMACIA)));
        assertThrows(ResourceNotFoundException.class, () -> service().getById(viaje.getId()));
    }
}
