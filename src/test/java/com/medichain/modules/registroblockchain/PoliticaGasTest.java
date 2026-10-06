package com.medichain.modules.registroblockchain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test unitario PoliticaGasTest en MediChain (R15).
 * Límite = estimación + 30 % dentro de [gas-minimo; gas-maximo]. Reproduce
 * el bug del 06/10/2026: con Glamsterdam el primer anclaje pedía 353.084 de
 * gas y el límite fijo de 300.000 lo recortaba; ahora el anclaje NO se envía.
 */
class PoliticaGasTest {

    @Test
    @DisplayName("BUG del 06/10/2026: estimación 353.084 con techo 300.000 → GAS_SOBRE_EL_MAXIMO (no se recorta ni se envía)")
    void reproduceElBug() {
        PoliticaGas politica = new PoliticaGas(100_000, 300_000);

        ErrorBlockchainException e = assertThrows(ErrorBlockchainException.class,
                () -> politica.limiteParaEnviar(BigInteger.valueOf(353_084)));

        assertEquals(CausaFallo.GAS_SOBRE_EL_MAXIMO, e.getCausa());
        assertTrue(e.getMessage().contains("459010") && e.getMessage().contains("300000"), e.getMessage());
    }

    @Test
    @DisplayName("Con el techo por defecto (500.000) el primer anclaje entra: 353.084 + 30 % = 459.010")
    void primerAnclajeEntraEnElTechoPorDefecto() {
        PoliticaGas politica = new PoliticaGas(new AnclajeProperties());

        assertEquals(BigInteger.valueOf(459_010), politica.limiteParaEnviar(BigInteger.valueOf(353_084)));
        assertEquals(BigInteger.valueOf(200_860), politica.limiteParaEnviar(BigInteger.valueOf(154_507)));
    }

    @Test
    @DisplayName("Piso: una estimación chica sube a gas-minimo (100.000); el techo es inclusivo")
    void pisoYTecho() {
        PoliticaGas politica = new PoliticaGas(100_000, 500_000);

        assertEquals(BigInteger.valueOf(100_000), politica.limitePara(BigInteger.valueOf(50_000)));
        assertTrue(politica.dentroDelMaximo(BigInteger.valueOf(500_000)));
        assertFalse(politica.dentroDelMaximo(BigInteger.valueOf(500_001)));
        assertEquals(BigInteger.valueOf(500_000), politica.limiteParaEnviar(BigInteger.valueOf(384_615)));
        assertThrows(ErrorBlockchainException.class, () -> politica.limiteParaEnviar(BigInteger.valueOf(384_616)));
    }
}
