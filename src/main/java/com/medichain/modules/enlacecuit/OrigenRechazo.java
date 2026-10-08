package com.medichain.modules.enlacecuit;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Enumeración OrigenRechazo en MediChain.
 * Quién rechazó un circuito: una de las empresas invitadas (antes de que
 * acepten las dos) o el inspector (después). En ambos casos el rechazo es
 * definitivo; el laboratorio puede volver a proponer el mismo par.
 */
@Schema(enumAsRef = true, description = "Quién rechazó un circuito: una de las empresas invitadas (antes de que acepten las dos) o el inspector (después).")
public enum OrigenRechazo {
    DISTRIBUIDOR,
    FARMACIA,
    INSPECTOR
}
