package com.medichain.modules.inspectoranmat;

import com.medichain.exceptions.ReglaNegocioException;
import com.medichain.modules.usuario.Usuario;
import com.medichain.utils.BaseEntity;
import com.medichain.utils.enums.Provincia;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Entidad InspectorAnmat en MediChain.
 * Representa a un inspector de ANMAT, que revisa y habilita empresas,
 * aprueba enlaces de CUIT y dictamina cuarentenas. Cada inspector tiene una
 * única cuenta de Usuario asociada y fue dado de alta por otro Usuario de
 * sede central.
 * Hereda id, fechas de auditoría y version desde BaseEntity.
 */
@Entity
@Table(name = "inspectores_anmat")
public class InspectorAnmat extends BaseEntity {

    @Column(name = "legajo", nullable = false, length = 20, unique = true)
    private String legajo;

    @Column(name = "dni", nullable = false, length = 8, unique = true)
    private String dni;

    @Column(name = "provincia", nullable = false, length = 30, unique = false)
    private Provincia provincia;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 30, unique = false)
    private EstadoInspector estado;

    @Column(name = "fecha_alta", nullable = false, unique = false)
    private LocalDateTime fechaAlta;

    // nullable = true: solo tiene valor una vez que el inspector es dado de baja.
    @Column(name = "fecha_baja", nullable = true, unique = false)
    private LocalDateTime fechaBaja;

    // cardinalidad "1"-"1": la cuenta propia del inspector; obligatoria y única.
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private Usuario usuario;

    // cardinalidad "0..*"-"1": el usuario de sede central que dio de alta a este inspector.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_alta_id", nullable = false)
    private Usuario usuarioAlta;

    /** Constructor vacío exigido por JPA. */
    protected InspectorAnmat() {
    }

    /**
     * Constructor con los campos escalares obligatorios para dar de alta un
     * inspector. El estado inicial siempre es ACTIVO y la fecha de alta se
     * fija en el momento de la creación. Las relaciones obligatorias con
     * Usuario (usuario y usuarioAlta) se completan después, vía setter,
     * porque el service las resuelve contra la base a partir de los ids
     * que llegan en el DTO — así ninguna clase de otro paquete necesita
     * instanciar Usuario directamente.
     */
    public InspectorAnmat(String legajo, String dni, Provincia provincia) {
        this.legajo = legajo;
        this.dni = dni;
        this.provincia = provincia;
        this.estado = EstadoInspector.ACTIVO;
        this.fechaAlta = LocalDateTime.now();
    }

    /** Devuelve el legajo del inspector. */
    public String getLegajo() {
        return legajo;
    }

    /** Establece el legajo del inspector. */
    public void setLegajo(String legajo) {
        this.legajo = legajo;
    }

    /** Devuelve el DNI del inspector. */
    public String getDni() {
        return dni;
    }

    /** Establece el DNI del inspector. */
    public void setDni(String dni) {
        this.dni = dni;
    }

    /** Devuelve la provincia donde actúa el inspector. */
    public Provincia getProvincia() {
        return provincia;
    }

    /** Establece la provincia donde actúa el inspector. */
    public void setProvincia(Provincia provincia) {
        this.provincia = provincia;
    }

    /** Devuelve el estado administrativo del inspector. */
    public EstadoInspector getEstado() {
        return estado;
    }

    /** Establece el estado administrativo del inspector. */
    public void setEstado(EstadoInspector estado) {
        this.estado = estado;
    }

    /** Devuelve la fecha de alta del inspector. */
    public LocalDateTime getFechaAlta() {
        return fechaAlta;
    }

    /** Establece la fecha de alta del inspector. */
    public void setFechaAlta(LocalDateTime fechaAlta) {
        this.fechaAlta = fechaAlta;
    }

    /** Devuelve la fecha de baja del inspector, si aplica. */
    public LocalDateTime getFechaBaja() {
        return fechaBaja;
    }

    /** Establece la fecha de baja del inspector. */
    public void setFechaBaja(LocalDateTime fechaBaja) {
        this.fechaBaja = fechaBaja;
    }

    /** Devuelve la cuenta de usuario propia del inspector. */
    public Usuario getUsuario() {
        return usuario;
    }

    /** Establece la cuenta de usuario propia del inspector. */
    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    /** Devuelve el usuario de sede central que dio de alta a este inspector. */
    public Usuario getUsuarioAlta() {
        return usuarioAlta;
    }

    /** Establece el usuario de sede central que dio de alta a este inspector. */
    public void setUsuarioAlta(Usuario usuarioAlta) {
        this.usuarioAlta = usuarioAlta;
    }

    /** Indica si el inspector está actualmente activo. */
    public boolean estaActivo() {
        return this.estado == EstadoInspector.ACTIVO;
    }

    /** Da de baja al inspector: ACTIVO → BAJA, con fecha de baja. */
    public void darDeBaja() {
        if (this.estado != EstadoInspector.ACTIVO) {
            throw new ReglaNegocioException("TRANSICION_INVALIDA",
                    "Un inspector en estado " + this.estado + " no admite la acción baja");
        }
        this.estado = EstadoInspector.BAJA;
        this.fechaBaja = LocalDateTime.now();
    }

    /** Reactiva al inspector: BAJA → ACTIVO, borra la fecha de baja. */
    public void reactivar() {
        if (this.estado != EstadoInspector.BAJA) {
            throw new ReglaNegocioException("TRANSICION_INVALIDA",
                    "Un inspector en estado " + this.estado + " no admite la acción reactivar");
        }
        this.estado = EstadoInspector.ACTIVO;
        this.fechaBaja = null;
    }
}
