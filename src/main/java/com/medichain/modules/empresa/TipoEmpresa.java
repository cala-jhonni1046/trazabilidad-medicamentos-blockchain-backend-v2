package com.medichain.modules.empresa;

/**
 * Enumeración TipoEmpresa en MediChain.
 * Clasifica el rol que cumple una empresa dentro del circuito de
 * trazabilidad: laboratorio (fabrica), distribuidor (transporta/almacena)
 * o farmacia (dispensa al paciente).
 */
public enum TipoEmpresa {
    LABORATORIO,
    DISTRIBUIDOR,
    FARMACIA
}
