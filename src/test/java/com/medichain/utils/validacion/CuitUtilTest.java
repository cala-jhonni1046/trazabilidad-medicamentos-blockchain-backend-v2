package com.medichain.utils.validacion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test unitario CuitUtilTest en MediChain.
 * Prueba CuitUtil.esValido() y CuitUtil.normalizar() en aislamiento
 * (sin Spring, sin base de datos).
 */
class CuitUtilTest {

    @ParameterizedTest(name = "CUIT \"{0}\" es válido")
    @ValueSource(strings = {"30-71234567-1", "30712345671"})
    @DisplayName("Acepta un CUIT válido, con o sin guiones")
    void aceptaCuitValidoConOSinGuiones(String cuit) {
        // Arrange: el valor viene del @ValueSource.
        // Act
        boolean resultado = CuitUtil.esValido(cuit);
        // Assert
        assertTrue(resultado);
    }

    @ParameterizedTest(name = "CUIT \"{0}\" tiene el dígito verificador mal")
    @ValueSource(strings = {"30-71234567-0", "30-71234567-2", "30-71234567-9"})
    @DisplayName("Rechaza un CUIT con dígito verificador equivocado")
    void rechazaCuitConDigitoVerificadorEquivocado(String cuit) {
        boolean resultado = CuitUtil.esValido(cuit);
        assertFalse(resultado);
    }

    @ParameterizedTest(name = "\"{0}\" tiene menos de 11 dígitos")
    @ValueSource(strings = {"1234567890", "3071234", "3-0-7"})
    @DisplayName("Rechaza un CUIT con menos de 11 dígitos")
    void rechazaCuitConMenosDeOnceDigitos(String cuit) {
        boolean resultado = CuitUtil.esValido(cuit);
        assertFalse(resultado);
    }

    @ParameterizedTest(name = "\"{0}\" contiene letras")
    @ValueSource(strings = {"3A-71234567-1", "30-7123456A-1", "AB-CDEFGHIJ-K"})
    @DisplayName("Rechaza un CUIT que contiene letras")
    void rechazaCuitConLetras(String cuit) {
        boolean resultado = CuitUtil.esValido(cuit);
        assertFalse(resultado);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("Rechaza un CUIT null o vacío")
    void rechazaCuitNuloOVacio(String cuit) {
        boolean resultado = CuitUtil.esValido(cuit);
        assertFalse(resultado);
    }

    @Test
    @DisplayName("Rechaza un CUIT cuyo resto da dígito verificador 10 (no existe CUIT posible)")
    void rechazaCuitConRestoDiezPorqueNoExisteDigitoVerificadorPosible() {
        // Arrange: base "2020000009" da dígito verificador calculado = 10
        // (resto de la división por 11 = 1), que la regla marca inválido
        // sin importar qué dígito se le agregue como verificador.
        String cuit = "20-20000009-1";
        // Act
        boolean resultado = CuitUtil.esValido(cuit);
        // Assert
        assertFalse(resultado);
    }

    @ParameterizedTest(name = "normalizar(\"{0}\") = \"{1}\"")
    @CsvSource({
            "30-71234567-1, 30-71234567-1",
            "30712345671,   30-71234567-1"
    })
    @DisplayName("normalizar() siempre devuelve el formato XX-XXXXXXXX-X")
    void normalizarSiempreDevuelveFormatoCanonico(String entrada, String esperado) {
        // Act
        String resultado = CuitUtil.normalizar(entrada);
        // Assert
        assertEquals(esperado, resultado);
    }
}
