package com.medichain.utils.validacion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test unitario SerieUtilTest en MediChain.
 * Prueba SerieUtil.esValida() en aislamiento (sin Spring, sin base de
 * datos), según el formato de la regla R3.
 */
class SerieUtilTest {

    @ParameterizedTest(name = "\"{0}\" es una serie válida")
    @ValueSource(strings = {"0415S000123", "ABC123", "A", "12345678901234567890"})
    @DisplayName("Acepta una serie alfanumérica de hasta 20 caracteres que no empieza con \"779\"")
    void aceptaSerieValida(String serie) {
        // Arrange: el valor viene del @ValueSource (el último caso tiene exactamente 20 caracteres).
        // Act
        boolean resultado = SerieUtil.esValida(serie);
        // Assert
        assertTrue(resultado);
    }

    @ParameterizedTest(name = "\"{0}\" tiene más de 20 caracteres")
    @ValueSource(strings = {"123456789012345678901", "0415S0001234567890123"})
    @DisplayName("Rechaza una serie de más de 20 caracteres")
    void rechazaSerieDeMasDeVeinteCaracteres(String serie) {
        boolean resultado = SerieUtil.esValida(serie);
        assertFalse(resultado);
    }

    @ParameterizedTest(name = "\"{0}\" empieza con \"779\"")
    @ValueSource(strings = {"7790001234", "779", "779ABC"})
    @DisplayName("Rechaza una serie que empieza con \"779\" (R3)")
    void rechazaSerieQueEmpiezaCon779(String serie) {
        boolean resultado = SerieUtil.esValida(serie);
        assertFalse(resultado);
    }

    @ParameterizedTest(name = "\"{0}\" tiene caracteres no alfanuméricos")
    @ValueSource(strings = {"ABC-123", "ABC 123", "ABC#123", "0415_000123"})
    @DisplayName("Rechaza una serie con caracteres no alfanuméricos")
    void rechazaSerieConCaracteresNoAlfanumericos(String serie) {
        boolean resultado = SerieUtil.esValida(serie);
        assertFalse(resultado);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("Rechaza una serie null o vacía")
    void rechazaSerieNulaOVacia(String serie) {
        boolean resultado = SerieUtil.esValida(serie);
        assertFalse(resultado);
    }
}
