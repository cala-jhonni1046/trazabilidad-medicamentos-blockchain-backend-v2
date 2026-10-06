package com.medichain.utils.validacion;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * ConstraintValidator GlnValidator en MediChain.
 * Ejecuta la anotación @Gln: exige 13 dígitos y dígito verificador GS1
 * correcto (Gs1Util.esValido).
 */
public class GlnValidator implements ConstraintValidator<Gln, String> {

    /** Devuelve true si value es null (lo controla @NotBlank) o un GLN de 13 dígitos válido. */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return value.matches("\\d{13}") && Gs1Util.esValido(value);
    }
}
