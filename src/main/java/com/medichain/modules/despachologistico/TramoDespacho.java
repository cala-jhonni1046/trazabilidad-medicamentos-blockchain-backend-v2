package com.medichain.modules.despachologistico;

/**
 * Enumeración TramoDespacho en MediChain.
 * Tramo del circuito que recorre un despacho logístico: del laboratorio
 * al distribuidor, o del distribuidor a la farmacia.
 */
public enum TramoDespacho {
    LAB_A_DISTRIBUIDOR,
    DISTRIBUIDOR_A_FARMACIA
}
