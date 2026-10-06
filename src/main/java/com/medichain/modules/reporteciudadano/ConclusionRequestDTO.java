package com.medichain.modules.reporteciudadano;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO de entrada ConclusionRequestDTO en MediChain.
 * Conclusión obligatoria del inspector al cerrar un reporte ciudadano. Se
 * guarda en el reporte (el paciente no la ve); al evento va solo su hash.
 */
public class ConclusionRequestDTO {

    @Schema(description = "Conclusión de la investigación", example = "Serie inexistente: se informa a la fiscalía", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "La conclusión es obligatoria")
    @Size(max = 2000, message = "La conclusión no puede superar 2000 caracteres")
    private String conclusion;

    /** Constructor vacío exigido por Jackson. */
    public ConclusionRequestDTO() {
    }

    /** Devuelve la conclusión. */
    public String getConclusion() {
        return conclusion;
    }

    /** Establece la conclusión. */
    public void setConclusion(String conclusion) {
        this.conclusion = conclusion;
    }
}
