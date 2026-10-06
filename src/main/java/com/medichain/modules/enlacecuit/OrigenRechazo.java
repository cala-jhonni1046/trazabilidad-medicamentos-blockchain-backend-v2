package com.medichain.modules.enlacecuit;

/**
 * Enumeración OrigenRechazo en MediChain.
 * Quién rechazó un circuito: una de las empresas invitadas (antes de que
 * acepten las dos) o el inspector (después). En ambos casos el rechazo es
 * definitivo; el laboratorio puede volver a proponer el mismo par.
 */
public enum OrigenRechazo {
    DISTRIBUIDOR,
    FARMACIA,
    INSPECTOR
}
