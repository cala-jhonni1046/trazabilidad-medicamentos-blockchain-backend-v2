package com.medichain.modules.dispensacion;

import com.medichain.exceptions.ReglaNegocioException;
import com.medichain.modules.empresa.Empresa;
import com.medichain.modules.unidadtrazable.UnidadTrazable;
import com.medichain.modules.usuario.Usuario;
import com.medichain.utils.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Entidad Dispensacion en MediChain (R11, R13).
 * Entrega de UNA caja al paciente en una farmacia. Datos del paciente,
 * mínimos y solo aquí (nunca en eventos, blockchain ni verificación
 * pública): obra social y afiliado, o "particular"; número de receta; y el
 * DNI opcional SOLO enmascarado (el completo no se persiste). Una caja
 * puede tener varias dispensaciones en el tiempo si las anteriores se
 * anularon; vigente hay una sola (la caja queda DISPENSADA).
 * Hereda id, fechas de auditoría y version desde BaseEntity.
 */
@Entity
@Table(name = "dispensaciones")
public class Dispensacion extends BaseEntity {

    /** Plazo para anular una dispensación (R11). */
    public static final Duration PLAZO_ANULACION = Duration.ofHours(2);

    @Column(name = "fecha_hora", nullable = false, unique = false)
    private LocalDateTime fechaHora;

    @Column(name = "particular", nullable = false, unique = false)
    private Boolean particular;

    // nullable = true: vacío si es particular.
    @Column(name = "obra_social", nullable = true, length = 200, unique = false)
    private String obraSocial;

    // nullable = true: vacío si es particular.
    @Column(name = "numero_afiliado", nullable = true, length = 50, unique = false)
    private String numeroAfiliado;

    @Column(name = "numero_receta", nullable = false, length = 100, unique = false)
    private String numeroReceta;

    // nullable = true: el DNI es opcional; SOLO enmascarado (*****006), nunca completo (R13).
    @Column(name = "dni_enmascarado", nullable = true, length = 8, unique = false)
    private String dniEnmascarado;

    @Column(name = "anulada", nullable = false, unique = false)
    private Boolean anulada;

    // nullable = true: solo cuando se anula.
    @Column(name = "fecha_anulacion", nullable = true, unique = false)
    private LocalDateTime fechaAnulacion;

    // nullable = true: motivo de la anulación (al evento va solo su hash).
    @Column(name = "motivo_anulacion", nullable = true, columnDefinition = "TEXT")
    private String motivoAnulacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unidad_trazable_id", nullable = false, unique = false)
    private UnidadTrazable unidadTrazable;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "farmacia_id", nullable = false)
    private Empresa farmacia;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "farmaceutico_id", nullable = false)
    private Usuario farmaceutico;

    /** Constructor vacío exigido por JPA. */
    protected Dispensacion() {
    }

    /**
     * Registra la dispensación. Recibe el DNI YA enmascarado (o null): el
     * completo nunca llega a la entidad. Si es particular, obra social y
     * afiliado quedan vacíos.
     */
    public Dispensacion(UnidadTrazable unidadTrazable, Empresa farmacia, Usuario farmaceutico, boolean particular,
                        String obraSocial, String numeroAfiliado, String numeroReceta, String dniEnmascarado) {
        this.unidadTrazable = unidadTrazable;
        this.farmacia = farmacia;
        this.farmaceutico = farmaceutico;
        this.particular = particular;
        this.obraSocial = particular ? null : obraSocial;
        this.numeroAfiliado = particular ? null : numeroAfiliado;
        this.numeroReceta = numeroReceta;
        this.dniEnmascarado = dniEnmascarado;
        this.anulada = false;
        this.fechaHora = LocalDateTime.now();
    }

    /**
     * Anula la dispensación (R11): solo si no estaba anulada (si no,
     * TRANSICION_INVALIDA) y dentro de las 2 h (si no, R11).
     */
    public void anular(String motivo, LocalDateTime ahora) {
        if (Boolean.TRUE.equals(anulada)) {
            throw new ReglaNegocioException("TRANSICION_INVALIDA", "La dispensación ya estaba anulada");
        }
        if (Duration.between(fechaHora, ahora).compareTo(PLAZO_ANULACION) > 0) {
            throw new ReglaNegocioException("R11", "Solo se anula dentro de las 2 horas de la dispensación");
        }
        this.anulada = true;
        this.fechaAnulacion = ahora;
        this.motivoAnulacion = motivo;
    }

    /** Devuelve la fecha y hora de la dispensación. */
    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    /** Indica si fue particular (sin obra social). */
    public Boolean getParticular() {
        return particular;
    }

    /** Devuelve la obra social (null si particular). */
    public String getObraSocial() {
        return obraSocial;
    }

    /** Devuelve el número de afiliado (null si particular). */
    public String getNumeroAfiliado() {
        return numeroAfiliado;
    }

    /** Devuelve el número de receta. */
    public String getNumeroReceta() {
        return numeroReceta;
    }

    /** Devuelve el DNI enmascarado (*****006) o null. Nunca el completo. */
    public String getDniEnmascarado() {
        return dniEnmascarado;
    }

    /** Indica si la dispensación fue anulada. */
    public Boolean getAnulada() {
        return anulada;
    }

    /** Devuelve la fecha de anulación. */
    public LocalDateTime getFechaAnulacion() {
        return fechaAnulacion;
    }

    /** Devuelve el motivo de la anulación. */
    public String getMotivoAnulacion() {
        return motivoAnulacion;
    }

    /** Devuelve la caja dispensada. */
    public UnidadTrazable getUnidadTrazable() {
        return unidadTrazable;
    }

    /** Devuelve la farmacia. */
    public Empresa getFarmacia() {
        return farmacia;
    }

    /** Devuelve el usuario de la farmacia que dispensó. */
    public Usuario getFarmaceutico() {
        return farmaceutico;
    }
}
