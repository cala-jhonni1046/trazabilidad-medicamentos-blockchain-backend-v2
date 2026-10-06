package com.medichain.modules.lote;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Anotación @LoteRequestValido en MediChain.
 * Validación de clase de LoteRequestDTO: exactamente una forma de series
 * (lista "series" o "cantidad" a generar) y vencimiento posterior a la
 * fabricación. Cada error se informa en el campo que hay que corregir (400).
 */
@Documented
@Constraint(validatedBy = LoteRequestValidoValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface LoteRequestValido {

    /** Mensaje por defecto (cada regla arma el suyo). */
    String message() default "Datos de lote inválidos";

    /** Grupos de validación (estándar de Bean Validation). */
    Class<?>[] groups() default {};

    /** Payload de metadata (estándar de Bean Validation). */
    Class<? extends Payload>[] payload() default {};
}
