package com.medichain.modules.empresa;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Anotación @DirectorTecnicoSoloLaboratorio en MediChain.
 * Validación de clase de RegistroEmpresaRequestDTO: adminEsDirectorTecnico
 * = true solo es válido si tipo = LABORATORIO. El error se informa en el
 * campo adminEsDirectorTecnico (400), para que el cliente sepa cuál corregir.
 */
@Documented
@Constraint(validatedBy = DirectorTecnicoSoloLaboratorioValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DirectorTecnicoSoloLaboratorio {

    /** Mensaje de error. */
    String message() default "Solo un LABORATORIO puede marcar al administrador como director técnico";

    /** Grupos de validación (estándar de Bean Validation). */
    Class<?>[] groups() default {};

    /** Payload de metadata (estándar de Bean Validation). */
    Class<? extends Payload>[] payload() default {};
}
