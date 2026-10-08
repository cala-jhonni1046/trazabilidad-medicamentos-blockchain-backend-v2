package com.medichain.modules.empresa;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Enumeración EstadoHabilitacion en MediChain.
 * Ciclo de vida de la habilitación ANMAT de una Empresa: nace PENDIENTE,
 * un inspector la HABILITA o la RECHAZA, y una empresa ya habilitada puede
 * ser SUSPENDIDA.
 */
@Schema(enumAsRef = true, description = "Ciclo de vida de la habilitación ANMAT de una Empresa: nace PENDIENTE, un inspector la HABILITA o la RECHAZA, y una empresa ya habilitada puede ser SUSPENDIDA.")
public enum EstadoHabilitacion {
    PENDIENTE,
    HABILITADA,
    RECHAZADA,
    SUSPENDIDA
}
