package com.medichain.utils.validacion;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Anotación @Gtin en MediChain.
 * Valida que el campo sea un GTIN de 14 dígitos con dígito verificador
 * GS1 correcto (delega en Gs1Util.esValido). Un valor null se considera
 * válido: para exigir que el campo esté presente, combinar con
 * @NotBlank.
 */
@Documented
@Constraint(validatedBy = GtinValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface Gtin {

    /** Mensaje de error cuando el GTIN no es válido. */
    String message() default "El GTIN no es válido: debe tener 14 dígitos con dígito verificador GS1 correcto";

    /** Grupos de validación (estándar de Bean Validation). */
    Class<?>[] groups() default {};

    /** Payload de metadata (estándar de Bean Validation). */
    Class<? extends Payload>[] payload() default {};
}
