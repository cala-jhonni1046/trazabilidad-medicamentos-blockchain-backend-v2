package com.medichain.exceptions;

/**
 * Excepción DocumentoInvalidoException en MediChain.
 * El archivo subido no es un PDF válido o supera el tamaño permitido.
 * GlobalExceptionHandler la traduce a 400.
 */
public class DocumentoInvalidoException extends RuntimeException {

    /** Crea la excepción con un mensaje apto para el cliente. */
    public DocumentoInvalidoException(String message) {
        super(message);
    }
}
