package com.medichain.modules.despachologistico;

/**
 * Enumeración EstadoDespacho en MediChain.
 * Ciclo de vida de un despacho logístico: nace PROGRAMADO, pasa a
 * EN_TRANSITO al registrar la salida, y termina FINALIZADO o ROBADO.
 */
public enum EstadoDespacho {
    PROGRAMADO,
    EN_TRANSITO,
    FINALIZADO,
    ROBADO,
    CANCELADO
}
