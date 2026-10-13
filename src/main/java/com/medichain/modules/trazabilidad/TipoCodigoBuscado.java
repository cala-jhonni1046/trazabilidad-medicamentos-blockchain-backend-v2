package com.medichain.modules.trazabilidad;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Enumeración TipoCodigoBuscado en MediChain.
 * Qué es lo que encontró la búsqueda de trazabilidad por código. No se guarda:
 * lo calcula TrazabilidadPorCodigoService.
 */
@Schema(enumAsRef = true, description = "Qué encontró la búsqueda por código: un LOTE, un BULTO o una CAJA (GTIN + serie).")
public enum TipoCodigoBuscado {
    LOTE,
    BULTO,
    CAJA
}
