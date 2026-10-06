package com.medichain.modules.empresa;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO de entrada MotivoRequestDTO en MediChain.
 * Motivo obligatorio de una acción (rechazar o suspender una empresa).
 * El texto se guarda en la empresa; al evento solo va su SHA-256.
 */
public class MotivoRequestDTO {

    @NotBlank(message = "El motivo es obligatorio")
    @Size(max = 1000, message = "El motivo no puede superar 1000 caracteres")
    private String motivo;

    /** Constructor vacío exigido por Spring/Jackson. */
    public MotivoRequestDTO() {
    }

    /** Devuelve el motivo. */
    public String getMotivo() {
        return motivo;
    }

    /** Establece el motivo. */
    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }
}
