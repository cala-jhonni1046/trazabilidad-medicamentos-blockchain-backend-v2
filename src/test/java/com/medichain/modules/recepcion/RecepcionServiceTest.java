package com.medichain.modules.recepcion;

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
import com.medichain.modules.despachologistico.DespachoLogistico;
import com.medichain.modules.despachologistico.DespachoLogisticoRepository;
import com.medichain.modules.despachologistico.EstadoDespacho;
import com.medichain.modules.despachologistico.TramoDespacho;
import com.medichain.modules.empresa.Empresa;
import com.medichain.modules.empresa.TipoEmpresa;
import com.medichain.modules.enlacecuit.EnlaceCuit;
import com.medichain.modules.lote.Lote;
import com.medichain.modules.telemetriatemperatura.TelemetriaTemperatura;
import com.medichain.modules.telemetriatemperatura.TelemetriaTemperaturaRepository;
import com.medichain.modules.trazabilidad.RegistradorEventos;
import com.medichain.modules.trazabilidad.RegistradorEventosAparte;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
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
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test unitario RecepcionServiceTest en MediChain (R8, R10, R7).
 * Recepción conforme (tramo 1 y 2), cada causa de rechazo con su
 * cuarentena BULTO, bulto bloqueado rechazado automáticamente, empresa
 * que no es la destino, inexistente y duplicado (con su intento) y
 * finalización del viaje con el resumen de temperatura.
 */
@ExtendWith(MockitoExtension.class)
class RecepcionServiceTest {

    @Mock
    private RecepcionRepository repository;

    @Mock
    private BultoRepository bultoRepository;

    @Mock
    private DespachoLogisticoRepository despachoLogisticoRepository;

    @Mock
    private UnidadTrazableRepository unidadTrazableRepository;

    @Mock
    private TelemetriaTemperaturaRepository telemetriaTemperaturaRepository;

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

    @Mock
    private RegistradorEventosAparte registradorEventosAparte;

    private Empresa laboratorio;
    private Empresa distribuidora;
    private Empresa farmacia;
    private Lote lote;
    private Bulto bulto;
    private DespachoLogistico viaje;
    private List<UnidadTrazable> cajas;
    private final List<Recepcion> guardadas = new ArrayList<>();

    @BeforeEach
    void setUp() {
        laboratorio = DatosDePrueba.empresaHabilitada(TipoEmpresa.LABORATORIO);
        distribuidora = DatosDePrueba.empresaHabilitada(TipoEmpresa.DISTRIBUIDOR);
        farmacia = DatosDePrueba.empresaHabilitada(TipoEmpresa.FARMACIA);
        lote = DatosDePrueba.loteDe(laboratorio);
        lote.liberar(null);
        lote.getMedicamento().setTemperaturaMinima(new BigDecimal("15"));
        lote.getMedicamento().setTemperaturaMaxima(new BigDecimal("30"));
        EnlaceCuit circuito = DatosDePrueba.circuitoAprobado(laboratorio, distribuidora, farmacia);
        bulto = DatosDePrueba.bulto("BUL-0001", lote, circuito);
        cajas = new ArrayList<>();
        for (int i = 1; i <= 2; i++) {
            UnidadTrazable caja = DatosDePrueba.caja(lote, "S" + i);
            caja.asignarABulto(bulto);
            cajas.add(caja);
        }
        viaje = enViaje(TramoDespacho.LAB_A_DISTRIBUIDOR, laboratorio, bulto);
        lenient().when(bultoRepository.findByCodigo("BUL-0001")).thenReturn(Optional.of(bulto));
        lenient().when(unidadTrazableRepository.findByBultoId(bulto.getId())).thenReturn(cajas);
        lenient().when(evaluadorBloqueo.bloqueosDeBultos(anyCollection())).thenReturn(Map.of());
        lenient().when(repository.save(any(Recepcion.class))).thenAnswer(inv -> {
            Recepcion r = inv.getArgument(0);
            guardadas.add(r);
            return r;
        });
        lenient().when(repository.findByDespachoId(any())).thenReturn(guardadas);
        lenient().when(telemetriaTemperaturaRepository.findByDespachoId(any())).thenReturn(List.of());
    }

