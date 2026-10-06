package com.medichain.modules.cuarentena;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validador MotivoManualValidator en MediChain.
 * Implementa @MotivoManual: null lo resuelve @NotNull; si no, PREVENTIVA o DEFECTO_CALIDAD.
 */
public class MotivoManualValidator implements ConstraintValidator<MotivoManual, MotivoBloqueo> {

    /** Valida que el motivo sea de los que puede usar un inspector. */
    @Override
    public boolean isValid(MotivoBloqueo motivo, ConstraintValidatorContext context) {
        return motivo == null || motivo == MotivoBloqueo.PREVENTIVA || motivo == MotivoBloqueo.DEFECTO_CALIDAD;
    }
}
