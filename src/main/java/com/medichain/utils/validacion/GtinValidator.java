package com.medichain.utils.validacion;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * ConstraintValidator GtinValidator en MediChain.
 * Ejecuta la anotación @Gtin: exige 14 dígitos y dígito verificador GS1
 * correcto (Gs1Util.esValido).
 */
public class GtinValidator implements ConstraintValidator<Gtin, String> {

    /** Devuelve true si value es null (lo controla @NotBlank) o un GTIN de 14 dígitos válido. */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return value.matches("\\d{14}") && Gs1Util.esValido(value);
    }
}
