package com.medichain.exceptions;

/**
 * Excepción ReglaNegocioException en MediChain.
 * Señala que una operación viola una regla de negocio del dominio
 * (identificada por un código, p. ej. "R11"), a diferencia de
 * ResourceNotFoundException (recurso inexistente) o de los errores de
 * validación de Bean Validation (formato/obligatoriedad de un campo).
 */
public class ReglaNegocioException extends RuntimeException {

    private final String codigoRegla;

    /** Crea la excepción con el código de la regla incumplida y el mensaje explicativo. */
    public ReglaNegocioException(String codigoRegla, String message) {
        super(message);
        this.codigoRegla = codigoRegla;
    }

    /** Devuelve el código de la regla de negocio incumplida (p. ej. "R11"). */
    public String getCodigoRegla() {
        return codigoRegla;
    }
}
