package com.medichain.modules.empresa;

/**
 * Enumeración EstadoHabilitacion en MediChain.
 * Ciclo de vida de la habilitación ANMAT de una Empresa: nace PENDIENTE,
 * un inspector la HABILITA o la RECHAZA, y una empresa ya habilitada puede
 * ser SUSPENDIDA.
 */
public enum EstadoHabilitacion {
    PENDIENTE,
    HABILITADA,
    RECHAZADA,
    SUSPENDIDA
}
