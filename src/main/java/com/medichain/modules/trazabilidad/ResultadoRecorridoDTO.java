package com.medichain.modules.trazabilidad;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * DTO de salida ResultadoRecorridoDTO en MediChain.
 * Una coincidencia de la búsqueda por código con sus eventos: del lote, del bulto o de la caja
 * y de lo que lo rodea (ver TrazabilidadPorCodigoService). Siempre con el laboratorio, porque un
 * código de lote puede repetirse entre laboratorios.
 */
public class ResultadoRecorridoDTO {

    @Schema(description = "Qué se encontró: LOTE, BULTO o CAJA", requiredMode = Schema.RequiredMode.REQUIRED)
    private TipoCodigoBuscado tipo;
    @Schema(description = "Código del lote, código del bulto o serie de la caja", example = "L2026-0001", requiredMode = Schema.RequiredMode.REQUIRED)
    private String codigo;
    @Schema(description = "Id de lo encontrado (lote, bulto o caja)", requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID entidadId;
    @Schema(description = "Código del lote (el mismo lote si se buscó un lote)", example = "L2026-0001", requiredMode = Schema.RequiredMode.REQUIRED)
    private String loteCodigo;
    @Schema(description = "Nombre comercial del medicamento", example = "Cuyafen", requiredMode = Schema.RequiredMode.REQUIRED)
    private String medicamentoNombre;
    @Schema(description = "Razón social del laboratorio del lote (un código de lote puede repetirse entre laboratorios)", requiredMode = Schema.RequiredMode.REQUIRED)
    private String laboratorioRazonSocial;
    @Schema(description = "Eventos de lo encontrado y de lo que lo rodea, del más viejo al más nuevo", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<EventoTrazabilidadResponseDTO> eventos;
    @Schema(description = "true si había más eventos que el máximo devuelto (1000)", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean truncado;

    /** Constructor vacío exigido por Jackson. */
    public ResultadoRecorridoDTO() {
    }

    /** Devuelve qué se encontró: LOTE, BULTO o CAJA. */
    public TipoCodigoBuscado getTipo() {
        return tipo;
    }

    /** Establece qué se encontró: LOTE, BULTO o CAJA. */
    public void setTipo(TipoCodigoBuscado tipo) {
        this.tipo = tipo;
    }

    /** Devuelve código del lote, código del bulto o serie de la caja. */
    public String getCodigo() {
        return codigo;
    }

    /** Establece código del lote, código del bulto o serie de la caja. */
    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    /** Devuelve id de lo encontrado (lote, bulto o caja). */
    public UUID getEntidadId() {
        return entidadId;
    }

    /** Establece id de lo encontrado (lote, bulto o caja). */
    public void setEntidadId(UUID entidadId) {
        this.entidadId = entidadId;
    }

    /** Devuelve código del lote (el mismo lote si se buscó un lote). */
    public String getLoteCodigo() {
        return loteCodigo;
    }

    /** Establece código del lote (el mismo lote si se buscó un lote). */
    public void setLoteCodigo(String loteCodigo) {
        this.loteCodigo = loteCodigo;
    }

    /** Devuelve nombre comercial del medicamento. */
    public String getMedicamentoNombre() {
        return medicamentoNombre;
    }

    /** Establece nombre comercial del medicamento. */
    public void setMedicamentoNombre(String medicamentoNombre) {
        this.medicamentoNombre = medicamentoNombre;
    }

    /** Devuelve razón social del laboratorio del lote (un código de lote puede repetirse entre laboratorios). */
    public String getLaboratorioRazonSocial() {
        return laboratorioRazonSocial;
    }

    /** Establece razón social del laboratorio del lote (un código de lote puede repetirse entre laboratorios). */
    public void setLaboratorioRazonSocial(String laboratorioRazonSocial) {
        this.laboratorioRazonSocial = laboratorioRazonSocial;
    }

    /** Devuelve eventos de lo encontrado y de lo que lo rodea, del más viejo al más nuevo. */
    public List<EventoTrazabilidadResponseDTO> getEventos() {
        return eventos;
    }

    /** Establece eventos de lo encontrado y de lo que lo rodea, del más viejo al más nuevo. */
    public void setEventos(List<EventoTrazabilidadResponseDTO> eventos) {
        this.eventos = eventos;
    }

    /** Devuelve true si había más eventos que el máximo devuelto (1000). */
    public Boolean getTruncado() {
        return truncado;
    }

    /** Establece true si había más eventos que el máximo devuelto (1000). */
    public void setTruncado(Boolean truncado) {
        this.truncado = truncado;
    }
}
