package com.medichain.modules.registroblockchain;

import com.medichain.exceptions.ReglaNegocioException;
import com.medichain.modules.trazabilidad.CadenaEstado;
import com.medichain.modules.trazabilidad.CadenaEstadoRepository;
import com.medichain.modules.trazabilidad.EventoTrazabilidad;
import com.medichain.modules.trazabilidad.EventoTrazabilidadRepository;
import com.medichain.testutil.DatosDePrueba;
import com.medichain.testutil.RelojMovible;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Test unitario ProcesoAnclajeTest en MediChain (R15).
 * Blockchain simulada (ClienteBlockchain de Mockito) y la tabla
 * registros_blockchain simulada en memoria; PasosAnclaje real con un reloj
 * que avanza a pedido. Cubre: sin eventos nuevos, PENDIENTE → ENVIADO
 * (guardado antes de transmitir), un solo anclaje en curso, reintentos con
 * espera hasta FALLIDO, confirmaciones, revert, rechazo al estimar, cobertura
 * después de un FALLIDO, reemplazo de una transacción trabada, recuperación
 * tras un reinicio, tope de comisión y el control que impide anclar sobre una
 * cadena alterada (R15).
 */
@ExtendWith(MockitoExtension.class)
class ProcesoAnclajeTest {

    private static final String CONTRATO = "0x" + "1".repeat(40);
    private static final String HASH_EVENTO_28 = "aa".repeat(32);
    private static final BigInteger GWEI = BigInteger.valueOf(1_000_000_000L);

    @Mock
    private ClienteBlockchain cliente;

    @Mock
    private RegistroBlockchainRepository repository;

    @Mock
    private CadenaEstadoRepository cadenaEstadoRepository;

    @Mock
    private EventoTrazabilidadRepository eventoRepository;

    private AnclajeProperties propiedades;
    private RelojMovible reloj;
    private Map<UUID, RegistroBlockchain> tabla;
    private long ultimoNumeroLocal;
    private int firmadas;

    @BeforeEach
    void setUp() {
        propiedades = new AnclajeProperties();
        propiedades.setHabilitado(true);
        propiedades.setContrato(CONTRATO);
        reloj = new RelojMovible(Instant.parse("2026-10-05T12:00:00Z"));
        tabla = new LinkedHashMap<>();
        ultimoNumeroLocal = 28;
        simularTabla();
        lenient().when(cadenaEstadoRepository.findFirstByNombre(CadenaEstado.PRINCIPAL)).thenAnswer(inv ->
                Optional.of(new CadenaEstado(CadenaEstado.PRINCIPAL, ultimoNumeroLocal, HASH_EVENTO_28)));
        lenient().when(cliente.ultimoNumeroAnclado()).thenReturn(0L);
        lenient().when(cliente.nonce()).thenReturn(BigInteger.valueOf(7));
        lenient().when(cliente.comisiones()).thenReturn(new ComisionesRed(GWEI, GWEI.divide(BigInteger.TEN)));
        lenient().when(cliente.estimarGasAnclaje(anyString(), anyLong())).thenReturn(BigInteger.valueOf(60_000));
        lenient().when(cliente.firmarAnclaje(anyString(), anyLong(), any(), any(), any(), any())).thenAnswer(inv -> {
            firmadas++;
            return new TransaccionFirmada("0x" + String.format("%064x", firmadas), "0x02f8", inv.getArgument(2),
                    inv.getArgument(3), inv.getArgument(4), inv.getArgument(5));
        });
        lenient().when(cliente.recibo(anyString())).thenReturn(Optional.empty());
        lenient().when(cliente.transaccionConocida(anyString())).thenReturn(false);
        lenient().when(cliente.saldoWei()).thenReturn(new BigInteger("1000000000000000000"));
    }

