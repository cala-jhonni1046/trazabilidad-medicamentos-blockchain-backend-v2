package com.medichain.modules.registroblockchain;

import java.math.BigInteger;

/**
 * Valor EstimacionAnclaje en MediChain (R15).
 * Lo que costaría el próximo anclaje según la red AHORA: gas estimado
 * (eth_estimateGas), límite que se usaría (estimación + 30 %, al menos
 * gas-minimo) y si entra en gas-maximo, precio por unidad de gas (base +
 * propina) y comisión máxima (2 × base + propina, con el tope de gwei).
 * <ul>
 *   <li>costoEstimadoWei = gas estimado × precio: lo que se espera pagar.</li>
 *   <li>costoMaximoWei = límite × comisión máxima: el saldo que el nodo exige
 *       tener para aceptar la transacción.</li>
 * </ul>
 */
public final class EstimacionAnclaje {

    private final BigInteger gasEstimado;
    private final BigInteger limiteGas;
    private final boolean dentroDelMaximo;
    private final BigInteger precioWei;
    private final BigInteger comisionMaximaWei;

    /** Crea la estimación con todos sus campos. */
    public EstimacionAnclaje(BigInteger gasEstimado, BigInteger limiteGas, boolean dentroDelMaximo, BigInteger precioWei,
                             BigInteger comisionMaximaWei) {
        this.gasEstimado = gasEstimado;
        this.limiteGas = limiteGas;
        this.dentroDelMaximo = dentroDelMaximo;
        this.precioWei = precioWei;
        this.comisionMaximaWei = comisionMaximaWei;
    }

    /** Costo esperado: gas estimado × (base + propina), en wei. */
    public BigInteger costoEstimadoWei() {
        return gasEstimado.multiply(precioWei);
    }

    /** Saldo que el nodo exige para aceptar la transacción: límite × comisión máxima, en wei. */
    public BigInteger costoMaximoWei() {
        return limiteGas.multiply(comisionMaximaWei);
    }

    /** Devuelve el gas estimado (eth_estimateGas). */
    public BigInteger getGasEstimado() {
        return gasEstimado;
    }

    /** Devuelve el límite de gas que se usaría (estimación + 30 %, al menos gas-minimo). */
    public BigInteger getLimiteGas() {
        return limiteGas;
    }

    /** Indica si el límite entra en gas-maximo (si no, el anclaje no se enviaría). */
    public boolean isDentroDelMaximo() {
        return dentroDelMaximo;
    }

    /** Devuelve el precio actual por unidad de gas (base + propina), en wei. */
    public BigInteger getPrecioWei() {
        return precioWei;
    }

    /** Devuelve la comisión máxima por unidad de gas (2 × base + propina, con el tope), en wei. */
    public BigInteger getComisionMaximaWei() {
        return comisionMaximaWei;
    }
}
