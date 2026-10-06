package com.medichain.modules.cuarentena;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Anotación @MotivoManual en MediChain.
 * Un inspector abre cuarentenas de LOTE solo por PREVENTIVA o
 * DEFECTO_CALIDAD; RUPTURA_FRIO, ROBO y RECHAZO_RECEPCION los usa el
 * sistema en sus medidas automáticas (400 si llegan por la API).
 */
@Documented
@Constraint(validatedBy = MotivoManualValidator.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MotivoManual {

    /** Mensaje de error. */
    String message() default "Una cuarentena manual de lote solo admite los motivos PREVENTIVA o DEFECTO_CALIDAD";

    /** Grupos de validación (estándar de Bean Validation). */
    Class<?>[] groups() default {};

    /** Payload de metadata (estándar de Bean Validation). */
    Class<? extends Payload>[] payload() default {};
}
