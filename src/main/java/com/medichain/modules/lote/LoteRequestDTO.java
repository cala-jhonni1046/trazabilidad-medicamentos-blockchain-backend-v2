package com.medichain.modules.lote;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * DTO de entrada LoteRequestDTO en MediChain.
 * Registro de un lote con TODAS sus cajas en una sola operación. Las series
 * llegan de una de dos formas (exactamente una): la lista explícita que el
 * laboratorio imprimió en el DataMatrix, o la cantidad para que el servidor
 * las genere (código del lote sin guiones + "S" + 6 dígitos). Máximo 10.000
 * cajas por lote. El formato de cada serie (R3) se valida en el Service,
 * para poder registrar INTENTO_SERIE_INVALIDA.
 */
@LoteRequestValido
public class LoteRequestDTO {

    /** Máximo de cajas por lote. */
    public static final int MAXIMO_CAJAS = 10_000;

    @Schema(description = "Código del lote que escribe el laboratorio: hasta 12 caracteres, letras, dígitos o guion. Único por laboratorio.",
            example = "L2026-0003", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "El campo codigo es obligatorio")
    @Pattern(regexp = "[A-Za-z0-9-]{1,12}", message = "El código del lote admite hasta 12 letras, dígitos o guiones")
    private String codigo;

    @Schema(description = "Fecha de fabricación (hoy o anterior)", example = "2026-09-01",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "El campo fechaFabricacion es obligatorio")
    @PastOrPresent(message = "La fecha de fabricación no puede ser futura")
    private LocalDate fechaFabricacion;

    @Schema(description = "Fecha de vencimiento (futura y posterior a la fabricación)", example = "2028-09-01",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "El campo fechaVencimiento es obligatorio")
    @Future(message = "La fecha de vencimiento debe ser futura")
    private LocalDate fechaVencimiento;

    @Schema(description = "Medicamento de TU laboratorio", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "El campo medicamentoId es obligatorio")
    private UUID medicamentoId;

    @Schema(description = "Forma A: lista explícita de series (alfanuméricas, hasta 20, no empiezan con 779, únicas por GTIN). No enviar junto con cantidad.",
            example = "[\"ABC000001\", \"ABC000002\"]")
    @Size(min = 1, max = MAXIMO_CAJAS, message = "La lista de series debe tener entre 1 y 10000 elementos")
    private List<String> series;

    @Schema(description = "Forma B: cantidad de cajas para que el servidor genere las series (L2026-0003 → L20260003S000001…). No enviar junto con series.",
            example = "20")
    @Min(value = 1, message = "La cantidad debe ser al menos 1")
    @Max(value = MAXIMO_CAJAS, message = "La cantidad no puede superar 10000 cajas por lote")
    private Integer cantidad;

    /** Constructor vacío exigido por Jackson. */
    public LoteRequestDTO() {
    }

    /** Devuelve el código del lote. */
    public String getCodigo() {
        return codigo;
    }

    /** Establece el código del lote. */
    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    /** Devuelve la fecha de fabricación. */
    public LocalDate getFechaFabricacion() {
        return fechaFabricacion;
    }

    /** Establece la fecha de fabricación. */
    public void setFechaFabricacion(LocalDate fechaFabricacion) {
        this.fechaFabricacion = fechaFabricacion;
    }

    /** Devuelve la fecha de vencimiento. */
    public LocalDate getFechaVencimiento() {
        return fechaVencimiento;
    }

    /** Establece la fecha de vencimiento. */
    public void setFechaVencimiento(LocalDate fechaVencimiento) {
        this.fechaVencimiento = fechaVencimiento;
    }

    /** Devuelve el id del medicamento. */
    public UUID getMedicamentoId() {
        return medicamentoId;
    }

    /** Establece el id del medicamento. */
    public void setMedicamentoId(UUID medicamentoId) {
        this.medicamentoId = medicamentoId;
    }

    /** Devuelve la lista explícita de series (forma A). */
    public List<String> getSeries() {
        return series;
    }

    /** Establece la lista explícita de series (forma A). */
    public void setSeries(List<String> series) {
        this.series = series;
    }

    /** Devuelve la cantidad de series a generar (forma B). */
    public Integer getCantidad() {
        return cantidad;
    }

    /** Establece la cantidad de series a generar (forma B). */
    public void setCantidad(Integer cantidad) {
        this.cantidad = cantidad;
    }
}
