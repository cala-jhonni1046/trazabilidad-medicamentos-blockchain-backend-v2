package com.medichain.modules.cuarentena;

/**
 * Enumeración EstadoCuarentena en MediChain.
 * Ciclo de vida de la medida sanitaria: nace ACTIVA, un inspector la
 * LEVANTA, o la CONVIERTE_EN_RECALL si el problema es definitivo.
 */
public enum EstadoCuarentena {
    ACTIVA,
    LEVANTADA,
    CONVERTIDA_EN_RECALL
}
