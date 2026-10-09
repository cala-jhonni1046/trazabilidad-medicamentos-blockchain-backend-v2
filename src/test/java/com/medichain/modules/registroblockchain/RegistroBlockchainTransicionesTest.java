package com.medichain.modules.registroblockchain;

import com.medichain.exceptions.ReglaNegocioException;
import com.medichain.testutil.DatosDePrueba;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test unitario RegistroBlockchainTransicionesTest en MediChain (R15).
 * PENDIENTE → ENVIADO → CONFIRMADO; ENVIADO → ENVIADO (reemplazo);
 * PENDIENTE/ENVIADO → FALLIDO; cualquier otra → TRANSICION_INVALIDA.
 */
class RegistroBlockchainTransicionesTest {

    private static final Instant AHORA = Instant.parse("2026-10-05T12:00:00Z");
    private static final String CONTRATO = "0x" + "1".repeat(40);

    /** Anclaje PENDIENTE de los eventos 1..28. */
    private RegistroBlockchain pendiente() {
        return new RegistroBlockchain(1L, 28L, "aa".repeat(32), "sepolia", CONTRATO);
    }

    /** Recibo exitoso en el bloque 100. */
    private ReciboTransaccion recibo() {
        return new ReciboTransaccion(100L, true, 61_234L, 1_200_000_000L);
    }

    @Test
    @DisplayName("PENDIENTE → ENVIADO → (2 confirmaciones: sigue ENVIADO) → CONFIRMADO con bloque, gas y costo")
    void caminoFeliz() {
        RegistroBlockchain anclaje = pendiente();
        anclaje.iniciarIntento();
        anclaje.registrarTransaccion(DatosDePrueba.transaccion("0x" + "ab".repeat(32)));
        assertEquals(EstadoAnclaje.PENDIENTE, anclaje.getEstado(), "firmada pero todavía no transmitida");
        anclaje.marcarEnviado(AHORA);
        assertEquals(EstadoAnclaje.ENVIADO, anclaje.getEstado());

        anclaje.registrarInclusion(recibo(), 2, 3, AHORA);
        assertEquals(EstadoAnclaje.ENVIADO, anclaje.getEstado());
        anclaje.registrarInclusion(recibo(), 3, 3, AHORA.plusSeconds(40));

        assertEquals(EstadoAnclaje.CONFIRMADO, anclaje.getEstado());
        assertEquals(100L, anclaje.getBloque());
        assertEquals(61_234L, anclaje.getGasUsado());
        assertEquals("0.0000734808", anclaje.costoEth().toPlainString());
        assertEquals("https://sepolia.etherscan.io/tx/0x" + "ab".repeat(32), anclaje.enlaceEtherscan());
    }

    @Test
    @DisplayName("ENVIADO → ENVIADO por reemplazo: nueva transacción, bloque y confirmaciones en cero")
    void reemplazo() {
        RegistroBlockchain anclaje = pendiente();
        anclaje.iniciarIntento();
        anclaje.registrarTransaccion(DatosDePrueba.transaccion("0x" + "ab".repeat(32)));
        anclaje.marcarEnviado(AHORA);
        anclaje.iniciarIntento();

        anclaje.registrarReemplazo(DatosDePrueba.transaccion("0x" + "cd".repeat(32)), AHORA.plusSeconds(180));

        assertEquals(EstadoAnclaje.ENVIADO, anclaje.getEstado());
        assertEquals("0x" + "cd".repeat(32), anclaje.getTransactionHash());
        assertEquals(2, anclaje.getIntentos());
        assertNull(anclaje.getBloque());
    }

    @Test
    @DisplayName("Fallos: espera hasta proximoIntento; al llegar a max-intentos → FALLIDO")
    void fallosHastaFallido() {
        RegistroBlockchain anclaje = pendiente();
        for (int intento = 1; intento < 5; intento++) {
            anclaje.iniciarIntento();
            anclaje.registrarFallo("timeout", AHORA.plusSeconds(30), 5);
            assertEquals(EstadoAnclaje.PENDIENTE, anclaje.getEstado());
            assertTrue(!anclaje.puedeIntentarse(AHORA) && anclaje.puedeIntentarse(AHORA.plusSeconds(30)));
        }
        anclaje.iniciarIntento();
        anclaje.registrarFallo("timeout", AHORA.plusSeconds(30), 5);

        assertEquals(EstadoAnclaje.FALLIDO, anclaje.getEstado());
        assertEquals("timeout", anclaje.getUltimoError());
        assertNull(anclaje.getProximoIntento());
    }

