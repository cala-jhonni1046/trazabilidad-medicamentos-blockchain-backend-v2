package com.medichain.modules.registroblockchain;

import java.math.BigInteger;

/**
 * Valor TransaccionFirmada en MediChain.
 * Transacción de anclaje ya firmada con la clave de la billetera: su hash
 * (calculado localmente, antes de transmitirla), los bytes firmados en
 * hexadecimal y los parámetros con que se firmó (nonce, límite de gas,
 * comisión máxima y propina). Firmar de nuevo con los mismos parámetros da
 * los mismos bytes (la firma es determinística, RFC 6979).
 */
public final class TransaccionFirmada {

    private final String hash;
    private final String hexFirmado;
    private final BigInteger nonce;
    private final BigInteger limiteGas;
    private final BigInteger comisionMaximaWei;
    private final BigInteger propinaWei;

    /** Crea el valor con todos sus campos. */
    public TransaccionFirmada(String hash, String hexFirmado, BigInteger nonce, BigInteger limiteGas,
                              BigInteger comisionMaximaWei, BigInteger propinaWei) {
        this.hash = hash;
        this.hexFirmado = hexFirmado;
        this.nonce = nonce;
        this.limiteGas = limiteGas;
        this.comisionMaximaWei = comisionMaximaWei;
        this.propinaWei = propinaWei;
    }

    /** Devuelve el hash de la transacción (0x + 64 hexadecimales). */
    public String getHash() {
        return hash;
    }

    /** Devuelve la transacción firmada en hexadecimal (lo que se transmite). */
    public String getHexFirmado() {
        return hexFirmado;
    }

    /** Devuelve el nonce de la billetera usado. */
    public BigInteger getNonce() {
        return nonce;
    }

    /** Devuelve el límite de gas. */
    public BigInteger getLimiteGas() {
        return limiteGas;
    }

    /** Devuelve la comisión máxima por unidad de gas, en wei. */
    public BigInteger getComisionMaximaWei() {
        return comisionMaximaWei;
    }

    /** Devuelve la propina por unidad de gas, en wei. */
    public BigInteger getPropinaWei() {
        return propinaWei;
    }
}
