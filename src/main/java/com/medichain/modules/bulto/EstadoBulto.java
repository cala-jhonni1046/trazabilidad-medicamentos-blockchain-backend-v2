package com.medichain.modules.bulto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Enumeración EstadoBulto en MediChain.
 * Ciclo de vida de un bulto: nace ARMADO, viaja EN_TRANSITO, llega a un
 * EN_DEPOSITO o queda RECIBIDO/RECHAZADO por la farmacia, o se reporta
 * ROBADO durante el trayecto.
 */
@Schema(enumAsRef = true, description = "Ciclo de vida de un bulto: nace ARMADO, viaja EN_TRANSITO, llega a un EN_DEPOSITO o queda RECIBIDO/RECHAZADO por la farmacia, o se reporta ROBADO durante el trayecto.")
public enum EstadoBulto {
    ARMADO,
    EN_TRANSITO,
    EN_DEPOSITO,
    RECIBIDO,
    RECHAZADO,
    ROBADO,
    DESARMADO
}
