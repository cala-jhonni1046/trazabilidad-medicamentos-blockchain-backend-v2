package com.medichain.modules.cuarentena;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO de entrada DictamenRequestDTO en MediChain (R12).
 * Fundamento obligatorio del inspector al levantar una medida o convertirla
 * en recall. Se guarda en la medida; al evento va solo su hash.
 */
public class DictamenRequestDTO {

    @Schema(description = "Fundamento del dictamen", example = "Análisis de laboratorio conforme", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "El fundamento es obligatorio")
    @Size(max = 2000, message = "El fundamento no puede superar 2000 caracteres")
    private String fundamento;

    /** Constructor vacío exigido por Jackson. */
    public DictamenRequestDTO() {
    }

    /** Devuelve el fundamento del dictamen. */
    public String getFundamento() {
        return fundamento;
    }

    /** Establece el fundamento del dictamen. */
    public void setFundamento(String fundamento) {
        this.fundamento = fundamento;
    }
}
