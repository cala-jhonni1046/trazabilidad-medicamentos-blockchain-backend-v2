package com.medichain.modules.dispensacion;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Anotación @DispensacionRequestValida en MediChain (R13).
 * Particular → sin obra social ni afiliado; con obra social → los dos
 * obligatorios. El error se informa en el campo a corregir (400).
 */
@Documented
@Constraint(validatedBy = DispensacionRequestValidaValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DispensacionRequestValida {

    /** Mensaje por defecto (cada regla arma el suyo). */
    String message() default "Datos de cobertura inválidos";

    /** Grupos de validación (estándar de Bean Validation). */
    Class<?>[] groups() default {};

    /** Payload de metadata (estándar de Bean Validation). */
    Class<? extends Payload>[] payload() default {};
}
