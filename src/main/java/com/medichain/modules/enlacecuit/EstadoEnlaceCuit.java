package com.medichain.modules.enlacecuit;

/**
 * Enumeración EstadoEnlaceCuit en MediChain.
 * Ciclo de vida del circuito comercial entre laboratorio, distribuidor y
 * farmacia: nace PENDIENTE_EMPRESAS (a la espera de que ambas empresas
 * acepten), pasa a PENDIENTE_INSPECTOR cuando ya aceptaron, y un inspector
 * lo APRUEBA o lo RECHAZA. Un enlace aprobado puede SUSPENDERSE.
 */
public enum EstadoEnlaceCuit {
    PENDIENTE_EMPRESAS,
    PENDIENTE_INSPECTOR,
    APROBADO,
    RECHAZADO,
    SUSPENDIDO
}
