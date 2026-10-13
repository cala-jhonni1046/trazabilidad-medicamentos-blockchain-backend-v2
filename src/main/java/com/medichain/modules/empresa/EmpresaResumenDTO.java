package com.medichain.modules.empresa;

import com.medichain.utils.enums.Provincia;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * DTO de salida EmpresaResumenDTO en MediChain.
 * Datos mínimos de una empresa HABILITADA encontrada por CUIT, para que
 * el laboratorio arme un circuito. Sin documentos, inspectores ni motivos.
 */
public class EmpresaResumenDTO {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID id;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String cuit;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String razonSocial;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private TipoEmpresa tipo;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Provincia provincia;

    /** Constructor vacío exigido por Jackson. */
    public EmpresaResumenDTO() {
    }

    /** Devuelve el id de la empresa. */
    public UUID getId() {
        return id;
    }

    /** Establece el id de la empresa. */
    public void setId(UUID id) {
        this.id = id;
    }

    /** Devuelve el CUIT. */
    public String getCuit() {
        return cuit;
    }

    /** Establece el CUIT. */
    public void setCuit(String cuit) {
        this.cuit = cuit;
    }

    /** Devuelve la razón social. */
    public String getRazonSocial() {
        return razonSocial;
    }

    /** Establece la razón social. */
    public void setRazonSocial(String razonSocial) {
        this.razonSocial = razonSocial;
    }

    /** Devuelve el tipo de empresa. */
    public TipoEmpresa getTipo() {
        return tipo;
    }

    /** Establece el tipo de empresa. */
    public void setTipo(TipoEmpresa tipo) {
        this.tipo = tipo;
    }

    /** Devuelve la provincia. */
    public Provincia getProvincia() {
        return provincia;
    }

    /** Establece la provincia. */
    public void setProvincia(Provincia provincia) {
        this.provincia = provincia;
    }
}
