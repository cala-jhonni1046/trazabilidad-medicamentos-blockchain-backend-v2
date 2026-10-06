package com.medichain.modules.registroblockchain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test unitario CodigoEsperadoContratoTest en MediChain (R15).
 * Con el código REAL de dos contratos de Sepolia (leído con eth_getCode y
 * guardado en src/test/resources/contrato): 0x9d71… (contracts/MediChainAnchor.bin,
 * con optimizador) y 0xB126… (el mismo fuente compilado en Remix sin
 * optimizador). Los dos son válidos; otro código, no. Los metadatos y los
 * immutables (dueño) no cuentan.
 */
class CodigoEsperadoContratoTest {

    private final CodigoEsperadoContrato esperado = new CodigoEsperadoContrato();

    @Test
    @DisplayName("0x9d71… (con optimizador, el .bin del repo) → \"con optimizador (200 runs)\"")
    void reconoceLaVarianteOptimizada() {
        String codigo = VerificacionInicialAnclajeTest.codigoDePrueba("codigo-sepolia-0x9d71-optimizado.hex");
        assertEquals(Optional.of("con optimizador (200 runs)"), esperado.varianteDe(codigo));
    }

    @Test
    @DisplayName("0xB126… (mismo fuente, Remix sin optimizador) → \"sin optimizador\"")
    void reconoceLaVarianteSinOptimizador() {
        String codigo = VerificacionInicialAnclajeTest.codigoDePrueba("codigo-sepolia-0xB126-sin-optimizador.hex");
        assertEquals(Optional.of("sin optimizador"), esperado.varianteDe(codigo));
    }

    @Test
    @DisplayName("Otros metadatos (otra ruta de archivo en Remix) u otro dueño en los immutables → sigue siendo válido")
    void ignoraMetadatosEInmutables() {
        String codigo = VerificacionInicialAnclajeTest.codigoDePrueba("codigo-sepolia-0x9d71-optimizado.hex");
        // Últimos 53 bytes = metadatos CBOR (51) + largo (2): se cambia un byte del hash IPFS de los metadatos.
        int enMetadatos = codigo.length() - 2 * 20;
        String otrosMetadatos = codigo.substring(0, enMetadatos) + (codigo.charAt(enMetadatos) == 'a' ? 'b' : 'a')
                + codigo.substring(enMetadatos + 1);
        assertTrue(esperado.varianteDe(otrosMetadatos).isPresent());

        String dueno = "1eeb5f841204c7603cf83fc4345428bb4c2c232d";
        assertTrue(codigo.contains(dueno), "el dueño está grabado en el código (immutable)");
        assertTrue(esperado.varianteDe(codigo.replace(dueno, "2c7536e3605d9c16a7a3d7b1898e529396a65c23")).isPresent());
    }

    @Test
    @DisplayName("Un byte de código distinto, código vacío u otro contrato → no coincide (WARN al arrancar)")
    void rechazaOtroCodigo() {
        String codigo = VerificacionInicialAnclajeTest.codigoDePrueba("codigo-sepolia-0x9d71-optimizado.hex");
        String alterado = codigo.substring(0, 20) + (codigo.charAt(20) == '0' ? '1' : '0') + codigo.substring(21);

        assertTrue(esperado.varianteDe(alterado).isEmpty());
        assertTrue(esperado.varianteDe("0x").isEmpty());
        assertTrue(esperado.varianteDe("0x6080604052348015600e575f5ffd5b50").isEmpty());
    }

    @Test
    @DisplayName("La variante optimizada del recurso es exactamente el runtime incluido en contracts/MediChainAnchor.bin")
    void elRecursoCoincideConElBin() throws IOException {
        String bin = Files.readString(Path.of("contracts/MediChainAnchor.bin")).trim();
        // En el código de creación el runtime va con los immutables en cero, como en el recurso.
        String codigo = VerificacionInicialAnclajeTest.codigoDePrueba("codigo-sepolia-0x9d71-optimizado.hex");
        String runtimeSinMetadatos = codigo.substring(2, codigo.length() - 2 * 53)
                .replace("1eeb5f841204c7603cf83fc4345428bb4c2c232d", "0".repeat(40));
        assertTrue(bin.contains(runtimeSinMetadatos));
    }
}