    /** Crea un viaje del tramo dado con los bultos y registra su salida (bultos y cajas EN_TRANSITO). */
    private DespachoLogistico enViaje(TramoDespacho tramo, Empresa origen, Bulto... bultos) {
        DespachoLogistico nuevo = DatosDePrueba.viaje(tramo, origen);
        for (Bulto b : bultos) {
            nuevo.agregarBulto(b);
        }
        nuevo.registrarSalida();
        for (Bulto b : bultos) {
            b.salir();
        }
        cajas.forEach(UnidadTrazable::salir);
        return nuevo;
    }

    /** Construye el Service bajo prueba con los mocks. */
    private RecepcionService service() {
        return new RecepcionService(repository, bultoRepository, despachoLogisticoRepository, unidadTrazableRepository,
                telemetriaTemperaturaRepository, evaluadorBloqueo, aperturaCuarentenas, usuarioActual,
                verificadorEmpresa, verificadorUsuario, registradorEventos, registradorEventosAparte);
    }

    /** Autentica como la empresa dada (HABILITADA). */
    private UsuarioAutenticado como(RolUsuario rol, Empresa empresa) {
        UsuarioAutenticado actual = DatosDePrueba.autenticado(rol, empresa);
        when(usuarioActual.obtener()).thenReturn(actual);
        lenient().when(verificadorEmpresa.exigirHabilitada(empresa.getId())).thenReturn(empresa);
        return actual;
    }

    /** DTO de recepción. */
    private RecepcionRequestDTO dto(boolean precinto, int cantidad, String temperatura) {
        RecepcionRequestDTO dto = new RecepcionRequestDTO();
        dto.setCodigoBulto("BUL-0001");
        dto.setPrecintoIntacto(precinto);
        dto.setCantidadVerificada(cantidad);
        dto.setTemperatura(new BigDecimal(temperatura));
        dto.setObservacion("Texto libre que nunca va al evento");
        return dto;
    }

