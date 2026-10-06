package com.medichain.modules.recepcion;

/**
 * Enumeración MotivoRechazoRecepcion en MediChain (R8, R10).
 * Por qué una recepción no fue conforme. Los calcula el servidor; son
 * códigos fijos (no texto libre), así pueden ir al evento BULTO_RECHAZADO.
 */
public enum MotivoRechazoRecepcion {
    PRECINTO_ROTO,
    CANTIDAD_DISTINTA,
    TEMPERATURA_FUERA_DE_RANGO,
    BULTO_BLOQUEADO
}
