package com.medichain.modules.unidadtrazable;

/**
 * Enumeración MotivoDevolucion en MediChain (R14).
 * Por qué la farmacia devuelve una caja de su stock. Código fijo (va al
 * evento DEVOLUCION); el detalle libre va en la observación, al evento solo su hash.
 */
public enum MotivoDevolucion {
    DANADA,
    VENCIDA,
    RETIRO_DEL_MERCADO,
    OTRO
}
