package com.medichain.exceptions;

/**
 * Excepción ConsultaInvalidaException en MediChain.
 * La consulta combina mal sus parámetros (por ejemplo, ninguno o más de uno de los
 * que se excluyen entre sí): no es un campo mal formado (eso lo frena Bean Validation)
 * ni una regla de negocio (409). GlobalExceptionHandler la traduce a 400.
 */
public class ConsultaInvalidaException extends RuntimeException {

    /** Crea la excepción con un mensaje apto para el cliente. */
    public ConsultaInvalidaException(String message) {
        super(message);
    }
}
