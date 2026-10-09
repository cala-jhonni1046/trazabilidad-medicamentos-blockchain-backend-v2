package com.medichain.modules.recepcion;

import com.medichain.modules.bulto.Bulto;
import com.medichain.modules.despachologistico.DespachoLogistico;
import com.medichain.modules.empresa.Empresa;
import com.medichain.modules.usuario.Usuario;
import com.medichain.utils.BaseEntity;
import com.medichain.utils.Tiempo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Entidad Recepcion en MediChain (acta de recepción, R8).
 * Registro atómico de la llegada de un bulto a la empresa destino: lo
 * verificado (precinto, cantidad, temperatura de llegada), si fue conforme
 * y, si no, los motivos (códigos fijos calculados por el servidor). Se
 * crea entera y no cambia después: no tiene setters.
 * Hereda id, fechas de auditoría y version desde BaseEntity.
 */
@Entity
@Table(name = "recepciones")
public class Recepcion extends BaseEntity {

    @Column(name = "fecha_hora", nullable = false, unique = false)
    private Instant fechaHora;

    @Column(name = "temperatura", nullable = false, precision = 5, scale = 2, unique = false)
    private BigDecimal temperatura;

    @Column(name = "precinto_intacto", nullable = false, unique = false)
    private Boolean precintoIntacto;

    @Column(name = "cantidad_verificada", nullable = false, unique = false)
    private Integer cantidadVerificada;

    @Column(name = "conforme", nullable = false, unique = false)
    private Boolean conforme;

    // nullable = true: solo si no fue conforme; códigos MotivoRechazoRecepcion separados por coma.
    @Column(name = "motivo_rechazo", nullable = true, length = 200, unique = false)
    private String motivoRechazo;

    // nullable = true: observación libre de quien recibe; NUNCA va a un evento.
    @Column(name = "observacion", nullable = true, columnDefinition = "TEXT")
    private String observacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bulto_id", nullable = false)
    private Bulto bulto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "despacho_id", nullable = false)
    private DespachoLogistico despacho;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receptora_id", nullable = false)
    private Empresa receptora;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registrada_por_id", nullable = false)
    private Usuario registradaPor;

    /** Constructor vacío exigido por JPA. */
    protected Recepcion() {
    }

    /**
     * Crea el acta completa. conforme = sin motivos de rechazo. La fecha la
     * fija el servidor en el momento de la recepción.
     */
    public Recepcion(Bulto bulto, DespachoLogistico despacho, Empresa receptora, Usuario registradaPor,
                     BigDecimal temperatura, Boolean precintoIntacto, Integer cantidadVerificada,
                     List<MotivoRechazoRecepcion> motivos, String observacion) {
        this.bulto = bulto;
        this.despacho = despacho;
        this.receptora = receptora;
        this.registradaPor = registradaPor;
        this.temperatura = temperatura;
        this.precintoIntacto = precintoIntacto;
        this.cantidadVerificada = cantidadVerificada;
        this.conforme = motivos.isEmpty();
        this.motivoRechazo = motivos.isEmpty() ? null
                : motivos.stream().map(Enum::name).collect(Collectors.joining(","));
        this.observacion = observacion;
        this.fechaHora = Tiempo.ahora();
    }

    /** Devuelve los motivos de rechazo como lista (vacía si fue conforme). */
    public List<MotivoRechazoRecepcion> motivos() {
        if (motivoRechazo == null || motivoRechazo.isBlank()) {
            return new ArrayList<>();
        }
        return Arrays.stream(motivoRechazo.split(",")).map(MotivoRechazoRecepcion::valueOf)
                .collect(Collectors.toList());
    }

    /** Devuelve la fecha y hora de la recepción. */
    public Instant getFechaHora() {
        return fechaHora;
    }

    /** Devuelve la temperatura de llegada. */
    public BigDecimal getTemperatura() {
        return temperatura;
    }

    /** Indica si el precinto llegó intacto. */
    public Boolean getPrecintoIntacto() {
        return precintoIntacto;
    }

    /** Devuelve la cantidad de cajas verificada. */
    public Integer getCantidadVerificada() {
        return cantidadVerificada;
    }

    /** Indica si la recepción fue conforme. */
    public Boolean getConforme() {
        return conforme;
    }

    /** Devuelve los motivos de rechazo (códigos separados por coma) o null. */
    public String getMotivoRechazo() {
        return motivoRechazo;
    }

    /** Devuelve la observación libre (nunca va a eventos). */
    public String getObservacion() {
        return observacion;
    }

    /** Devuelve el bulto recibido. */
    public Bulto getBulto() {
        return bulto;
    }

    /** Devuelve el viaje en el que llegó. */
    public DespachoLogistico getDespacho() {
        return despacho;
    }

    /** Devuelve la empresa receptora. */
    public Empresa getReceptora() {
        return receptora;
    }

    /** Devuelve el usuario que registró la recepción. */
    public Usuario getRegistradaPor() {
        return registradaPor;
    }
}
