package com.medichain.modules.trazabilidad;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * DTO de salida RecorridoCodigoResponseDTO en MediChain.
 * Resultado de la búsqueda de trazabilidad por código: una coincidencia por cada lote, bulto o caja
 * que el usuario puede ver (varias solo si un código de lote se repite entre laboratorios).
 */
public class RecorridoCodigoResponseDTO {

    @Schema(description = "Coincidencias dentro de lo que el usuario puede ver (al menos una; si no, 404)", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<ResultadoRecorridoDTO> resultados;

    /** Constructor vacío exigido por Jackson. */
    public RecorridoCodigoResponseDTO() {
    }

    /** Devuelve las coincidencias. */
    public List<ResultadoRecorridoDTO> getResultados() {
        return resultados;
    }

    /** Establece las coincidencias. */
    public void setResultados(List<ResultadoRecorridoDTO> resultados) {
        this.resultados = resultados;
    }
}
