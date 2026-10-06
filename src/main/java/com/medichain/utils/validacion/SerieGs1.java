package com.medichain.utils.validacion;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Anotación @SerieGs1 en MediChain.
 * Valida el formato de una serie de unidad trazable según la regla R3
 * (delega en SerieUtil.esValida): alfanumérica, de 1 a 20 caracteres,
 * que no empiece con "779". Un valor null se considera válido: para
 * exigir que el campo esté presente, combinar con @NotBlank.
 */
@Documented
@Constraint(validatedBy = SerieGs1Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface SerieGs1 {

    /** Mensaje de error cuando la serie no es válida. */
    String message() default "La serie no es válida: debe ser alfanumérica, de 1 a 20 caracteres, y no puede empezar con \"779\"";

    /** Grupos de validación (estándar de Bean Validation). */
    Class<?>[] groups() default {};

    /** Payload de metadata (estándar de Bean Validation). */
    Class<? extends Payload>[] payload() default {};
}
