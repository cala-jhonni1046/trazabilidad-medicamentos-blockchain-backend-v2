package com.medichain.modules.usuario;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO de salida UsuarioResponseDTO en MediChain.
 * Devuelve al cliente los datos del usuario. NUNCA incluye passwordHash
 * ni ningún otro dato sensible de la cuenta.
 */
public class UsuarioResponseDTO {

    private UUID id;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;
    private Long version;
    private String email;
    private String nombre;
    private String apellido;
    private String dni;
    private RolUsuario rol;
    private Boolean activo;
    private Boolean esAdminEmpresa;
    private Boolean esDirectorTecnico;
    private LocalDateTime ultimoLogin;
    private UUID empresaId;

    /** Constructor vacío exigido por Jackson. */
    public UsuarioResponseDTO() {
    }

    /** Devuelve el id del usuario. */
    public UUID getId() {
        return id;
    }

    /** Establece el id del usuario. */
    public void setId(UUID id) {
        this.id = id;
    }

    /** Devuelve la fecha de creación del registro. */
    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    /** Establece la fecha de creación del registro. */
    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    /** Devuelve la fecha de última actualización del registro. */
    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    /** Establece la fecha de última actualización del registro. */
    public void setFechaActualizacion(LocalDateTime fechaActualizacion) {
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

    /** Devuelve el email del usuario. */
    public String getEmail() {
        return email;
    }

    /** Establece el email del usuario. */
    public void setEmail(String email) {
        this.email = email;
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

    /** Devuelve si la cuenta está activa. */
    public Boolean getActivo() {
        return activo;
    }

    /** Establece si la cuenta está activa. */
    public void setActivo(Boolean activo) {
        this.activo = activo;
    }

    /** Devuelve si el usuario administra la cuenta de su empresa. */
    public Boolean getEsAdminEmpresa() {
        return esAdminEmpresa;
    }

    /** Establece si el usuario administra la cuenta de su empresa. */
    public void setEsAdminEmpresa(Boolean esAdminEmpresa) {
        this.esAdminEmpresa = esAdminEmpresa;
    }

    /** Devuelve si el usuario es el director técnico de su empresa. */
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

    /** Devuelve el id de la empresa donde trabaja el usuario. */
    public UUID getEmpresaId() {
        return empresaId;
    }

    /** Establece el id de la empresa donde trabaja el usuario. */
    public void setEmpresaId(UUID empresaId) {
        this.empresaId = empresaId;
    }
}
