package com.medichain.modules.despachologistico;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Enumeración TramoDespacho en MediChain.
 * Tramo del circuito que recorre un despacho logístico: del laboratorio
 * al distribuidor, o del distribuidor a la farmacia.
 */
@Schema(enumAsRef = true, description = "Tramo del circuito que recorre un despacho logístico: del laboratorio al distribuidor, o del distribuidor a la farmacia.")
public enum TramoDespacho {
    LAB_A_DISTRIBUIDOR,
    DISTRIBUIDOR_A_FARMACIA
}
