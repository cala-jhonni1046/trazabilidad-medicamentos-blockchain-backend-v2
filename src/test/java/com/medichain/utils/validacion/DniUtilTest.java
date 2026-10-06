package com.medichain.utils.validacion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test unitario DniUtilTest en MediChain (R13).
 * Validación de 7 u 8 dígitos y enmascarado con solo los últimos 3 visibles.
 */
class DniUtilTest {

    @Test
    @DisplayName("Enmascara dejando visibles solo los últimos 3 dígitos (8 y 7 dígitos)")
    void enmascara() {
        assertEquals("*****006", DniUtil.enmascarar("30111006"));
        assertEquals("****567", DniUtil.enmascarar("1234567"));
    }

    @Test
    @DisplayName("DNI ausente → null; inválido → excepción (nunca se guarda a medias)")
    void ausenteEInvalido() {
        assertNull(DniUtil.enmascarar(null));
        assertNull(DniUtil.enmascarar(" "));
        assertThrows(IllegalArgumentException.class, () -> DniUtil.enmascarar("30.111.006"));
    }

    @Test
    @DisplayName("Válido: 7 u 8 dígitos sin puntos")
    void validacion() {
        assertTrue(DniUtil.esValido("30111006"));
        assertTrue(DniUtil.esValido("1234567"));
        assertFalse(DniUtil.esValido("123456"));
        assertFalse(DniUtil.esValido("301110061"));
        assertFalse(DniUtil.esValido("3011100A"));
    }
}
