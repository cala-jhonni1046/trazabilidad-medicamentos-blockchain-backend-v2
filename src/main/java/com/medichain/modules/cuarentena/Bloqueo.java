package com.medichain.modules.cuarentena;

/**
 * Valor Bloqueo en MediChain (R10).
 * Resultado de EvaluadorBloqueo cuando algo está bloqueado: la causa (código
 * para el frontend y para las decisiones) y el mensaje para mostrar o para la
 * respuesta 409. Inmutable; "no bloqueado" se representa con su ausencia.
 */
public class Bloqueo {

    private final CausaBloqueo causa;
    private final String mensaje;

    /** Crea el bloqueo con su causa y su mensaje. */
    public Bloqueo(CausaBloqueo causa, String mensaje) {
        this.causa = causa;
        this.mensaje = mensaje;
    }

    /** Devuelve la causa del bloqueo. */
    public CausaBloqueo getCausa() {
        return causa;
    }

    /** Devuelve la explicación del bloqueo. */
    public String getMensaje() {
        return mensaje;
    }
}
