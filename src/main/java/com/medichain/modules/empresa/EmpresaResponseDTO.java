package com.medichain.modules.empresa;

import com.medichain.utils.enums.Provincia;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO de salida EmpresaResponseDTO en MediChain.
 * Devuelve al cliente el estado completo de la empresa, incluidos id,
 * auditoría, version y los ids de los inspectores que la revisaron o
 * habilitaron.
 */
public class EmpresaResponseDTO {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID id;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Instant fechaCreacion;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Instant fechaActualizacion;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long version;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private TipoEmpresa tipo;
    @Schema(example = "30-71000001-4", requiredMode = Schema.RequiredMode.REQUIRED)
    private String cuit;
    @Schema(example = "Laboratorio Andino S.A.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String razonSocial;
    @Schema(nullable = true)
    private String gln;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Provincia provincia;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String localidad;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String domicilio;
    @Schema(nullable = true)
    private String numeroHabilitacion;
    @Schema(nullable = true)
    private String directorTecnico;
    @Schema(nullable = true)
    private String documentoNombre;
    @Schema(nullable = true)
    private String documentoHash;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private EstadoHabilitacion estado;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Instant fechaSolicitud;
    @Schema(nullable = true)
    private Instant fechaHabilitacion;
    @Schema(nullable = true)
    private String motivoRechazo;
    @Schema(nullable = true)
    private String motivoSuspension;
    @Schema(nullable = true)
    private UUID inspectorRevisorId;
    @Schema(nullable = true)
    private UUID inspectorHabilitadorId;

    /** Constructor vacío exigido por Jackson. */
    public EmpresaResponseDTO() {
    }

    /** Devuelve el id de la empresa. */
    public UUID getId() {
        return id;
    }

    /** Establece el id de la empresa. */
    public void setId(UUID id) {
        this.id = id;
    }

    /** Devuelve la fecha de creación del registro. */
    public Instant getFechaCreacion() {
        return fechaCreacion;
    }

    /** Establece la fecha de creación del registro. */
    public void setFechaCreacion(Instant fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    /** Devuelve la fecha de última actualización del registro. */
    public Instant getFechaActualizacion() {
        return fechaActualizacion;
    }

    /** Establece la fecha de última actualización del registro. */
    public void setFechaActualizacion(Instant fechaActualizacion) {
        this.fechaActualizacion = fechaActualizacion;
    }

    /** Devuelve la versión usada para el locking optimista. */
    public Long getVersion() {
        return version;
    }

    /** Establece la versión usada para el locking optimista. */
    public void setVersion(Long version) {
        this.version = version;
    }

    /** Devuelve el tipo de empresa. */
    public TipoEmpresa getTipo() {
        return tipo;
    }

    /** Establece el tipo de empresa. */
    public void setTipo(TipoEmpresa tipo) {
        this.tipo = tipo;
    }

    /** Devuelve el CUIT de la empresa. */
    public String getCuit() {
        return cuit;
    }

    /** Establece el CUIT de la empresa. */
    public void setCuit(String cuit) {
        this.cuit = cuit;
    }

    /** Devuelve la razón social de la empresa. */
    public String getRazonSocial() {
        return razonSocial;
    }

    /** Establece la razón social de la empresa. */
    public void setRazonSocial(String razonSocial) {
        this.razonSocial = razonSocial;
    }

    /** Devuelve el GLN de la empresa. */
    public String getGln() {
        return gln;
    }

    /** Establece el GLN de la empresa. */
    public void setGln(String gln) {
        this.gln = gln;
    }

    /** Devuelve la provincia de la empresa. */
    public Provincia getProvincia() {
        return provincia;
    }

    /** Establece la provincia de la empresa. */
    public void setProvincia(Provincia provincia) {
        this.provincia = provincia;
    }

    /** Devuelve la localidad de la empresa. */
    public String getLocalidad() {
        return localidad;
    }

    /** Establece la localidad de la empresa. */
    public void setLocalidad(String localidad) {
        this.localidad = localidad;
    }

    /** Devuelve el domicilio de la empresa. */
    public String getDomicilio() {
        return domicilio;
    }

    /** Establece el domicilio de la empresa. */
    public void setDomicilio(String domicilio) {
        this.domicilio = domicilio;
    }

    /** Devuelve el número de habilitación de la empresa. */
    public String getNumeroHabilitacion() {
        return numeroHabilitacion;
    }

    /** Establece el número de habilitación de la empresa. */
    public void setNumeroHabilitacion(String numeroHabilitacion) {
        this.numeroHabilitacion = numeroHabilitacion;
    }

    /** Devuelve el director técnico de la empresa. */
    public String getDirectorTecnico() {
        return directorTecnico;
    }

    /** Establece el director técnico de la empresa. */
    public void setDirectorTecnico(String directorTecnico) {
        this.directorTecnico = directorTecnico;
    }

    /** Devuelve el nombre del documento adjuntado. */
    public String getDocumentoNombre() {
        return documentoNombre;
    }

    /** Establece el nombre del documento adjuntado. */
    public void setDocumentoNombre(String documentoNombre) {
        this.documentoNombre = documentoNombre;
    }

    /** Devuelve el hash del documento adjuntado. */
    public String getDocumentoHash() {
        return documentoHash;
    }

    /** Establece el hash del documento adjuntado. */
    public void setDocumentoHash(String documentoHash) {
        this.documentoHash = documentoHash;
    }

    /** Devuelve el estado de habilitación. */
    public EstadoHabilitacion getEstado() {
        return estado;
    }

    /** Establece el estado de habilitación. */
    public void setEstado(EstadoHabilitacion estado) {
        this.estado = estado;
    }

    /** Devuelve la fecha de solicitud de habilitación. */
    public Instant getFechaSolicitud() {
        return fechaSolicitud;
    }

    /** Establece la fecha de solicitud de habilitación. */
    public void setFechaSolicitud(Instant fechaSolicitud) {
        this.fechaSolicitud = fechaSolicitud;
    }

    /** Devuelve la fecha de habilitación. */
    public Instant getFechaHabilitacion() {
        return fechaHabilitacion;
    }

    /** Establece la fecha de habilitación. */
    public void setFechaHabilitacion(Instant fechaHabilitacion) {
        this.fechaHabilitacion = fechaHabilitacion;
    }

    /** Devuelve el motivo de rechazo, si aplica. */
    public String getMotivoRechazo() {
        return motivoRechazo;
    }

    /** Establece el motivo de rechazo. */
    public void setMotivoRechazo(String motivoRechazo) {
        this.motivoRechazo = motivoRechazo;
    }

    /** Devuelve el id del inspector que revisa la solicitud. */
    public UUID getInspectorRevisorId() {
        return inspectorRevisorId;
    }

    /** Establece el id del inspector que revisa la solicitud. */
    public void setInspectorRevisorId(UUID inspectorRevisorId) {
        this.inspectorRevisorId = inspectorRevisorId;
    }

    /** Devuelve el id del inspector que habilitó o rechazó la solicitud. */
    public UUID getInspectorHabilitadorId() {
        return inspectorHabilitadorId;
    }

    /** Establece el id del inspector que habilitó o rechazó la solicitud. */
    public void setInspectorHabilitadorId(UUID inspectorHabilitadorId) {
        this.inspectorHabilitadorId = inspectorHabilitadorId;
    }

    /** Devuelve el motivo de la última suspensión. */
    public String getMotivoSuspension() {
        return motivoSuspension;
    }

    /** Establece el motivo de la última suspensión. */
    public void setMotivoSuspension(String motivoSuspension) {
        this.motivoSuspension = motivoSuspension;
    }
}