    /** La tabla registros_blockchain en memoria, con las consultas que usa PasosAnclaje. */
    private void simularTabla() {
        lenient().when(repository.save(any(RegistroBlockchain.class))).thenAnswer(inv -> {
            RegistroBlockchain registro = inv.getArgument(0);
            if (registro.getId() == null) {
                registro.setId(UUID.randomUUID());
            }
            tabla.put(registro.getId(), registro);
            return registro;
        });
        lenient().when(repository.findById(any())).thenAnswer(inv -> Optional.ofNullable(tabla.get(inv.getArgument(0))));
        lenient().when(repository.findFirstByEstadoInOrderByFechaCreacionDesc(any())).thenAnswer(inv -> {
            Collection<EstadoAnclaje> estados = inv.getArgument(0);
            return ultimo(r -> estados.contains(r.getEstado()));
        });
        lenient().when(repository.maxHastaNumero(any())).thenAnswer(inv -> {
            Collection<EstadoAnclaje> estados = inv.getArgument(0);
            return tabla.values().stream().filter(r -> estados.contains(r.getEstado()))
                    .map(RegistroBlockchain::getHastaNumero).max(Long::compare).orElse(null);
        });
        lenient().when(repository.findFirstByEstadoOrderByFechaCreacionDesc(any())).thenAnswer(inv ->
                ultimo(r -> r.getEstado() == inv.getArgument(0)));
    }

    /** El último registro de la tabla que cumple el filtro. */
    private Optional<RegistroBlockchain> ultimo(Predicate<RegistroBlockchain> filtro) {
        RegistroBlockchain encontrado = null;
        for (RegistroBlockchain registro : tabla.values()) {
            if (filtro.test(registro)) {
                encontrado = registro;
            }
        }
        return Optional.ofNullable(encontrado);
    }

    /** Agrega a la tabla un anclaje ya existente. */
    private void enTabla(RegistroBlockchain registro) {
        if (registro.getId() == null) {
            registro.setId(UUID.randomUUID());
        }
        tabla.put(registro.getId(), registro);
    }

    /** Proceso bajo prueba (el cerrojo es por instancia: reusar la misma en un test). */
    private ProcesoAnclaje proceso() {
        return new ProcesoAnclaje(cliente,
                new PasosAnclaje(repository, cadenaEstadoRepository, eventoRepository, propiedades, reloj), propiedades);
    }

    /** El contrato ya ancló hasta el número dado con ese hash. */
    private void contratoAncladoHasta(long numero, String hash) {
        when(cliente.ultimoNumeroAnclado()).thenReturn(numero);
        lenient().when(cliente.hashAnclado(numero)).thenReturn(hash);
    }

    /** La base tiene el evento número dado con ese hash. */
    private void eventoLocal(long numero, String hash) {
        EventoTrazabilidad evento = mock(EventoTrazabilidad.class);
        lenient().when(evento.getHash()).thenReturn(hash);
        when(eventoRepository.findByNumero(numero)).thenReturn(Optional.of(evento));
    }

    /** El único anclaje de la tabla. */
    private RegistroBlockchain unico() {
        assertEquals(1, tabla.size());
        return tabla.values().iterator().next();
    }

    // ---------- Apertura y envío ----------

    @Test
    @DisplayName("Anclar ya: PENDIENTE 1..28 con el hash del último evento; simula, nonce LATEST, gas +30 % (piso 100.000), 2×base+propina; se guarda ANTES de transmitir → ENVIADO")
    void anclarYaEnvia() {
        doAnswer(inv -> {
            assertNotNull(unico().getTransactionHash(), "la transacción ya está guardada al transmitir");
            assertEquals(EstadoAnclaje.PENDIENTE, unico().getEstado());
            return null;
        }).when(cliente).transmitir(any());

        RegistroBlockchain anclaje = proceso().anclarAhora();

        assertEquals(EstadoAnclaje.ENVIADO, anclaje.getEstado());
        assertEquals(1L, anclaje.getDesdeNumero());
        assertEquals(28L, anclaje.getHastaNumero());
        assertEquals(HASH_EVENTO_28, anclaje.getHashAnclado());
        assertEquals(CONTRATO, anclaje.getDireccionContrato());
        assertEquals(7L, anclaje.getNonce());
        assertEquals(1, anclaje.getIntentos());
        verify(cliente).simularAnclaje(HASH_EVENTO_28, 28L, BigInteger.valueOf(500_000));
        verify(cliente).firmarAnclaje(HASH_EVENTO_28, 28L, BigInteger.valueOf(7), BigInteger.valueOf(100_000),
                BigInteger.valueOf(2_100_000_000L), BigInteger.valueOf(100_000_000L));
    }

