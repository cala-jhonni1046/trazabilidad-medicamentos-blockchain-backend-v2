package com.medichain.modules.registroblockchain;

import com.medichain.config.AnclajeConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test unitario AnclajePropertiesTest en MediChain.
 * Deshabilitado arranca sin nada (cliente deshabilitado); habilitado con
 * algo faltante o mal formado → la app no arranca, y el mensaje nombra la
 * variable pero NUNCA muestra su valor.
 */
class AnclajePropertiesTest {

    private static final String URL = "https://eth-sepolia.g.alchemy.com/v2/ClaveDeApiDePrueba123";
    private static final String CLAVE = "4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318";
    private static final String CONTRATO = "0x1111111111111111111111111111111111111111";

    /** Propiedades habilitadas y completas. */
    private AnclajeProperties completas() {
        AnclajeProperties propiedades = new AnclajeProperties();
        propiedades.setHabilitado(true);
        propiedades.setRpcUrl(URL);
        propiedades.setClavePrivada(CLAVE);
        propiedades.setContrato(CONTRATO);
        return propiedades;
    }

    @Test
    @DisplayName("Deshabilitado (por defecto) → cliente deshabilitado, sin pedir ninguna variable")
    void deshabilitadoPorDefecto() {
        AnclajeProperties propiedades = new AnclajeProperties();
        assertFalse(propiedades.isHabilitado());
        assertInstanceOf(ClienteBlockchainDeshabilitado.class, new AnclajeConfig().clienteBlockchain(propiedades));
    }

    @Test
    @DisplayName("Habilitado y completo → cliente web3j (sin conectarse todavía)")
    void habilitadoCompleto() {
        ClienteBlockchain cliente = new AnclajeConfig().clienteBlockchain(completas());
        assertInstanceOf(ClienteBlockchainWeb3j.class, cliente);
        assertTrue(cliente.direccionBilletera().startsWith("0x"));
        cliente.cerrar();
    }

    @Test
    @DisplayName("Falta la clave → no arranca; el mensaje nombra WALLET_PRIVATE_KEY y no muestra la URL")
    void faltaLaClave() {
        AnclajeProperties propiedades = completas();
        propiedades.setClavePrivada("");

        IllegalStateException e = assertThrows(IllegalStateException.class, propiedades::validarFormato);

        assertTrue(e.getMessage().contains("falta WALLET_PRIVATE_KEY"), e.getMessage());
        assertFalse(e.getMessage().contains("ClaveDeApiDePrueba123"));
    }

    @Test
    @DisplayName("Formatos inválidos → mensajes por variable, sin ninguno de los valores")
    void formatosInvalidos() {
        AnclajeProperties propiedades = new AnclajeProperties();
        propiedades.setHabilitado(true);
        propiedades.setRpcUrl("http://eth-sepolia.g.alchemy.com/v2/ClaveDeApiDePrueba123");
        propiedades.setClavePrivada("0xNoEsUnaClaveSecreta");
        propiedades.setContrato("0xContratoMalEscrito");

        IllegalStateException e = assertThrows(IllegalStateException.class, propiedades::validarFormato);

        assertTrue(e.getMessage().contains("SEPOLIA_RPC_URL debe empezar con https://"));
        assertTrue(e.getMessage().contains("WALLET_PRIVATE_KEY no tiene el formato"));
        assertTrue(e.getMessage().contains("ANCHOR_CONTRACT_ADDRESS no es una dirección"));
        assertFalse(e.getMessage().contains("ClaveDeApiDePrueba123"));
        assertFalse(e.getMessage().contains("NoEsUnaClaveSecreta"));
        assertFalse(e.getMessage().contains("ContratoMalEscrito"));
    }

    @Test
    @DisplayName("Falta el contrato → el mensaje remite a la guía de despliegue")
    void faltaElContrato() {
        AnclajeProperties propiedades = completas();
        propiedades.setContrato(" ");

        IllegalStateException e = assertThrows(IllegalStateException.class, propiedades::validarFormato);

        assertTrue(e.getMessage().contains("falta ANCHOR_CONTRACT_ADDRESS"));
        assertTrue(e.getMessage().contains("GUIA-DESPLIEGUE"));
        assertDoesNotThrow(completas()::validarFormato);
    }

    @Test
    @DisplayName("Valores por defecto de gas y saldo: piso 100.000, techo 500.000, 10 anclajes; techo menor que el piso → no arranca")
    void gasYSaldo() {
        AnclajeProperties propiedades = completas();
        assertEquals(100_000L, propiedades.getGasMinimo());
        assertEquals(500_000L, propiedades.getGasMaximo());
        assertEquals(10, propiedades.getSaldoMinimoAnclajes());

        propiedades.setGasMaximo(90_000);
        IllegalStateException e = assertThrows(IllegalStateException.class, propiedades::validarFormato);
        assertTrue(e.getMessage().contains("gas-maximo no puede ser menor que gas-minimo"), e.getMessage());

        AnclajeProperties sinSaldo = completas();
        sinSaldo.setSaldoMinimoAnclajes(0);
        assertThrows(IllegalStateException.class, sinSaldo::validarFormato);
    }
}
