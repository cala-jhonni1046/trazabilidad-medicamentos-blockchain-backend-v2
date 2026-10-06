package com.medichain.modules.trazabilidad;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test unitario HashUtilTest en MediChain.
 * SHA-256 en hex minúscula y seriesHash (A3) independiente del orden.
 */
class HashUtilTest {

    @Test
    @DisplayName("SHA-256 de 'abc' coincide con el vector de prueba estándar")
    void sha256VectorEstandar() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", HashUtil.sha256Hex("abc"));
    }

    @Test
    @DisplayName("seriesHash ordena las series y las une con \\n (valor calculado con sha256sum)")
    void seriesHashOrdenaYUne() {
        String esperado = "75d7f92c60762bb88d7dfbed108245f87bce64de4e397d4c64c51cb3cf469f05";
        assertEquals(esperado, HashUtil.seriesHash(List.of("C3", "A1", "B2")));
        assertEquals(esperado, HashUtil.seriesHash(List.of("A1", "B2", "C3")));
    }
}
