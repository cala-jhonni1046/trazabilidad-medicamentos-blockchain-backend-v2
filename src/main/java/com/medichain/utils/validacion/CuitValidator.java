package com.medichain.utils.validacion;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * ConstraintValidator CuitValidator en MediChain.
 * Ejecuta la anotación @Cuit delegando en CuitUtil.esValido.
 */
public class CuitValidator implements ConstraintValidator<Cuit, String> {

    /** Devuelve true si value es null (lo controla @NotBlank) o un CUIT válido. */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return CuitUtil.esValido(value);
    }
}
