package com.medichain.utils.validacion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test unitario CuitValidatorTest en MediChain.
 * Prueba que el ConstraintValidator de la anotación @Cuit delega
 * correctamente en CuitUtil.esValido(), sin levantar Spring ni el motor
 * de Bean Validation completo: se instancia la clase directamente. El
 * ConstraintValidatorContext no se usa en la implementación, así que se
 * pasa null.
 */
class CuitValidatorTest {

    private final CuitValidator validador = new CuitValidator();

    @Test
    @DisplayName("Devuelve true para un CUIT válido, igual que CuitUtil.esValido()")
    void devuelveTrueParaCuitValido() {
        // Arrange
        String cuit = "30-71234567-1";
        // Act
        boolean resultado = validador.isValid(cuit, null);
        // Assert
        assertTrue(resultado);
        assertTrue(CuitUtil.esValido(cuit), "el validator debe coincidir con CuitUtil.esValido()");
    }

    @ParameterizedTest(name = "CUIT \"{0}\" es inválido")
    @ValueSource(strings = {"30-71234567-0", "1234567890", "sin-numeros"})
    @DisplayName("Devuelve false para un CUIT inválido, igual que CuitUtil.esValido()")
    void devuelveFalseParaCuitInvalido(String cuit) {
        boolean resultado = validador.isValid(cuit, null);
        assertFalse(resultado);
        assertFalse(CuitUtil.esValido(cuit), "el validator debe coincidir con CuitUtil.esValido()");
    }

    @Test
    @DisplayName("Devuelve true para null (lo controla @NotBlank, no @Cuit)")
    void devuelveTrueParaNull() {
        boolean resultado = validador.isValid(null, null);
        assertTrue(resultado);
    }
}
