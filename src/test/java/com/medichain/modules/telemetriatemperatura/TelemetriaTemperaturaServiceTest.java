package com.medichain.modules.telemetriatemperatura;

import com.medichain.exceptions.ReglaNegocioException;
import com.medichain.exceptions.ResourceNotFoundException;
import com.medichain.modules.auth.UsuarioAutenticado;
import com.medichain.modules.bulto.Bulto;
import com.medichain.modules.cuarentena.AperturaCuarentenas;
import com.medichain.modules.cuarentena.EvaluadorBloqueo;
import com.medichain.modules.cuarentena.MotivoBloqueo;
import com.medichain.modules.despachologistico.DespachoLogistico;
import com.medichain.modules.despachologistico.DespachoLogisticoRepository;
import com.medichain.modules.despachologistico.TramoDespacho;
import com.medichain.modules.empresa.Empresa;
import com.medichain.modules.empresa.TipoEmpresa;
import com.medichain.modules.enlacecuit.EnlaceCuit;
import com.medichain.modules.lote.Lote;
import com.medichain.modules.medicamento.Medicamento;
import com.medichain.modules.trazabilidad.RegistradorEventos;
import com.medichain.modules.trazabilidad.TipoEvento;
import com.medichain.modules.usuario.RolUsuario;
import com.medichain.testutil.DatosDePrueba;
import com.medichain.utils.seguridad.UsuarioActual;
import com.medichain.utils.seguridad.VerificadorEmpresa;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * Test unitario TelemetriaTemperaturaServiceTest en MediChain.
 * R9 por bulto afectado: cada bulto contra el rango de SU medicamento,
 * cuarentena solo de los afectados, sin duplicar, solo con el viaje
 * EN_TRANSITO y solo la empresa origen.
 */
@ExtendWith(MockitoExtension.class)
class TelemetriaTemperaturaServiceTest {

    @Mock
    private TelemetriaTemperaturaRepository repository;

    @Mock
    private DespachoLogisticoRepository despachoLogisticoRepository;

    @Mock
    private UsuarioActual usuarioActual;

    @Mock
    private VerificadorEmpresa verificadorEmpresa;

    @Mock
    private RegistradorEventos registradorEventos;

    @Mock
    private EvaluadorBloqueo evaluadorBloqueo;

    @Mock
    private AperturaCuarentenas aperturaCuarentenas;

    private Empresa laboratorio;
    private DespachoLogistico viaje;
    private Bulto bultoComun;
    private Bulto bultoVacuna;

    /** Lote liberado de un medicamento del laboratorio con el rango dado. */
    private Lote loteCon(String gtin, String minimo, String maximo, boolean biologico) {
        Medicamento medicamento = new Medicamento(gtin, "Med " + gtin, "PA", "1", "F", "P");
        medicamento.setId(UUID.randomUUID());
        medicamento.setLaboratorio(laboratorio);
        medicamento.setTemperaturaMinima(new BigDecimal(minimo));
        medicamento.setTemperaturaMaxima(new BigDecimal(maximo));
        medicamento.setBiologico(biologico);
        Lote lote = new Lote("L-" + gtin.substring(10), LocalDate.now().minusMonths(1), LocalDate.now().plusYears(1),
                10, medicamento);
        lote.setId(UUID.randomUUID());
        lote.liberar(null);
        return lote;
    }

