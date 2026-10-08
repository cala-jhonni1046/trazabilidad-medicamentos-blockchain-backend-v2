package com.medichain.utils;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * DTO de salida ErrorResponseDTO en MediChain.
 * Cuerpo de TODA respuesta de error (400, 401, 403, 404, 409, 503). El 409
 * siempre trae el código en "regla" (R1–R15 o un código de la tabla de
 * CLAUDE.md); el 400 de validación trae el detalle por campo en "errors".
 * Los mensajes nunca incluyen detalles internos (tablas, restricciones, trazas).
 */
@Schema(name = "ErrorResponseDTO", description = "Respuesta de error. 409: el código va en 'regla'. "
        + "400 de validación: un elemento por campo en 'errors'.")
public class ErrorResponseDTO {

    @Schema(description = "Código HTTP de la respuesta", example = "409", requiredMode = Schema.RequiredMode.REQUIRED)
    private int status;

    @Schema(description = "Mensaje para mostrar a la persona (sin detalles internos)",
            example = "El lote está vencido: no se puede liberar", requiredMode = Schema.RequiredMode.REQUIRED)
    private String message;

    @Schema(description = "Código de la regla incumplida. Solo en 409: R1–R15 o un código de la tabla (por ejemplo "
            + "TRANSICION_INVALIDA, LOTE_DUPLICADO). null en los demás errores.", example = "R10", nullable = true)
    private String regla;

    @Schema(description = "Errores por campo. Solo en el 400 de validación (Bean Validation); null en los demás.",
            nullable = true)
    private List<FieldError> errors;

    /** Constructor vacío exigido por Jackson. */
    public ErrorResponseDTO() {
    }

    /** Error sin regla ni detalle por campo. */
    public ErrorResponseDTO(int status, String message) {
        this.status = status;
        this.message = message;
    }

    /** Error de regla de negocio (409) con su código. */
    public ErrorResponseDTO(int status, String message, String regla) {
        this.status = status;
        this.message = message;
        this.regla = regla;
    }

    /** Error de validación (400) con el detalle por campo. */
    public ErrorResponseDTO(int status, String message, List<FieldError> errors) {
        this.status = status;
        this.message = message;
        this.errors = errors;
    }

    /** Error con código de regla y detalle por campo. */
    public ErrorResponseDTO(int status, String message, String regla, List<FieldError> errors) {
        this.status = status;
        this.message = message;
        this.regla = regla;
        this.errors = errors;
    }

    /** Devuelve el código HTTP. */
    public int getStatus() {
        return status;
    }

    /** Establece el código HTTP. */
    public void setStatus(int status) {
        this.status = status;
    }

    /** Devuelve el mensaje para mostrar. */
    public String getMessage() {
        return message;
    }

    /** Establece el mensaje para mostrar. */
    public void setMessage(String message) {
        this.message = message;
    }

    /** Devuelve los errores por campo (solo en el 400 de validación). */
    public List<FieldError> getErrors() {
        return errors;
    }

    /** Establece los errores por campo. */
    public void setErrors(List<FieldError> errors) {
        this.errors = errors;
    }

    /** Devuelve el código de la regla incumplida (solo en 409). */
    public String getRegla() {
        return regla;
    }

    /** Establece el código de la regla incumplida. */
    public void setRegla(String regla) {
        this.regla = regla;
    }

    /** Error de validación de un campo: qué campo y por qué. */
    @Schema(name = "ErrorCampoDTO", description = "Error de validación de un campo del request")
    public static class FieldError {

        @Schema(description = "Nombre del campo (o parámetro) que falló", example = "fechaVencimiento",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private String field;

        @Schema(description = "Por qué falló", example = "El campo fechaVencimiento es obligatorio",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private String message;

        /** Constructor vacío exigido por Jackson. */
        public FieldError() {
        }

        /** Crea el error de un campo. */
        public FieldError(String field, String message) {
            this.field = field;
            this.message = message;
        }

        /** Devuelve el nombre del campo. */
        public String getField() {
            return field;
        }

        /** Establece el nombre del campo. */
        public void setField(String field) {
            this.field = field;
        }

        /** Devuelve el motivo del error. */
        public String getMessage() {
            return message;
        }

        /** Establece el motivo del error. */
        public void setMessage(String message) {
            this.message = message;
        }
    }
}
