package com.medichain.modules.empresa;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * DTO de entrada AsignacionInspectorRequestDTO en MediChain.
 * Inspector al que la Sede asigna una solicitud de una provincia sin inspectores.
 */
public class AsignacionInspectorRequestDTO {

    @NotNull(message = "El campo inspectorId es obligatorio")
    private UUID inspectorId;

    /** Constructor vacío exigido por Spring/Jackson. */
    public AsignacionInspectorRequestDTO() {
    }

    /** Devuelve el id del inspector asignado. */
    public UUID getInspectorId() {
        return inspectorId;
    }

    /** Establece el id del inspector asignado. */
    public void setInspectorId(UUID inspectorId) {
        this.inspectorId = inspectorId;
    }
}