    @BeforeEach
    void setUp() {
        laboratorio = DatosDePrueba.empresaHabilitada(TipoEmpresa.LABORATORIO);
        EnlaceCuit circuito = DatosDePrueba.circuitoAprobado(laboratorio,
                DatosDePrueba.empresaHabilitada(TipoEmpresa.DISTRIBUIDOR), DatosDePrueba.empresaHabilitada(TipoEmpresa.FARMACIA));
        bultoComun = DatosDePrueba.bulto("BUL-0001", loteCon("07799000001010", "15", "30", false), circuito);
        bultoVacuna = DatosDePrueba.bulto("BUL-0002", loteCon("07799000002024", "2", "8", true), circuito);
        viaje = DatosDePrueba.viaje(TramoDespacho.LAB_A_DISTRIBUIDOR, laboratorio);
        viaje.agregarBulto(bultoComun);
        viaje.agregarBulto(bultoVacuna);
        lenient().when(despachoLogisticoRepository.findById(viaje.getId())).thenReturn(Optional.of(viaje));
        lenient().when(repository.save(any(TelemetriaTemperatura.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(evaluadorBloqueo.bultosConMedidaVigente(anyCollection())).thenReturn(Set.of());
    }

    /** Construye el Service bajo prueba con los mocks. */
    private TelemetriaTemperaturaService service() {
        return new TelemetriaTemperaturaService(repository, despachoLogisticoRepository, usuarioActual,
                verificadorEmpresa, registradorEventos, evaluadorBloqueo, aperturaCuarentenas);
    }

    /** Autentica como la empresa origen y registra la salida del viaje. */
    private UsuarioAutenticado origenEnTransito() {
        UsuarioAutenticado actual = DatosDePrueba.autenticado(RolUsuario.LABORATORIO, laboratorio);
        when(usuarioActual.obtener()).thenReturn(actual);
        viaje.registrarSalida();
        bultoComun.salir();
        bultoVacuna.salir();
        return actual;
    }

    /** DTO de lectura (el cliente nunca informa fueraDeRango). */
    private TelemetriaTemperaturaRequestDTO dto(DespachoLogistico despacho, String temperatura) {
        TelemetriaTemperaturaRequestDTO dto = new TelemetriaTemperaturaRequestDTO();
        dto.setSensorId("S-1");
        dto.setTemperatura(new BigDecimal(temperatura));
        dto.setFechaHora(LocalDateTime.now());
        dto.setDespachoId(despacho.getId());
        return dto;
    }

    /** Registra una lectura con la temperatura dada. */
    private TelemetriaTemperatura lectura(String temperatura) {
        return service().create(dto(viaje, temperatura));
    }

    @Test
    @DisplayName("fueraDeRango lo calcula el servidor (a 20 °C la vacuna 2–8 queda fuera)")
    void fueraDeRangoLoCalculaElServidor() {
        origenEnTransito();

        TelemetriaTemperatura guardada = lectura("20");

        assertTrue(guardada.getFueraDeRango());
    }

    @Test
    @DisplayName("20 °C: solo la vacuna (2–8) queda fuera → RUPTURA_FRIO y cuarentena SOLO de ese bulto")
    @SuppressWarnings("unchecked")
    void soloBultosAfectados() {
        UsuarioAutenticado actual = origenEnTransito();

        lectura("20");

        verify(registradorEventos).registrar(eq(TipoEvento.RUPTURA_FRIO), eq("DespachoLogistico"), eq(viaje.getId()),
                anyMap(), eq(actual));
        ArgumentCaptor<Collection<Bulto>> bultos = ArgumentCaptor.forClass(Collection.class);
        verify(aperturaCuarentenas).abrirPorDespacho(eq(MotivoBloqueo.RUPTURA_FRIO), eq(viaje), bultos.capture());
        assertEquals(List.of(bultoVacuna), List.copyOf(bultos.getValue()));
    }

    @Test
    @DisplayName("10 °C: Cuyafen (bajo su mínimo) y la vacuna (sobre su máximo) quedan fuera → ambos en cuarentena")
    @SuppressWarnings("unchecked")
    void ambosAfectados() {
        origenEnTransito();

        lectura("10");

        ArgumentCaptor<Collection<Bulto>> bultos = ArgumentCaptor.forClass(Collection.class);
        verify(aperturaCuarentenas).abrirPorDespacho(eq(MotivoBloqueo.RUPTURA_FRIO), eq(viaje), bultos.capture());
        assertEquals(2, bultos.getValue().size());
    }

    @Test
    @DisplayName("Sin bultos afectados (viaje solo con Cuyafen a 20 °C) → solo telemetría, sin eventos")
    void enRangoSinEventos() {
        DespachoLogistico soloComun = DatosDePrueba.viaje(TramoDespacho.LAB_A_DISTRIBUIDOR, laboratorio);
        Bulto bulto = DatosDePrueba.bulto("BUL-0003", bultoComun.getLote(), bultoComun.getDestino());
        soloComun.agregarBulto(bulto);
        soloComun.registrarSalida();
        bulto.salir();
        when(despachoLogisticoRepository.findById(soloComun.getId())).thenReturn(Optional.of(soloComun));
        when(usuarioActual.obtener()).thenReturn(DatosDePrueba.autenticado(RolUsuario.LABORATORIO, laboratorio));
        TelemetriaTemperatura guardada = service().create(dto(soloComun, "20"));

        assertFalse(guardada.getFueraDeRango());
        verify(registradorEventos, never()).registrar(any(), any(), any(), anyMap(), any(UsuarioAutenticado.class));
        verify(aperturaCuarentenas, never()).abrirPorDespacho(any(), any(), any());
    }

    @Test
    @DisplayName("Lectura repetida fuera de rango con los bultos ya en cuarentena → sin evento ni cuarentena nueva")
    void sinDuplicarCuarentena() {
        origenEnTransito();
        when(evaluadorBloqueo.bultosConMedidaVigente(anyCollection()))
                .thenReturn(Set.of(bultoComun.getId(), bultoVacuna.getId()));

        TelemetriaTemperatura guardada = lectura("35");

        assertTrue(guardada.getFueraDeRango());
        verify(registradorEventos, never()).registrar(any(), any(), any(), anyMap(), any(UsuarioAutenticado.class));
        verify(aperturaCuarentenas, never()).abrirPorDespacho(any(), any(), any());
    }

    @Test
    @DisplayName("Viaje PROGRAMADO (no EN_TRANSITO) → TRANSICION_INVALIDA")
    void viajeNoEnTransito() {
        when(usuarioActual.obtener()).thenReturn(DatosDePrueba.autenticado(RolUsuario.LABORATORIO, laboratorio));

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class, () -> lectura("20"));
        assertEquals("TRANSICION_INVALIDA", ex.getCodigoRegla());
    }

    @Test
    @DisplayName("Empresa que no es la origen del viaje → 404")
    void empresaNoOrigen() {
        when(usuarioActual.obtener()).thenReturn(DatosDePrueba.autenticado(RolUsuario.DISTRIBUIDOR,
                DatosDePrueba.empresaHabilitada(TipoEmpresa.DISTRIBUIDOR)));

        assertThrows(ResourceNotFoundException.class, () -> lectura("20"));
    }

    @Test
    @DisplayName("Ve lo suyo: el laboratorio ve una lectura de su viaje")
    void veLecturaDeSuViaje() {
        TelemetriaTemperatura lectura = new TelemetriaTemperatura("S-1", new BigDecimal("20"), false,
                LocalDateTime.now(), viaje);
        lectura.setId(UUID.randomUUID());
        when(usuarioActual.obtener()).thenReturn(DatosDePrueba.autenticado(RolUsuario.LABORATORIO, laboratorio));
        when(repository.findById(lectura.getId())).thenReturn(Optional.of(lectura));

        assertSame(lectura, service().getById(lectura.getId()));
    }
}
