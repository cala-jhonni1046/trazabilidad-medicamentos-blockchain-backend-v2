package com.medichain.modules.registroblockchain;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Enumeración EstadoAnclaje en MediChain.
 * Ciclo de vida del anclaje de un rango de eventos a la blockchain: nace
 * PENDIENTE, pasa a ENVIADO al transmitir la transacción, y termina
 * CONFIRMADO o FALLIDO.
 */
@Schema(enumAsRef = true, description = "Ciclo de vida del anclaje de un rango de eventos a la blockchain: nace PENDIENTE, pasa a ENVIADO al transmitir la transacción, y termina CONFIRMADO o FALLIDO.")
public enum EstadoAnclaje {
    PENDIENTE,
    ENVIADO,
    CONFIRMADO,
    FALLIDO
}
