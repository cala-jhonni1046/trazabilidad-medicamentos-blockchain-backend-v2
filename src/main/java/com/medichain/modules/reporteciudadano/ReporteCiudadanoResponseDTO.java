package com.medichain.modules.reporteciudadano;

import com.medichain.utils.enums.Provincia;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO de salida ReporteCiudadanoResponseDTO en MediChain.
 * Para el PACIENTE: su reporte, estado y fechas (sin la conclusión ni datos
 * internos de la investigación). Para INSPECTOR y SEDE además: conclusión,
 * inspector y la provincia donde está la caja real (si no coincide con la
 * declarada, es una señal de posible falsificación).
 */
public class ReporteCiudadanoResponseDTO {

    private UUID id;

    private String codigo;

    private String gtinReportado;

    private String serieReportada;

    private MotivoReporte motivo;

    private String descripcion;

    private EstadoAuditoria estado;

    private Provincia provincia;

    private LocalDateTime fechaReporte;

    private LocalDateTime fechaCierre;

    private Boolean cajaExiste;

    private String conclusion;

    private UUID investigaId;

    private Provincia provinciaCaja;

    /** Constructor vacío exigido por Jackson. */
    public ReporteCiudadanoResponseDTO() {
    }

    /** Devuelve el id del reporte. */
    public UUID getId() {
        return id;
    }

    /** Establece el id del reporte. */
    public void setId(UUID id) {
        this.id = id;
    }

    /** Devuelve el código. */
    public String getCodigo() {
        return codigo;
    }

    /** Establece el código. */
    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    /** Devuelve el GTIN reportado. */
    public String getGtinReportado() {
        return gtinReportado;
    }

    /** Establece el GTIN reportado. */
    public void setGtinReportado(String gtinReportado) {
        this.gtinReportado = gtinReportado;
    }

    /** Devuelve la serie reportada. */
    public String getSerieReportada() {
        return serieReportada;
    }

    /** Establece la serie reportada. */
    public void setSerieReportada(String serieReportada) {
        this.serieReportada = serieReportada;
    }

    /** Devuelve el motivo. */
    public MotivoReporte getMotivo() {
        return motivo;
    }

    /** Establece el motivo. */
    public void setMotivo(MotivoReporte motivo) {
        this.motivo = motivo;
    }

    /** Devuelve la descripción libre. */
    public String getDescripcion() {
        return descripcion;
    }

    /** Establece la descripción libre. */
    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    /** Devuelve el estado. */
    public EstadoAuditoria getEstado() {
        return estado;
    }

    /** Establece el estado. */
    public void setEstado(EstadoAuditoria estado) {
        this.estado = estado;
    }

    /** Devuelve la provincia declarada. */
    public Provincia getProvincia() {
        return provincia;
    }

    /** Establece la provincia declarada. */
    public void setProvincia(Provincia provincia) {
        this.provincia = provincia;
    }

    /** Devuelve la fecha del reporte. */
    public LocalDateTime getFechaReporte() {
        return fechaReporte;
    }

    /** Establece la fecha del reporte. */
    public void setFechaReporte(LocalDateTime fechaReporte) {
        this.fechaReporte = fechaReporte;
    }

    /** Devuelve la fecha de cierre. */
    public LocalDateTime getFechaCierre() {
        return fechaCierre;
    }

    /** Establece la fecha de cierre. */
    public void setFechaCierre(LocalDateTime fechaCierre) {
        this.fechaCierre = fechaCierre;
    }

    /** Devuelve si GTIN + serie corresponden a una caja registrada. */
    public Boolean getCajaExiste() {
        return cajaExiste;
    }

    /** Establece si GTIN + serie corresponden a una caja registrada. */
    public void setCajaExiste(Boolean cajaExiste) {
        this.cajaExiste = cajaExiste;
    }

    /** Devuelve la conclusión del inspector (solo inspectores y Sede). */
    public String getConclusion() {
        return conclusion;
    }

    /** Establece la conclusión del inspector (solo inspectores y Sede). */
    public void setConclusion(String conclusion) {
        this.conclusion = conclusion;
    }

    /** Devuelve el inspector que investiga (solo inspectores y Sede). */
    public UUID getInvestigaId() {
        return investigaId;
    }

    /** Establece el inspector que investiga (solo inspectores y Sede). */
    public void setInvestigaId(UUID investigaId) {
        this.investigaId = investigaId;
    }

    /** Devuelve la provincia de la empresa que tiene la caja real (solo inspectores y Sede). */
    public Provincia getProvinciaCaja() {
        return provinciaCaja;
    }

    /** Establece la provincia de la empresa que tiene la caja real (solo inspectores y Sede). */
    public void setProvinciaCaja(Provincia provinciaCaja) {
        this.provinciaCaja = provinciaCaja;
    }
}