    /** Verifica que la acción falle con el código dado. */
    private void fallaCon(String codigo, Runnable accion) {
        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class, accion::run);
        assertEquals(codigo, ex.getCodigoRegla());
    }

    // ---------- Conforme ----------

    @Test
    @DisplayName("Tramo 1 conforme: bulto y cajas EN_DEPOSITO en la distribuidora; viaje FINALIZADO")
    @SuppressWarnings("unchecked")
    void tramoUnoConforme() {
        UsuarioAutenticado actual = como(RolUsuario.DISTRIBUIDOR, distribuidora);

        Recepcion recepcion = service().recibir(dto(true, 10, "20"));

        assertTrue(recepcion.getConforme());
        assertEquals(EstadoBulto.EN_DEPOSITO, bulto.getEstado());
        assertSame(distribuidora, bulto.getUbicacion());
        assertNull(bulto.getViajeActual());
        assertEquals(EstadoUnidad.EN_DEPOSITO, cajas.get(0).getEstado());
        assertTrue(cajas.get(0).estaEn(distribuidora));
        ArgumentCaptor<Map<String, Object>> datos = ArgumentCaptor.forClass(Map.class);
        verify(registradorEventos).registrar(eq(TipoEvento.BULTO_RECIBIDO), eq("Bulto"), eq(bulto.getId()),
                datos.capture(), eq(actual));
        assertFalse(datos.getValue().toString().contains("Texto libre"), "la observación no va al evento");
        assertEquals(EstadoDespacho.FINALIZADO, viaje.getEstado());
        verify(registradorEventos).registrar(eq(TipoEvento.VIAJE_FINALIZADO), eq("DespachoLogistico"),
                eq(viaje.getId()), anyMap(), eq(actual));
        verify(aperturaCuarentenas, never()).abrirPorBulto(any());
    }

    @Test
    @DisplayName("Tramo 2 conforme: bulto RECIBIDO y cajas EN_STOCK en la farmacia")
    void tramoDosConforme() {
        bulto.recibir(distribuidora);
        cajas.forEach(c -> c.recibirEnDeposito(distribuidora));
        enViaje(TramoDespacho.DISTRIBUIDOR_A_FARMACIA, distribuidora, bulto);
        como(RolUsuario.FARMACIA, farmacia);

        service().recibir(dto(true, 10, "22"));

        assertEquals(EstadoBulto.RECIBIDO, bulto.getEstado());
        assertEquals(EstadoUnidad.EN_STOCK, cajas.get(0).getEstado());
        assertTrue(cajas.get(0).estaEn(farmacia));
    }

    // ---------- Rechazo ----------

    @Test
    @DisplayName("Precinto roto, cantidad distinta y temperatura fuera de rango → RECHAZADO, cajas RECHAZADA y cuarentena BULTO")
    @SuppressWarnings("unchecked")
    void rechazoPorCadaCausa() {
        como(RolUsuario.DISTRIBUIDOR, distribuidora);

        Recepcion recepcion = service().recibir(dto(false, 9, "40"));

        assertFalse(recepcion.getConforme());
        assertEquals(List.of(MotivoRechazoRecepcion.PRECINTO_ROTO, MotivoRechazoRecepcion.CANTIDAD_DISTINTA,
                MotivoRechazoRecepcion.TEMPERATURA_FUERA_DE_RANGO), recepcion.motivos());
        assertEquals(EstadoBulto.RECHAZADO, bulto.getEstado());
        assertEquals(EstadoUnidad.RECHAZADA, cajas.get(0).getEstado());
        assertTrue(cajas.get(0).estaEn(distribuidora));
        verify(aperturaCuarentenas).abrirPorBulto(bulto);
        ArgumentCaptor<Map<String, Object>> datos = ArgumentCaptor.forClass(Map.class);
        verify(registradorEventos).registrar(eq(TipoEvento.BULTO_RECHAZADO), eq("Bulto"), eq(bulto.getId()),
                datos.capture(), any(UsuarioAutenticado.class));
        assertEquals(recepcion.motivos(), datos.getValue().get("motivos"));
    }

    @Test
    @DisplayName("Solo la temperatura de llegada fuera del rango del medicamento → rechazo")
    void rechazoPorTemperatura() {
        como(RolUsuario.DISTRIBUIDOR, distribuidora);

        Recepcion recepcion = service().recibir(dto(true, 10, "10"));

        assertEquals(List.of(MotivoRechazoRecepcion.TEMPERATURA_FUERA_DE_RANGO), recepcion.motivos());
    }

    @Test
    @DisplayName("R10: bulto bloqueado → se registra y se rechaza automáticamente, sin cuarentena nueva")
    void bultoBloqueado() {
        como(RolUsuario.DISTRIBUIDOR, distribuidora);
        when(evaluadorBloqueo.bloqueosDeBultos(anyCollection()))
                .thenReturn(Map.of(UUID.randomUUID(), new Bloqueo(CausaBloqueo.BULTO_CON_MEDIDA_VIGENTE, "ruptura de frío")));

        Recepcion recepcion = service().recibir(dto(true, 10, "20"));

        assertEquals(List.of(MotivoRechazoRecepcion.BULTO_BLOQUEADO), recepcion.motivos());
        assertEquals(EstadoBulto.RECHAZADO, bulto.getEstado());
        verify(aperturaCuarentenas, never()).abrirPorBulto(any());
        assertEquals(EstadoDespacho.FINALIZADO, viaje.getEstado(), "el viaje igual puede terminar");
    }

    // ---------- Quién y cuándo ----------

    @Test
    @DisplayName("Una empresa que no es la destino actual → 404")
    void empresaNoDestino() {
        como(RolUsuario.FARMACIA, farmacia);

        assertThrows(ResourceNotFoundException.class, () -> service().recibir(dto(true, 10, "20")));
    }

    @Test
    @DisplayName("Bulto que todavía no salió (viaje PROGRAMADO) → TRANSICION_INVALIDA")
    void viajeNoEnTransito() {
        Bulto armado = DatosDePrueba.bulto("BUL-0009", lote, bulto.getDestino());
        DatosDePrueba.viaje(TramoDespacho.LAB_A_DISTRIBUIDOR, laboratorio).agregarBulto(armado);
        when(bultoRepository.findByCodigo("BUL-0009")).thenReturn(Optional.of(armado));
        como(RolUsuario.DISTRIBUIDOR, distribuidora);
        RecepcionRequestDTO dto = dto(true, 10, "20");
        dto.setCodigoBulto("BUL-0009");

        fallaCon("TRANSICION_INVALIDA", () -> service().recibir(dto));
    }

    @Test
    @DisplayName("Código inexistente → 404 y BULTO_INEXISTENTE en transacción aparte")
    void bultoInexistente() {
        como(RolUsuario.DISTRIBUIDOR, distribuidora);
        when(bultoRepository.findByCodigo("BUL-9999")).thenReturn(Optional.empty());
        RecepcionRequestDTO dto = dto(true, 10, "20");
        dto.setCodigoBulto("BUL-9999");

        assertThrows(ResourceNotFoundException.class, () -> service().recibir(dto));
        verify(registradorEventosAparte).registrar(eq(TipoEvento.BULTO_INEXISTENTE), eq("Empresa"),
                eq(distribuidora.getId()), anyMap(), any(), eq(distribuidora.getId()));
    }

    @Test
    @DisplayName("Bulto que la empresa ya recibió → 409 y BULTO_DUPLICADO en transacción aparte")
    void bultoDuplicado() {
        como(RolUsuario.DISTRIBUIDOR, distribuidora);
        when(repository.existsByBultoIdAndReceptoraId(bulto.getId(), distribuidora.getId())).thenReturn(true);

        fallaCon("TRANSICION_INVALIDA", () -> service().recibir(dto(true, 10, "20")));
        verify(registradorEventosAparte).registrar(eq(TipoEvento.BULTO_DUPLICADO), eq("Bulto"), eq(bulto.getId()),
                anyMap(), any(), eq(distribuidora.getId()));
        verify(registradorEventos, never()).registrar(any(), any(), any(), anyMap(), any(UsuarioAutenticado.class));
    }

    // ---------- Finalización del viaje ----------

    @Test
    @DisplayName("Con dos bultos, recibir uno no finaliza el viaje")
    void viajeConBultosPendientes() {
        Bulto principal = DatosDePrueba.bulto("BUL-0003", lote, bulto.getDestino());
        DespachoLogistico doble = DatosDePrueba.viaje(TramoDespacho.LAB_A_DISTRIBUIDOR, laboratorio);
        Bulto segundo = DatosDePrueba.bulto("BUL-0004", lote, bulto.getDestino());
        doble.agregarBulto(principal);
        doble.agregarBulto(segundo);
        doble.registrarSalida();
        principal.salir();
        segundo.salir();
        when(bultoRepository.findByCodigo("BUL-0003")).thenReturn(Optional.of(principal));
        when(unidadTrazableRepository.findByBultoId(principal.getId())).thenReturn(List.of());
        como(RolUsuario.DISTRIBUIDOR, distribuidora);
        RecepcionRequestDTO dto = dto(true, 10, "20");
        dto.setCodigoBulto("BUL-0003");

        service().recibir(dto);

        assertEquals(EstadoDespacho.EN_TRANSITO, doble.getEstado());
        verify(registradorEventos, never()).registrar(eq(TipoEvento.VIAJE_FINALIZADO), any(), any(), anyMap(),
                any(UsuarioAutenticado.class));
    }

    @Test
    @DisplayName("VIAJE_FINALIZADO lleva el resumen de temperatura (lecturas, fuera de rango, mínima y máxima)")
    @SuppressWarnings("unchecked")
    void resumenDeTemperatura() {
        como(RolUsuario.DISTRIBUIDOR, distribuidora);
        when(telemetriaTemperaturaRepository.findByDespachoId(viaje.getId())).thenReturn(List.of(
                new TelemetriaTemperatura("S", new BigDecimal("20.5"), false, Instant.now(), viaje),
                new TelemetriaTemperatura("S", new BigDecimal("35"), true, Instant.now(), viaje)));

        service().recibir(dto(true, 10, "20"));

        ArgumentCaptor<Map<String, Object>> datos = ArgumentCaptor.forClass(Map.class);
        verify(registradorEventos).registrar(eq(TipoEvento.VIAJE_FINALIZADO), eq("DespachoLogistico"),
                eq(viaje.getId()), datos.capture(), any(UsuarioAutenticado.class));
        Map<String, Object> resumen = (Map<String, Object>) datos.getValue().get("temperatura");
        assertEquals(2, resumen.get("lecturas"));
        assertEquals(1, resumen.get("fueraDeRango"));
        assertEquals(new BigDecimal("20.5"), resumen.get("minima"));
        assertEquals(new BigDecimal("35"), resumen.get("maxima"));
        assertEquals(1, datos.getValue().get("recibidos"));
    }
}
