package com.medichain.modules.despachologistico;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Enumeración EstadoDespacho en MediChain.
 * Ciclo de vida de un despacho logístico: nace PROGRAMADO, pasa a
 * EN_TRANSITO al registrar la salida, y termina FINALIZADO o ROBADO.
 */
@Schema(enumAsRef = true, description = "Ciclo de vida de un despacho logístico: nace PROGRAMADO, pasa a EN_TRANSITO al registrar la salida, y termina FINALIZADO o ROBADO.")
public enum EstadoDespacho {
    PROGRAMADO,
    EN_TRANSITO,
    FINALIZADO,
    ROBADO,
    CANCELADO
}
