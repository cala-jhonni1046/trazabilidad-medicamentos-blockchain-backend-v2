package com.medichain.modules.auth;


/**
 * DTO de salida RegistroResponseDTO en MediChain.
 * Respuesta de un registro público: solo un identificador legible
 * (CUIT de la empresa o email del paciente) y el estado; nunca ids internos.
 */
public class RegistroResponseDTO {

    private String identificador;

    private String estado;

    private String mensaje;

    /** Constructor vacío exigido por Spring/Jackson. */
    public RegistroResponseDTO() {
    }

    /** Devuelve el identificador legible (CUIT o email). */
    public String getIdentificador() {
        return identificador;
    }

    /** Establece el identificador legible (CUIT o email). */
    public void setIdentificador(String identificador) {
        this.identificador = identificador;
    }

    /** Devuelve el estado resultante. */
    public String getEstado() {
        return estado;
    }

    /** Establece el estado resultante. */
    public void setEstado(String estado) {
        this.estado = estado;
    }

    /** Devuelve el mensaje para el usuario. */
    public String getMensaje() {
        return mensaje;
    }

    /** Establece el mensaje para el usuario. */
    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }
}
