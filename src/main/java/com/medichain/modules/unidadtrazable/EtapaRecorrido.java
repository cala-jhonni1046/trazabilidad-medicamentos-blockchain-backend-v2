package com.medichain.modules.unidadtrazable;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Enumeración EtapaRecorrido en MediChain.
 * Hito del recorrido público de una caja en la verificación pública:
 * fabricación y liberación del lote, cada salida de viaje y cada recepción
 * (conforme o rechazada). Nunca la dispensación (R13). No se guarda: la
 * calcula VerificacionPublicaService.
 */
@Schema(enumAsRef = true, description = "Hito del recorrido público de una caja: FABRICADO y LIBERADO (lote), DESPACHADO (salida de un viaje), RECIBIDO o RECHAZADO (recepción del bulto). Nunca la dispensación (R13).")
public enum EtapaRecorrido {
    FABRICADO,
    LIBERADO,
    DESPACHADO,
    RECIBIDO,
    RECHAZADO
}
