package com.medichain.modules.lote;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Enumeración EstadoLote en MediChain.
 * Ciclo de vida de un lote de medicamentos: nace PENDIENTE_LIBERACION,
 * un usuario lo LIBERA, puede pasar a CUARENTENA por una medida sanitaria,
 * o directamente a RECALL.
 */
@Schema(enumAsRef = true, description = "Ciclo de vida de un lote de medicamentos: nace PENDIENTE_LIBERACION, un usuario lo LIBERA, puede pasar a CUARENTENA por una medida sanitaria, o directamente a RECALL.")
public enum EstadoLote {
    PENDIENTE_LIBERACION,
    LIBERADO,
    CUARENTENA,
    RECALL
}
