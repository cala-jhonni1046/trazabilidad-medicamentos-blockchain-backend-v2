package com.medichain.modules.registroblockchain;

/**
 * Valor AnclajeEnContrato en MediChain.
 * Un anclaje tal como lo guarda el contrato MediChainAnchor en Sepolia:
 * el número de evento y su hash (64 hexadecimales en minúscula, sin 0x,
 * el mismo formato que EventoTrazabilidad.hash).
 */
public final class AnclajeEnContrato {

    private final long numero;
    private final String hash;

    /** Crea el valor con el número de evento y su hash. */
    public AnclajeEnContrato(long numero, String hash) {
        this.numero = numero;
        this.hash = hash;
    }

    /** Devuelve el número de evento anclado. */
    public long getNumero() {
        return numero;
    }

    /** Devuelve el hash anclado (64 hexadecimales en minúscula, sin 0x). */
    public String getHash() {
        return hash;
    }
}
