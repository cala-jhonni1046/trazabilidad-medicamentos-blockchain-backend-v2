package com.medichain.modules.bulto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validador BultoRequestValidoValidator en MediChain.
 * Implementa @BultoRequestValido: exactamente una de "series" o "cantidad".
 */
public class BultoRequestValidoValidator implements ConstraintValidator<BultoRequestValido, BultoRequestDTO> {

    /** Válido si llega una sola de las dos formas. */
    @Override
    public boolean isValid(BultoRequestDTO dto, ConstraintValidatorContext context) {
        if (dto == null || (dto.getSeries() != null) != (dto.getCantidad() != null)) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("series").addConstraintViolation();
        return false;
    }
}
