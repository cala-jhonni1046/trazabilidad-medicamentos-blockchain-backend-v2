package com.medichain.modules.empresa;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Enumeración TipoEmpresa en MediChain.
 * Clasifica el rol que cumple una empresa dentro del circuito de
 * trazabilidad: laboratorio (fabrica), distribuidor (transporta/almacena)
 * o farmacia (dispensa al paciente).
 */
@Schema(enumAsRef = true, description = "Clasifica el rol que cumple una empresa dentro del circuito de trazabilidad: laboratorio (fabrica), distribuidor (transporta/almacena) o farmacia (dispensa al paciente).")
public enum TipoEmpresa {
    LABORATORIO,
    DISTRIBUIDOR,
    FARMACIA
}
