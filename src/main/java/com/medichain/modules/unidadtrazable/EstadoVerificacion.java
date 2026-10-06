package com.medichain.modules.unidadtrazable;

/**
 * Enumeración EstadoVerificacion en MediChain.
 * Estado simple de una caja para el paciente en la verificación pública.
 */
public enum EstadoVerificacion {
    APTA,
    YA_DISPENSADA,
    BLOQUEADA,
    ROBADA,
    EN_DISTRIBUCION,
    NO_EXISTE
}
