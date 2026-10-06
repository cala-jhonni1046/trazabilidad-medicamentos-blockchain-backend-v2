package com.medichain.modules.despachologistico;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO de entrada DespachoLogisticoRequestDTO en MediChain (crear viaje).
 * El tramo lo decide el rol (LABORATORIO → tramo 1, DISTRIBUIDOR → tramo 2),
 * el origen y el creador salen del token, y el código VJ-0001 lo genera el
 * servidor. Los bultos se identifican por su código (lo que se escanea) y
 * quedan fijos: no se agregan ni se sacan después. Patente y chofer son
 * datos del viaje y nunca van a la cadena de eventos.
 */
public class DespachoLogisticoRequestDTO {

    @Schema(description = "Patente del camión (dato del viaje, nunca va a eventos)", example = "AB123CD",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "El campo patente es obligatorio")
    @Size(max = 10, message = "El campo patente no puede superar 10 caracteres")
    private String patente;

    @Schema(description = "Nombre del chofer (dato personal, nunca va a eventos)", example = "Juan Pérez",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "El campo chofer es obligatorio")
    @Size(max = 200, message = "El campo chofer no puede superar 200 caracteres")
    private String chofer;

    @Schema(description = "Fecha y hora estimada de entrega (futura)", example = "2026-12-01T18:00:00",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "El campo fechaEstimadaEntrega es obligatorio")
    @Future(message = "La fecha estimada de entrega debe ser futura")
    private LocalDateTime fechaEstimadaEntrega;

    @Schema(description = "Códigos de los bultos del viaje (tramo 1: ARMADO, de una misma distribuidora; tramo 2: EN_DEPOSITO en tu depósito)",
            example = "[\"BUL-0002\"]", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "El viaje debe llevar al menos un bulto")
    @Size(max = 500, message = "Un viaje no puede llevar más de 500 bultos")
    private List<String> bultos;

    /** Constructor vacío exigido por Jackson. */
    public DespachoLogisticoRequestDTO() {
    }

    /** Devuelve la patente. */
    public String getPatente() {
        return patente;
    }

    /** Establece la patente. */
    public void setPatente(String patente) {
        this.patente = patente;
    }

    /** Devuelve el chofer. */
    public String getChofer() {
        return chofer;
    }

    /** Establece el chofer. */
    public void setChofer(String chofer) {
        this.chofer = chofer;
    }

    /** Devuelve la fecha estimada de entrega. */
    public LocalDateTime getFechaEstimadaEntrega() {
        return fechaEstimadaEntrega;
    }

    /** Establece la fecha estimada de entrega. */
    public void setFechaEstimadaEntrega(LocalDateTime fechaEstimadaEntrega) {
        this.fechaEstimadaEntrega = fechaEstimadaEntrega;
    }

    /** Devuelve los códigos de los bultos. */
    public List<String> getBultos() {
        return bultos;
    }

    /** Establece los códigos de los bultos. */
    public void setBultos(List<String> bultos) {
        this.bultos = bultos;
    }
}
