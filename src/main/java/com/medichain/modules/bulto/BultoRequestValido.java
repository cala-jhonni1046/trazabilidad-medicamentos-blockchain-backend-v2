package com.medichain.modules.bulto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Anotación @BultoRequestValido en MediChain.
 * Validación de clase de BultoRequestDTO: exactamente una forma de elegir
 * las cajas ("series" o "cantidad"). El error se informa en "series" (400).
 */
@Documented
@Constraint(validatedBy = BultoRequestValidoValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface BultoRequestValido {

    /** Mensaje de error. */
    String message() default "Enviá exactamente una de las dos: la lista 'series' o la 'cantidad' de cajas";

    /** Grupos de validación (estándar de Bean Validation). */
    Class<?>[] groups() default {};

    /** Payload de metadata (estándar de Bean Validation). */
    Class<? extends Payload>[] payload() default {};
}
