package com.medichain.modules.usuario;

import com.medichain.modules.empresa.Empresa;
import com.medichain.utils.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Entidad Usuario en MediChain.
 * Representa la cuenta de acceso de cualquier actor del sistema: personal
 * de sede central, inspectores, personal de empresas y pacientes. Un
 * usuario puede trabajar en una Empresa (relación opcional).
 * Hereda id, fechas de auditoría y version desde BaseEntity.
 */
@Entity
@Table(name = "usuarios")
public class Usuario extends BaseEntity {

    @Column(name = "email", nullable = false, length = 150, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255, unique = false)
    private String passwordHash;

    @Column(name = "nombre", nullable = false, length = 150, unique = false)
    private String nombre;

    @Column(name = "apellido", nullable = false, length = 150, unique = false)
    private String apellido;

    @Column(name = "dni", nullable = false, length = 8, unique = false)
    private String dni;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol", nullable = false, length = 30, unique = false)
    private RolUsuario rol;

    @Column(name = "activo", nullable = false, unique = false)
    private Boolean activo;

    @Column(name = "es_admin_empresa", nullable = false, unique = false)
    private Boolean esAdminEmpresa;

    @Column(name = "es_director_tecnico", nullable = false, unique = false)
    private Boolean esDirectorTecnico;

    // nullable = true: solo tiene valor una vez que el usuario inició sesión por primera vez.
    @Column(name = "ultimo_login", nullable = true, unique = false)
    private LocalDateTime ultimoLogin;

    // nullable = true: cardinalidad "0..1", no todo usuario trabaja en una empresa (p. ej. SEDE_CENTRAL, PACIENTE).
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "empresa_id", nullable = true)
    private Empresa empresa;

    /** Constructor vacío exigido por JPA. */
    protected Usuario() {
    }

    /**
     * Constructor con los campos obligatorios para dar de alta un usuario.
     * activo nace en true; esAdminEmpresa y esDirectorTecnico nacen en
     * false; ultimoLogin y empresa quedan sin asignar.
     */
    public Usuario(String email, String passwordHash, String nombre, String apellido,
                    String dni, RolUsuario rol) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.nombre = nombre;
        this.apellido = apellido;
        this.dni = dni;
        this.rol = rol;
        this.activo = true;
        this.esAdminEmpresa = false;
        this.esDirectorTecnico = false;
    }

    /** Devuelve el email de la cuenta. */
    public String getEmail() {
        return email;
    }

    /** Establece el email de la cuenta. */
    public void setEmail(String email) {
        this.email = email;
    }

    /** Devuelve el hash de la contraseña. */
    public String getPasswordHash() {
        return passwordHash;
    }

    /** Establece el hash de la contraseña. */
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /** Devuelve el nombre del usuario. */
    public String getNombre() {
        return nombre;
    }

    /** Establece el nombre del usuario. */
    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    /** Devuelve el apellido del usuario. */
    public String getApellido() {
        return apellido;
    }

    /** Establece el apellido del usuario. */
    public void setApellido(String apellido) {
        this.apellido = apellido;
    }

    /** Devuelve el DNI del usuario. */
    public String getDni() {
        return dni;
    }

    /** Establece el DNI del usuario. */
    public void setDni(String dni) {
        this.dni = dni;
    }

    /** Devuelve el rol del usuario. */
    public RolUsuario getRol() {
        return rol;
    }

    /** Establece el rol del usuario. */
    public void setRol(RolUsuario rol) {
        this.rol = rol;
    }

    /** Indica si la cuenta está activa. */
    public Boolean getActivo() {
        return activo;
    }

    /** Establece si la cuenta está activa. */
    public void setActivo(Boolean activo) {
        this.activo = activo;
    }

    /** Indica si el usuario administra la cuenta de su empresa. */
    public Boolean getEsAdminEmpresa() {
        return esAdminEmpresa;
    }

    /** Establece si el usuario administra la cuenta de su empresa. */
    public void setEsAdminEmpresa(Boolean esAdminEmpresa) {
        this.esAdminEmpresa = esAdminEmpresa;
    }

    /** Indica si el usuario es el director técnico de su empresa. */
    public Boolean getEsDirectorTecnico() {
        return esDirectorTecnico;
    }

    /** Establece si el usuario es el director técnico de su empresa. */
    public void setEsDirectorTecnico(Boolean esDirectorTecnico) {
        this.esDirectorTecnico = esDirectorTecnico;
    }

    /** Devuelve la fecha del último inicio de sesión. */
    public LocalDateTime getUltimoLogin() {
        return ultimoLogin;
    }

    /** Establece la fecha del último inicio de sesión. */
    public void setUltimoLogin(LocalDateTime ultimoLogin) {
        this.ultimoLogin = ultimoLogin;
    }

    /** Devuelve la empresa en la que trabaja el usuario, si aplica. */
    public Empresa getEmpresa() {
        return empresa;
    }

    /** Establece la empresa en la que trabaja el usuario. */
    public void setEmpresa(Empresa empresa) {
        this.empresa = empresa;
    }
}