    @Test
    @DisplayName("Transiciones inválidas → TRANSICION_INVALIDA (enviar sin firmar, intentar un CONFIRMADO, tocar un FALLIDO)")
    void transicionesInvalidas() {
        RegistroBlockchain sinFirmar = pendiente();
        assertEquals("TRANSICION_INVALIDA",
                assertThrows(ReglaNegocioException.class, () -> sinFirmar.marcarEnviado(AHORA)).getCodigoRegla());

        RegistroBlockchain confirmado = DatosDePrueba.anclajeConfirmado(1, 28, "aa".repeat(32), CONTRATO);
        assertThrows(ReglaNegocioException.class, confirmado::iniciarIntento);
        assertThrows(ReglaNegocioException.class, () -> confirmado.marcarFallido("x", CausaFallo.REVERT));

        RegistroBlockchain fallido = pendiente();
        fallido.marcarFallido("revert", CausaFallo.REVERT);
        assertThrows(ReglaNegocioException.class, fallido::iniciarIntento);
        assertThrows(ReglaNegocioException.class, () -> fallido.registrarFallo("x", AHORA, 5));
    }

    @Test
    @DisplayName("El recibo desaparece (reorganización) → ENVIADO sin bloque, vuelve a esperar")
    void reorganizacion() {
        RegistroBlockchain anclaje = pendiente();
        anclaje.iniciarIntento();
        anclaje.registrarTransaccion(DatosDePrueba.transaccion("0x" + "ab".repeat(32)));
        anclaje.marcarEnviado(AHORA);
        anclaje.registrarInclusion(recibo(), 1, 3, AHORA);

        anclaje.registrarSinInclusion();

        assertEquals(EstadoAnclaje.ENVIADO, anclaje.getEstado());
        assertNull(anclaje.getBloque());
        assertEquals(0, anclaje.getConfirmaciones());
    }

    @Test
    @DisplayName("Al llegar a max-intentos por fallos de red la causa es ERROR_DE_RED (no frena la tarea)")
    void falloDeRedNoFrena() {
        RegistroBlockchain anclaje = pendiente();
        for (int intento = 1; intento <= 5; intento++) {
            anclaje.iniciarIntento();
            anclaje.registrarFallo("timeout", AHORA.plusSeconds(30), 5);
        }

        assertEquals(EstadoAnclaje.FALLIDO, anclaje.getEstado());
        assertEquals(CausaFallo.ERROR_DE_RED, anclaje.getCausaFallo());
        assertFalse(anclaje.frenaLaTareaAutomatica());
    }

    @Test
    @DisplayName("Frenan la tarea: REVERT, SIN_GAS, GAS_SOBRE_EL_MAXIMO y los FALLIDO sin causa (anteriores a la columna)")
    void causasQueFrenan() {
        for (CausaFallo causa : CausaFallo.values()) {
            RegistroBlockchain anclaje = pendiente();
            anclaje.marcarFallido("x", causa);
            assertEquals(causa.frenaLaTarea(), anclaje.frenaLaTareaAutomatica(), causa.name());
        }
        assertTrue(CausaFallo.REVERT.frenaLaTarea() && CausaFallo.SIN_GAS.frenaLaTarea()
                && CausaFallo.GAS_SOBRE_EL_MAXIMO.frenaLaTarea());
        assertFalse(CausaFallo.SIN_INCLUIR.frenaLaTarea() || CausaFallo.ERROR_DE_RED.frenaLaTarea());

        RegistroBlockchain anterior = pendiente();
        anterior.marcarFallido("El contrato rechazó la transacción (revert) en el bloque 11856780", null);
        assertTrue(anterior.frenaLaTareaAutomatica(), "los FALLIDO sin causa (06/10/2026) frenan");
        assertFalse(DatosDePrueba.anclajeConfirmado(1, 28, "aa".repeat(32), CONTRATO).frenaLaTareaAutomatica());
    }
}
