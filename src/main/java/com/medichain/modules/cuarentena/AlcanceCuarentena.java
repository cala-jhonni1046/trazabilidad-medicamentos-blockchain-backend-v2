package com.medichain.modules.cuarentena;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Enumeración AlcanceCuarentena en MediChain.
 * Indica sobre qué entidad aplica la medida sanitaria: un Lote completo,
 * un DespachoLogistico en tránsito, o un conjunto puntual de Bultos.
 */
@Schema(enumAsRef = true, description = "Indica sobre qué entidad aplica la medida sanitaria: un Lote completo, un DespachoLogistico en tránsito, o un conjunto puntual de Bultos.")
public enum AlcanceCuarentena {
    LOTE,
    DESPACHO,
    BULTO
}
