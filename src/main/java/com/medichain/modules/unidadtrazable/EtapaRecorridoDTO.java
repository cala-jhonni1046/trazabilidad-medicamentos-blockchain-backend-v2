package com.medichain.modules.unidadtrazable;

import java.time.LocalDate;

/**
 * DTO de salida EtapaRecorridoDTO en MediChain.
 * Una etapa del recorrido público de una caja: qué pasó, en qué empresa
 * (razón social) y qué día. Nunca datos de dispensación ni del paciente.
 */
public class EtapaRecorridoDTO {

    private String etapa;
    private String empresa;
    private LocalDate fecha;

    /** Constructor vacío exigido por Jackson. */
    public EtapaRecorridoDTO() {
    }

    /** Crea la etapa con todos sus datos. */
    public EtapaRecorridoDTO(String etapa, String empresa, LocalDate fecha) {
        this.etapa = etapa;
        this.empresa = empresa;
        this.fecha = fecha;
    }

    /** Devuelve la etapa (FABRICADO, LIBERADO, DESPACHADO, RECIBIDO, RECHAZADO). */
    public String getEtapa() {
        return etapa;
    }

    /** Establece la etapa. */
    public void setEtapa(String etapa) {
        this.etapa = etapa;
    }

    /** Devuelve la razón social de la empresa. */
    public String getEmpresa() {
        return empresa;
    }

    /** Establece la razón social de la empresa. */
    public void setEmpresa(String empresa) {
        this.empresa = empresa;
    }

    /** Devuelve el día de la etapa. */
    public LocalDate getFecha() {
        return fecha;
    }

    /** Establece el día de la etapa. */
    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }
}
