package com.medichain.modules.registroblockchain;

import java.math.BigInteger;

/**
 * Regla PoliticaGas en MediChain (R15).
 * Límite de gas de una transacción de anclaje: estimación + 30 %, nunca
 * menor que gas-minimo. Si ese límite supera gas-maximo el anclaje NO se
 * envía (FALLIDO por GAS_SOBRE_EL_MAXIMO, sin gastar nada): nunca se recorta
 * el límite para enviar igual, porque una transacción con menos gas del que
 * necesita falla seguro y cobra todo el límite (lo que pasó el 06/10/2026,
 * cuando Glamsterdam encareció el almacenamiento en Sepolia y el límite fijo
 * de 300.000 quedó por debajo de los ~353.000 que pedía el primer anclaje).
 */
public final class PoliticaGas {

    /** Margen sobre la estimación: +30 %. */
    public static final int MARGEN_PORCENTAJE = 30;

    private static final BigInteger CIEN = BigInteger.valueOf(100);

    private final BigInteger gasMinimo;
    private final BigInteger gasMaximo;

    /** Crea la regla con el piso y el techo dados. */
    public PoliticaGas(long gasMinimo, long gasMaximo) {
        this.gasMinimo = BigInteger.valueOf(gasMinimo);
        this.gasMaximo = BigInteger.valueOf(gasMaximo);
    }

    /** Crea la regla con el piso y el techo de la configuración. */
    public PoliticaGas(AnclajeProperties propiedades) {
        this(propiedades.getGasMinimo(), propiedades.getGasMaximo());
    }

    /** Estimación + 30 % (redondeado hacia arriba), al menos gas-minimo. No aplica el techo. */
    public BigInteger limitePara(BigInteger estimado) {
        BigInteger[] division = estimado.multiply(BigInteger.valueOf(100 + MARGEN_PORCENTAJE)).divideAndRemainder(CIEN);
        BigInteger conMargen = division[1].signum() == 0 ? division[0] : division[0].add(BigInteger.ONE);
        return conMargen.max(gasMinimo);
    }

    /** true si el límite entra en gas-maximo. */
    public boolean dentroDelMaximo(BigInteger limite) {
        return limite.compareTo(gasMaximo) <= 0;
    }

    /**
     * Límite para enviar: limitePara(estimado), o ErrorBlockchainException con
     * causa GAS_SOBRE_EL_MAXIMO si supera gas-maximo (el anclaje no se envía).
     */
    public BigInteger limiteParaEnviar(BigInteger estimado) {
        BigInteger limite = limitePara(estimado);
        if (!dentroDelMaximo(limite)) {
            throw new ErrorBlockchainException("El anclaje necesitaría " + limite + " de gas (estimación " + estimado
                    + " + " + MARGEN_PORCENTAJE + " %), más que el máximo configurado de " + gasMaximo
                    + " (medichain.anclaje.gas-maximo): no se envió para no gastar en una transacción que fallaría.",
                    CausaFallo.GAS_SOBRE_EL_MAXIMO);
        }
        return limite;
    }

    /** Devuelve el techo de gas. */
    public BigInteger getGasMaximo() {
        return gasMaximo;
    }

    /** Devuelve el piso de gas. */
    public BigInteger getGasMinimo() {
        return gasMinimo;
    }
}
