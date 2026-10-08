package com.medichain.modules.usuario;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Enumeración RolUsuario en MediChain.
 * Perfil de acceso de un usuario dentro del sistema.
 */
@Schema(enumAsRef = true, description = "Perfil de acceso de un usuario dentro del sistema.")
public enum RolUsuario {
    SEDE_CENTRAL,
    INSPECTOR,
    LABORATORIO,
    DISTRIBUIDOR,
    FARMACIA,
    PACIENTE
}