    @Test
    @DisplayName("Sin eventos nuevos desde el último anclaje → 409 SIN_EVENTOS_NUEVOS; la tarea no abre nada")
    void sinEventosNuevos() {
        contratoAncladoHasta(28, HASH_EVENTO_28);
        eventoLocal(28, HASH_EVENTO_28);
        ProcesoAnclaje proceso = proceso();

        assertEquals("SIN_EVENTOS_NUEVOS",
                assertThrows(ReglaNegocioException.class, proceso::anclarAhora).getCodigoRegla());
        proceso.cicloAnclaje();

        assertTrue(tabla.isEmpty());
        verify(cliente, never()).firmarAnclaje(anyString(), anyLong(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Con un anclaje en curso no se abre otro: anclar ya → 409 ANCLAJE_EN_CURSO; la tarea espera")
    void unSoloAnclajeEnCurso() {
        ProcesoAnclaje proceso = proceso();
        proceso.anclarAhora();
        ultimoNumeroLocal = 30;

        assertEquals("ANCLAJE_EN_CURSO",
                assertThrows(ReglaNegocioException.class, proceso::anclarAhora).getCodigoRegla());
        proceso.cicloAnclaje();

        assertEquals(1, tabla.size());
    }

    @Test
    @DisplayName("Anclaje deshabilitado → 409 ANCLAJE_DESHABILITADO sin tocar la red")
    void deshabilitado() {
        propiedades.setHabilitado(false);

        assertEquals("ANCLAJE_DESHABILITADO",
                assertThrows(ReglaNegocioException.class, () -> proceso().anclarAhora()).getCodigoRegla());
        verifyNoInteractions(cliente);
    }

    // ---------- Control R15: no anclar sobre una cadena alterada ----------

    @Test
    @DisplayName("El último anclaje del contrato no coincide con el evento local → R15 y no se ancla (ni por la tarea)")
    void noAnclaSobreUnaCadenaAlterada() {
        contratoAncladoHasta(20, "bb".repeat(32));
        eventoLocal(20, "cc".repeat(32));
        ProcesoAnclaje proceso = proceso();

        ReglaNegocioException e = assertThrows(ReglaNegocioException.class, proceso::anclarAhora);
        proceso.cicloAnclaje();

        assertEquals("R15", e.getCodigoRegla());
        assertTrue(e.getMessage().contains("no coincide"), e.getMessage());
        assertTrue(tabla.isEmpty());
        verify(cliente, never()).firmarAnclaje(anyString(), anyLong(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("El contrato ancló un evento que la base no tiene (base reseteada o eventos borrados) → R15")
    void contratoAdelantado() {
        contratoAncladoHasta(84, "bb".repeat(32));
        when(eventoRepository.findByNumero(84L)).thenReturn(Optional.empty());

        ReglaNegocioException e = assertThrows(ReglaNegocioException.class, () -> proceso().anclarAhora());

        assertEquals("R15", e.getCodigoRegla());
        assertTrue(e.getMessage().contains("la base no tiene ese evento"), e.getMessage());
    }

    // ---------- Reintentos, rechazos y confirmaciones ----------

    @Test
    @DisplayName("Error de red: suma intentos y espera (30 s, 1, 2, 4 min); no reintenta antes; al 5.º → FALLIDO")
    void reintentosHastaFallido() {
        doThrow(new ErrorBlockchainException("No se pudo transmitir: timeout")).when(cliente).transmitir(any());
        ProcesoAnclaje proceso = proceso();

        RegistroBlockchain anclaje = proceso.anclarAhora();
        assertEquals(EstadoAnclaje.PENDIENTE, anclaje.getEstado());
        assertEquals(1, anclaje.getIntentos());
        assertEquals("No se pudo transmitir: timeout", anclaje.getUltimoError());
        assertEquals(Instant.parse("2026-10-05T12:00:30Z"), anclaje.getProximoIntento());

        proceso.cicloSeguimiento();
        verify(cliente, times(1)).firmarAnclaje(anyString(), anyLong(), any(), any(), any(), any());

        for (int intento = 2; intento <= 5; intento++) {
            reloj.avanzar(Duration.ofMinutes(5));
            proceso.cicloSeguimiento();
        }

        assertEquals(EstadoAnclaje.FALLIDO, unico().getEstado());
        assertEquals(5, unico().getIntentos());
        verify(cliente, times(5)).firmarAnclaje(anyString(), anyLong(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Recibo con 2 confirmaciones → sigue ENVIADO; con 3 → CONFIRMADO con bloque y gas")
    void confirmaciones() {
        ProcesoAnclaje proceso = proceso();
        RegistroBlockchain anclaje = proceso.anclarAhora();
        when(cliente.recibo(anclaje.getTransactionHash()))
                .thenReturn(Optional.of(new ReciboTransaccion(100L, true, 61_000L, 1_100_000_000L)));
        when(cliente.ultimoBloque()).thenReturn(102L, 103L);

        proceso.cicloSeguimiento();
        assertEquals(EstadoAnclaje.ENVIADO, unico().getEstado());
        assertEquals(2, unico().getConfirmaciones());

        proceso.cicloSeguimiento();
        assertEquals(EstadoAnclaje.CONFIRMADO, unico().getEstado());
        assertEquals(100L, unico().getBloque());
        assertEquals(61_000L, unico().getGasUsado());
        assertNotNull(unico().getFechaConfirmacion());
    }

    @Test
    @DisplayName("La transacción se incluyó pero el contrato la rechazó (revert, status 0) → FALLIDO")
    void revert() {
        ProcesoAnclaje proceso = proceso();
        RegistroBlockchain anclaje = proceso.anclarAhora();
        when(cliente.recibo(anclaje.getTransactionHash()))
                .thenReturn(Optional.of(new ReciboTransaccion(100L, false, 25_000L, 1_100_000_000L)));

        proceso.cicloSeguimiento();

        assertEquals(EstadoAnclaje.FALLIDO, unico().getEstado());
        assertTrue(unico().getUltimoError().contains("revert"));
    }

    @Test
    @DisplayName("El contrato rechazaría el anclaje al estimar el gas → FALLIDO sin transmitir (sin gastar)")
    void rechazoAlEstimar() {
        when(cliente.estimarGasAnclaje(anyString(), anyLong())).thenThrow(new ErrorBlockchainException(
                "El contrato rechazaría el anclaje: NoEsElDuenio", CausaFallo.REVERT));

        RegistroBlockchain anclaje = proceso().anclarAhora();

        assertEquals(EstadoAnclaje.FALLIDO, anclaje.getEstado());
        verify(cliente, never()).transmitir(any());
    }

    @Test
    @DisplayName("Después de un FALLIDO no hay huecos: el nuevo anclaje cubre desde el último exitoso + 1")
    void cubreDesdeElUltimoExitoso() {
        enTabla(DatosDePrueba.anclajeConfirmado(1, 20, "dd".repeat(32), CONTRATO));
        RegistroBlockchain fallido = new RegistroBlockchain(21L, 30L, "ee".repeat(32), "sepolia", CONTRATO);
        fallido.iniciarIntento();
        fallido.marcarFallido("timeout", CausaFallo.ERROR_DE_RED);
        enTabla(fallido);
        contratoAncladoHasta(20, "dd".repeat(32));
        eventoLocal(20, "dd".repeat(32));
        ultimoNumeroLocal = 35;

        RegistroBlockchain anclaje = proceso().anclarAhora();

        assertEquals(21L, anclaje.getDesdeNumero());
        assertEquals(35L, anclaje.getHastaNumero());
    }

    @Test
    @DisplayName("Tope de comisión: si la red cobra más de 20 gwei no se firma; el intento queda con espera")
    void topeDeComision() {
        when(cliente.comisiones()).thenReturn(new ComisionesRed(GWEI.multiply(BigInteger.valueOf(25)), GWEI));

        RegistroBlockchain anclaje = proceso().anclarAhora();

        assertEquals(EstadoAnclaje.PENDIENTE, anclaje.getEstado());
        assertTrue(anclaje.getUltimoError().contains("supera el tope de 20 gwei"), anclaje.getUltimoError());
        verify(cliente, never()).firmarAnclaje(anyString(), anyLong(), any(), any(), any(), any());
    }

    // ---------- Nonce: reemplazo y recuperación ----------

    @Test
    @DisplayName("Transacción sin incluir después de 3 min → reemplazo con el MISMO nonce y +25 % de comisión")
    void reemplazoDeTransaccionTrabada() {
        ProcesoAnclaje proceso = proceso();
        RegistroBlockchain anclaje = proceso.anclarAhora();
        String primera = anclaje.getTransactionHash();
        reloj.avanzar(Duration.ofMinutes(2));
        proceso.cicloSeguimiento();
        verify(cliente, times(1)).firmarAnclaje(anyString(), anyLong(), any(), any(), any(), any());

        reloj.avanzar(Duration.ofMinutes(2));
        proceso.cicloSeguimiento();

        verify(cliente).firmarAnclaje(eq(HASH_EVENTO_28), eq(28L), eq(BigInteger.valueOf(7)), eq(BigInteger.valueOf(100_000)),
                eq(BigInteger.valueOf(2_625_000_000L)), eq(BigInteger.valueOf(125_000_000L)));
        assertEquals(EstadoAnclaje.ENVIADO, unico().getEstado());
        assertEquals(2, unico().getIntentos());
        assertTrue(!primera.equals(unico().getTransactionHash()));
    }

    @Test
    @DisplayName("Reinicio a mitad de camino: la transacción guardada ya está en la red → ENVIADO sin volver a firmar")
    void recuperaUnaTransaccionYaTransmitida() {
        doThrow(new ErrorBlockchainException("No se pudo transmitir: timeout")).when(cliente).transmitir(any());
        ProcesoAnclaje proceso = proceso();
        RegistroBlockchain anclaje = proceso.anclarAhora();
        when(cliente.transaccionConocida(anclaje.getTransactionHash())).thenReturn(true);
        reloj.avanzar(Duration.ofSeconds(31));

        proceso.cicloSeguimiento();

        assertEquals(EstadoAnclaje.ENVIADO, unico().getEstado());
        assertEquals(anclaje.getTransactionHash(), unico().getTransactionHash());
        verify(cliente, times(1)).firmarAnclaje(anyString(), anyLong(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Un FALLIDO dejó su transacción trabada con el mismo nonce → la nueva la supera en +25 % para reemplazarla")
    void superaLaTransaccionTrabadaDeUnFallido() {
        RegistroBlockchain fallido = new RegistroBlockchain(1L, 20L, "ee".repeat(32), "sepolia", CONTRATO);
        fallido.iniciarIntento();
        fallido.registrarTransaccion(new TransaccionFirmada("0x" + "9".repeat(64), "0x02", BigInteger.valueOf(7),
                BigInteger.valueOf(72_000), BigInteger.valueOf(2_100_000_000L), BigInteger.valueOf(100_000_000L)));
        fallido.marcarFallido("sin incluir", CausaFallo.SIN_INCLUIR);
        enTabla(fallido);
        when(cliente.transaccionConocida("0x" + "9".repeat(64))).thenReturn(true);

        proceso().anclarAhora();

        verify(cliente).firmarAnclaje(eq(HASH_EVENTO_28), eq(28L), eq(BigInteger.valueOf(7)), any(),
                eq(BigInteger.valueOf(2_625_000_000L)), eq(BigInteger.valueOf(125_000_000L)));
    }

    // ---------- Gas: estimación + 30 % dentro de [gas-minimo; gas-maximo] (corrección del 06/10/2026) ----------

    @Test
    @DisplayName("BUG del 06/10/2026: estimación 353.084 con techo 300.000 → NO envía (antes recortaba a 300.000 y fallaba sin gas)")
    void bugOutOfGasNoEnvia() {
        propiedades.setGasMaximo(300_000);
        when(cliente.estimarGasAnclaje(anyString(), anyLong())).thenReturn(BigInteger.valueOf(353_084));

        RegistroBlockchain anclaje = proceso().anclarAhora();

        assertEquals(EstadoAnclaje.FALLIDO, anclaje.getEstado());
        assertEquals(CausaFallo.GAS_SOBRE_EL_MAXIMO, anclaje.getCausaFallo());
        assertTrue(anclaje.getUltimoError().contains("459010") && anclaje.getUltimoError().contains("300000"),
                anclaje.getUltimoError());
        verify(cliente, never()).firmarAnclaje(anyString(), anyLong(), any(), any(), any(), any());
        verify(cliente, never()).transmitir(any());
    }

    @Test
    @DisplayName("Primer anclaje post-Glamsterdam (estimación 353.084) con el techo por defecto 500.000 → límite 459.010")
    void limiteConMargenDelTreintaPorCiento() {
        when(cliente.estimarGasAnclaje(anyString(), anyLong())).thenReturn(BigInteger.valueOf(353_084));

        RegistroBlockchain anclaje = proceso().anclarAhora();

        assertEquals(EstadoAnclaje.ENVIADO, anclaje.getEstado());
        verify(cliente).firmarAnclaje(eq(HASH_EVENTO_28), eq(28L), eq(BigInteger.valueOf(7)),
                eq(BigInteger.valueOf(459_010)), any(), any());
    }

    @Test
    @DisplayName("La simulación revierte → FALLIDO REVERT con el error decodificado, sin firmar ni gastar")
    void simulacionQueRevierte() {
        doThrow(new ErrorBlockchainException("El contrato rechazaría el anclaje: NumeroNoCreciente(recibido 28, "
                + "último anclado 84)", CausaFallo.REVERT)).when(cliente).simularAnclaje(anyString(), anyLong(), any());

        RegistroBlockchain anclaje = proceso().anclarAhora();

        assertEquals(EstadoAnclaje.FALLIDO, anclaje.getEstado());
        assertEquals(CausaFallo.REVERT, anclaje.getCausaFallo());
        assertTrue(anclaje.getUltimoError().contains("NumeroNoCreciente(recibido 28"));
        verify(cliente, never()).firmarAnclaje(anyString(), anyLong(), any(), any(), any(), any());
    }

    // ---------- Freno de la tarea automática tras un FALLIDO determinístico ----------

    @Test
    @DisplayName("Tras un FALLIDO por revert la tarea no reintenta sola; el manual sí envía y, confirmado, destraba la tarea")
    void frenoTrasUnFallidoDeterministico() {
        doThrow(new ErrorBlockchainException("revert", CausaFallo.REVERT)).doNothing()
                .when(cliente).simularAnclaje(anyString(), anyLong(), any());
        ProcesoAnclaje proceso = proceso();
        proceso.anclarAhora();
        ultimoNumeroLocal = 30;

        proceso.cicloAnclaje();
        assertEquals(1, tabla.size(), "frenada: no abrió otro anclaje");
        assertTrue(proceso.frenoPorFallo().orElseThrow().contains("REVERT"));

        RegistroBlockchain manual = proceso.anclarAhora();
        assertEquals(EstadoAnclaje.ENVIADO, manual.getEstado());
        when(cliente.recibo(manual.getTransactionHash()))
                .thenReturn(Optional.of(new ReciboTransaccion(100L, true, 61_000L, 1_100_000_000L)));
        when(cliente.ultimoBloque()).thenReturn(103L);
        proceso.cicloSeguimiento();
        assertTrue(proceso.frenoPorFallo().isEmpty(), "un anclaje confirmado destraba la tarea");

        ultimoNumeroLocal = 35;
        proceso.cicloAnclaje();
        assertEquals(3, tabla.size());
        assertEquals(31L, ultimo(r -> true).orElseThrow().getDesdeNumero());
    }

    @Test
    @DisplayName("Los 6 FALLIDO viejos sin causa (out of gas del 06/10) frenan la tarea pero NO traban el anclaje manual")
    void fallidosViejosNoTrabanElManual() {
        for (int i = 0; i < 6; i++) {
            RegistroBlockchain viejo = new RegistroBlockchain(1L, 28L, HASH_EVENTO_28, "sepolia", CONTRATO);
            viejo.iniciarIntento();
            viejo.marcarFallido("El contrato rechazó la transacción (revert) en el bloque 1185678" + i, null);
            enTabla(viejo);
        }
        ProcesoAnclaje proceso = proceso();

        proceso.cicloAnclaje();
        assertEquals(6, tabla.size());
        verify(cliente, never()).firmarAnclaje(anyString(), anyLong(), any(), any(), any(), any());

        RegistroBlockchain manual = proceso.anclarAhora();
        assertEquals(EstadoAnclaje.ENVIADO, manual.getEstado());
        assertEquals(1L, manual.getDesdeNumero());
    }

    @Test
    @DisplayName("Un FALLIDO por errores de red (ERROR_DE_RED) no frena: la tarea vuelve a intentar")
    void falloDeRedNoFrenaLaTarea() {
        RegistroBlockchain deRed = new RegistroBlockchain(1L, 20L, HASH_EVENTO_28, "sepolia", CONTRATO);
        deRed.iniciarIntento();
        deRed.marcarFallido("No se pudo transmitir: timeout", CausaFallo.ERROR_DE_RED);
        enTabla(deRed);

        proceso().cicloAnclaje();

        assertEquals(2, tabla.size());
        assertEquals(EstadoAnclaje.ENVIADO, ultimo(r -> true).orElseThrow().getEstado());
    }

    @Test
    @DisplayName("Minada sin gas (usó todo su límite) → FALLIDO SIN_GAS y la tarea queda frenada")
    void minadaSinGasFrenaLaTarea() {
        ProcesoAnclaje proceso = proceso();
        RegistroBlockchain anclaje = proceso.anclarAhora();
        when(cliente.recibo(anclaje.getTransactionHash()))
                .thenReturn(Optional.of(new ReciboTransaccion(100L, false, 100_000L, 1_100_000_000L)));

        proceso.cicloSeguimiento();
        assertEquals(EstadoAnclaje.FALLIDO, unico().getEstado());
        assertEquals(CausaFallo.SIN_GAS, unico().getCausaFallo());
        assertTrue(unico().getUltimoError().contains("sin gas"));

        ultimoNumeroLocal = 30;
        proceso.cicloAnclaje();
        assertEquals(1, tabla.size());
    }

    @Test
    @DisplayName("Minada con revert → FALLIDO REVERT con el error decodificado al volver a simularla")
    void minadaConRevertDecodificado() {
        ProcesoAnclaje proceso = proceso();
        RegistroBlockchain anclaje = proceso.anclarAhora();
        when(cliente.recibo(anclaje.getTransactionHash()))
                .thenReturn(Optional.of(new ReciboTransaccion(100L, false, 40_000L, 1_100_000_000L)));
        doThrow(new ErrorBlockchainException("El contrato rechazaría el anclaje: NumeroNoCreciente(recibido 28, "
                + "último anclado 28)", CausaFallo.REVERT)).when(cliente).simularAnclaje(anyString(), anyLong(), any());

        proceso.cicloSeguimiento();

        assertEquals(CausaFallo.REVERT, unico().getCausaFallo());
        assertTrue(unico().getUltimoError().contains("NumeroNoCreciente"), unico().getUltimoError());
    }

    // ---------- Freno por saldo ----------

    @Test
    @DisplayName("Saldo menor que 10 anclajes → la tarea no abre ni envía; el manual sí envía si alcanza para uno")
    void frenoPorSaldoDeLaTarea() {
        // costo estimado = 60.000 × 1,1 gwei = 0,000066 ETH → 10 anclajes = 0,00066; el nodo exige 100.000 × 2,1 gwei = 0,00021
        when(cliente.saldoWei()).thenReturn(new BigInteger("500000000000000"));
        ProcesoAnclaje proceso = proceso();

        proceso.cicloAnclaje();
        assertTrue(tabla.isEmpty());
        verify(cliente, never()).firmarAnclaje(anyString(), anyLong(), any(), any(), any(), any());

        assertEquals(EstadoAnclaje.ENVIADO, proceso.anclarAhora().getEstado());
    }

    @Test
    @DisplayName("Anclaje manual sin saldo para un anclaje → 409 SALDO_INSUFICIENTE, sin abrir nada")
    void manualSinSaldo() {
        when(cliente.saldoWei()).thenReturn(new BigInteger("100000000000000"));

        ReglaNegocioException e = assertThrows(ReglaNegocioException.class, () -> proceso().anclarAhora());

        assertEquals("SALDO_INSUFICIENTE", e.getCodigoRegla());
        assertTrue(tabla.isEmpty());
    }

    @Test
    @DisplayName("Saldo mínimo = mayor entre N anclajes al costo estimado y lo que el nodo exige por la transacción")
    void calculoDelSaldoMinimo() {
        // 154.507 × 1,1 gwei = 0,0001699577 ETH por anclaje; el nodo exige 200.860 × 2,1 gwei = 0,000421806 ETH
        EstimacionAnclaje estimacion = new EstimacionAnclaje(BigInteger.valueOf(154_507), BigInteger.valueOf(200_860),
                true, BigInteger.valueOf(1_100_000_000L), BigInteger.valueOf(2_100_000_000L));
        ProcesoAnclaje proceso = proceso();

        assertTrue(proceso.frenoPorSaldo(estimacion, new BigInteger("1699577000000000"), 10).isEmpty());
        assertTrue(proceso.frenoPorSaldo(estimacion, new BigInteger("1699576999999999"), 10).isPresent());
        assertTrue(proceso.frenoPorSaldo(estimacion, new BigInteger("421806000000000"), 1).isEmpty());
        assertTrue(proceso.frenoPorSaldo(estimacion, new BigInteger("421805999999999"), 1).isPresent());
    }
}
