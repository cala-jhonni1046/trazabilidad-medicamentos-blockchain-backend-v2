package com.medichain.modules.unidadtrazable;

/**
 * Enumeración EstadoUnidad en MediChain.
 * Ciclo de vida de una unidad trazable individual, desde que sale del
 * laboratorio hasta que se dispensa, se devuelve o se reporta robada.
 */
public enum EstadoUnidad {
    EN_LABORATORIO,
    EN_TRANSITO,
    EN_DEPOSITO,
    EN_STOCK,
    DISPENSADA,
    DEVUELTA,
    ROBADA,
    RECHAZADA
}
