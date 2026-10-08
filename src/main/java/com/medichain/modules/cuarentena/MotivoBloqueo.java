package com.medichain.modules.cuarentena;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Enumeración MotivoBloqueo en MediChain.
 * Motivo que originó la medida sanitaria.
 */
@Schema(enumAsRef = true, description = "Motivo que originó la medida sanitaria.")
public enum MotivoBloqueo {
    PREVENTIVA,
    DEFECTO_CALIDAD,
    RUPTURA_FRIO,
    ROBO,
    RECHAZO_RECEPCION
}
