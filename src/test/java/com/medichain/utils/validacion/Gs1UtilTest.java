package com.medichain.utils.validacion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test unitario Gs1UtilTest en MediChain.
 * Prueba Gs1Util.esValido() en aislamiento (sin Spring, sin base de
 * datos), tanto para GTIN (14 dígitos) como para GLN (13 dígitos): el
 * algoritmo GS1 es el mismo para ambos, solo cambia la longitud.
 */
class Gs1UtilTest {

    @ParameterizedTest(name = "\"{0}\" es un código GS1 válido")
    @ValueSource(strings = {
            "07791234567898", // GTIN de 14 dígitos
            "7791234567898"   // GLN de 13 dígitos
    })
    @DisplayName("Acepta un GTIN o GLN con dígito verificador GS1 correcto")
    void aceptaCodigoGs1Valido(String codigo) {
        // Arrange: el valor viene del @ValueSource.
        // Act
        boolean resultado = Gs1Util.esValido(codigo);
        // Assert
        assertTrue(resultado);
    }

    @ParameterizedTest(name = "\"{0}\" tiene el dígito verificador GS1 mal")
    @ValueSource(strings = {
            "07791234567899", // GTIN de 14 dígitos, dígito verificador incorrecto
            "7791234567899"   // GLN de 13 dígitos, dígito verificador incorrecto
    })
    @DisplayName("Rechaza un GTIN o GLN con dígito verificador GS1 equivocado")
    void rechazaCodigoGs1Invalido(String codigo) {
        boolean resultado = Gs1Util.esValido(codigo);
        assertFalse(resultado);
    }

    @ParameterizedTest
    @NullSource
    @DisplayName("Rechaza un código GS1 null")
    void rechazaCodigoNulo(String codigo) {
        boolean resultado = Gs1Util.esValido(codigo);
        assertFalse(resultado);
    }

    @ParameterizedTest(name = "\"{0}\" contiene letras")
    @ValueSource(strings = {"0779123456789A", "779123456789A", "ABCDEFGHIJKLMN"})
    @DisplayName("Rechaza un código GS1 que contiene letras")
    void rechazaCodigoConLetras(String codigo) {
        boolean resultado = Gs1Util.esValido(codigo);
        assertFalse(resultado);
    }
}
