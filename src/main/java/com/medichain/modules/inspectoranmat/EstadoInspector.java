package com.medichain.modules.inspectoranmat;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Enumeración EstadoInspector en MediChain.
 * Estado administrativo de un inspector ANMAT: ACTIVO mientras ejerce sus
 * funciones, BAJA una vez dado de baja.
 */
@Schema(enumAsRef = true, description = "Estado administrativo de un inspector ANMAT: ACTIVO mientras ejerce sus funciones, BAJA una vez dado de baja.")
public enum EstadoInspector {
    ACTIVO,
    BAJA
}
