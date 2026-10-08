package com.medichain.modules.cuarentena;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Enumeración TipoMedida en MediChain.
 * Tipo de medida sanitaria vigente: una cuarentena (reversible, se puede
 * levantar) o un recall (definitivo).
 */
@Schema(enumAsRef = true, description = "Tipo de medida sanitaria vigente: una cuarentena (reversible, se puede levantar) o un recall (definitivo).")
public enum TipoMedida {
    CUARENTENA,
    RECALL
}
