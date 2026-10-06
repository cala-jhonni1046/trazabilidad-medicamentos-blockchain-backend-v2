package com.medichain.modules.enlacecuit;

import com.medichain.utils.validacion.Cuit;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO de entrada EnlaceCuitRequestDTO en MediChain.
 * Propuesta de circuito: el DT del laboratorio identifica a la
 * distribuidora y a la farmacia por CUIT (con o sin guiones). El
 * laboratorio y el proponente salen del token; el código CIR-0001, el
 * estado y la fecha los fija el servidor.
 */
public class EnlaceCuitRequestDTO {

    @Schema(description = "CUIT de la distribuidora (HABILITADA), con o sin guiones", example = "30-71000002-2",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "El campo cuitDistribuidor es obligatorio")
    @Cuit
    private String cuitDistribuidor;

    @Schema(description = "CUIT de la farmacia (HABILITADA), con o sin guiones", example = "30-71000004-9",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "El campo cuitFarmacia es obligatorio")
    @Cuit
    private String cuitFarmacia;

    /** Constructor vacío exigido por Jackson. */
    public EnlaceCuitRequestDTO() {
    }

    /** Devuelve el CUIT de la distribuidora. */
    public String getCuitDistribuidor() {
        return cuitDistribuidor;
    }

    /** Establece el CUIT de la distribuidora. */
    public void setCuitDistribuidor(String cuitDistribuidor) {
        this.cuitDistribuidor = cuitDistribuidor;
    }

    /** Devuelve el CUIT de la farmacia. */
    public String getCuitFarmacia() {
        return cuitFarmacia;
    }

    /** Establece el CUIT de la farmacia. */
    public void setCuitFarmacia(String cuitFarmacia) {
        this.cuitFarmacia = cuitFarmacia;
    }
}
