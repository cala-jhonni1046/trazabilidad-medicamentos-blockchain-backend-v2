package com.medichain.utils.validacion;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Anotación @Cuit en MediChain.
 * Valida que el campo sea un CUIT argentino con dígito verificador
 * correcto (delega en CuitUtil.esValido). Un valor null se considera
 * válido: para exigir que el campo esté presente, combinar con
 * @NotBlank.
 */
@Documented
@Constraint(validatedBy = CuitValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface Cuit {

    /** Mensaje de error cuando el CUIT no es válido. */
    String message() default "El CUIT no es válido: el dígito verificador no coincide";

    /** Grupos de validación (estándar de Bean Validation). */
    Class<?>[] groups() default {};

    /** Payload de metadata (estándar de Bean Validation). */
    Class<? extends Payload>[] payload() default {};
}
