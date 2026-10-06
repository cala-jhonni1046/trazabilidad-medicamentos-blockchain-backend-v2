package com.medichain.modules.registroblockchain;

/**
 * Valor ReciboTransaccion en MediChain.
 * Lo que dice la red de una transacción ya incluida en un bloque: el
 * número de bloque, si se ejecutó bien (status 1) o el contrato la rechazó
 * (status 0), el gas que consumió y el precio efectivo pagado por unidad.
 */
public final class ReciboTransaccion {

    private final long bloque;
    private final boolean exitosa;
    private final long gasUsado;
    private final long precioEfectivoWei;

    /** Crea el valor con todos sus campos. */
    public ReciboTransaccion(long bloque, boolean exitosa, long gasUsado, long precioEfectivoWei) {
        this.bloque = bloque;
        this.exitosa = exitosa;
        this.gasUsado = gasUsado;
        this.precioEfectivoWei = precioEfectivoWei;
    }

    /** Devuelve el número de bloque que incluyó la transacción. */
    public long getBloque() {
        return bloque;
    }

    /** Indica si la transacción se ejecutó bien (false: el contrato la rechazó). */
    public boolean isExitosa() {
        return exitosa;
    }

    /** Devuelve el gas consumido. */
    public long getGasUsado() {
        return gasUsado;
    }

    /** Devuelve el precio efectivo pagado por unidad de gas, en wei. */
    public long getPrecioEfectivoWei() {
        return precioEfectivoWei;
    }
}
