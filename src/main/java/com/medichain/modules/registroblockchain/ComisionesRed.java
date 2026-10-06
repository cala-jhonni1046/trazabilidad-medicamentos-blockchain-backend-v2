package com.medichain.modules.registroblockchain;

import java.math.BigInteger;

/**
 * Valor ComisionesRed en MediChain.
 * Comisiones de la red en un momento dado (EIP-1559), en wei por unidad de
 * gas: la comisión base del último bloque (se quema) y la propina sugerida
 * para el validador.
 */
public final class ComisionesRed {

    private final BigInteger baseWei;
    private final BigInteger propinaWei;

    /** Crea el valor con la comisión base y la propina, en wei. */
    public ComisionesRed(BigInteger baseWei, BigInteger propinaWei) {
        this.baseWei = baseWei;
        this.propinaWei = propinaWei;
    }

    /** Devuelve la comisión base del último bloque, en wei. */
    public BigInteger getBaseWei() {
        return baseWei;
    }

    /** Devuelve la propina sugerida, en wei. */
    public BigInteger getPropinaWei() {
        return propinaWei;
    }
}
