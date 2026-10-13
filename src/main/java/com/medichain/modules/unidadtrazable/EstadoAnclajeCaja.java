package com.medichain.modules.unidadtrazable;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Enumeración EstadoAnclajeCaja en MediChain (R15).
 * Estado del anclaje en blockchain que cubre el último hito del recorrido
 * público de una caja. No es EstadoAnclaje (el ciclo de vida de un registro
 * de anclaje): acá PENDIENTE significa "todavía no se ancló" y no hay FALLIDO
 * (un anclaje fallido no cubre nada). No se guarda: lo calcula
 * VerificacionPublicaService.
 */
@Schema(enumAsRef = true, description = "Anclaje que cubre el recorrido público de la caja: CONFIRMADO o ENVIADO (con transacción), PENDIENTE (todavía no se ancló) o NO_DISPONIBLE (el anclaje está deshabilitado).")
public enum EstadoAnclajeCaja {
    CONFIRMADO,
    ENVIADO,
    PENDIENTE,
    NO_DISPONIBLE
}
