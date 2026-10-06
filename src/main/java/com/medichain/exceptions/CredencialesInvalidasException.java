package com.medichain.exceptions;

/**
 * Excepción CredencialesInvalidasException en MediChain.
 * Señala un login fallido (email inexistente, contraseña incorrecta o
 * usuario inactivo). Siempre lleva el mismo mensaje para no revelar cuál
 * de las condiciones falló. GlobalExceptionHandler la traduce a 401.
 */
public class CredencialesInvalidasException extends RuntimeException {

    /** Mensaje único para cualquier falla de login. */
    public static final String MENSAJE = "Credenciales inválidas";

    /** Crea la excepción con el mensaje único de credenciales inválidas. */
    public CredencialesInvalidasException() {
        super(MENSAJE);
    }
}
