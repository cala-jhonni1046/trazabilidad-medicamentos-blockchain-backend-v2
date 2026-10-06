package com.medichain.utils.validacion;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * ConstraintValidator SerieGs1Validator en MediChain.
 * Ejecuta la anotación @SerieGs1 delegando en SerieUtil.esValida.
 */
public class SerieGs1Validator implements ConstraintValidator<SerieGs1, String> {

    /** Devuelve true si value es null (lo controla @NotBlank) o una serie válida según R3. */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return SerieUtil.esValida(value);
    }
}
