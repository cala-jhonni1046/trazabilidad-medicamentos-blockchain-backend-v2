package com.medichain.modules.registroblockchain;

/**
 * Excepción ErrorBlockchainException en MediChain.
 * Falla al hablar con la red (nodo RPC caído, timeout, transacción
 * rechazada por el nodo; causa null) o un anclaje que no se debe enviar
 * porque fallaría seguro (causa REVERT o GAS_SOBRE_EL_MAXIMO): el proceso lo
 * marca FALLIDO sin gastar gas. El mensaje ya viene saneado por
 * OcultadorSecretos: nunca lleva la URL del RPC ni la clave.
 * GlobalExceptionHandler la responde como 503 con un mensaje fijo.
 */
public class ErrorBlockchainException extends RuntimeException {

    private final CausaFallo causa;

    /** Error de red o del nodo (mensaje ya saneado): se reintenta. */
    public ErrorBlockchainException(String mensaje) {
        this(mensaje, null);
    }

    /** Error con la causa por la que el anclaje fallaría seguro (null: error de red). */
    public ErrorBlockchainException(String mensaje, CausaFallo causa) {
        super(mensaje);
        this.causa = causa;
    }

    /** Causa determinística del fallo, o null si es un error de red (reintentable). */
    public CausaFallo getCausa() {
        return causa;
    }

    /** Indica si el anclaje fallaría seguro (revert o gas): no se envía y se marca FALLIDO. */
    public boolean isDeterministico() {
        return causa != null;
    }
}
