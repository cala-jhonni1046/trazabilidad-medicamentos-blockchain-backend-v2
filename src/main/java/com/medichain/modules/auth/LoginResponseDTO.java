package com.medichain.modules.auth;

import com.medichain.modules.usuario.RolUsuario;
import com.medichain.utils.enums.Provincia;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO de salida LoginResponseDTO en MediChain.
 * Resultado de un login exitoso: el JWT a enviar en el header
 * "Authorization: Bearer &lt;token&gt;" y los datos del usuario que necesita el
 * frontend, para que no tenga que decodificar el token. Las marcas
 * (esAdminEmpresa, esDirectorTecnico) son informativas: el backend las vuelve
 * a verificar contra la base en cada acción.
 */
public class LoginResponseDTO {

    @Schema(description = "JWT firmado (8 h). Se envía en 'Authorization: Bearer <token>'.",
            example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIuLi4ifQ.firma", requiredMode = Schema.RequiredMode.REQUIRED)
    private String token;

    @Schema(description = "Tipo de token: siempre Bearer", example = "Bearer", requiredMode = Schema.RequiredMode.REQUIRED)
    private String tipo;

    @Schema(description = "Vencimiento del token, en UTC", example = "2026-10-09T03:15:00Z",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Instant expiraEn;

    @Schema(description = "Id del usuario autenticado", requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID usuarioId;

    @Schema(description = "Nombre del usuario", example = "Elena", requiredMode = Schema.RequiredMode.REQUIRED)
    private String nombre;

    @Schema(description = "Rol del usuario", requiredMode = Schema.RequiredMode.REQUIRED)
    private RolUsuario rol;

    @Schema(description = "Empresa del usuario; null para SEDE_CENTRAL, INSPECTOR y PACIENTE", nullable = true)
    private UUID empresaId;

    @Schema(description = "El usuario administra su empresa (crea empleados, acepta circuitos)", example = "true",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean esAdminEmpresa;

    @Schema(description = "El usuario es director técnico de su empresa (libera lotes comunes, propone circuitos)",
            example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean esDirectorTecnico;

    @Schema(description = "Provincia del inspector (la de su jurisdicción); null para los demás roles", nullable = true)
    private Provincia provincia;

    /** Constructor vacío exigido por Jackson. */
    public LoginResponseDTO() {
    }

    /** Constructor completo; tipo siempre es "Bearer". */
    public LoginResponseDTO(String token, Instant expiraEn, UUID usuarioId, String nombre, RolUsuario rol,
                            UUID empresaId, boolean esAdminEmpresa, boolean esDirectorTecnico, Provincia provincia) {
        this.token = token;
        this.tipo = "Bearer";
        this.expiraEn = expiraEn;
        this.usuarioId = usuarioId;
        this.nombre = nombre;
        this.rol = rol;
        this.empresaId = empresaId;
        this.esAdminEmpresa = esAdminEmpresa;
        this.esDirectorTecnico = esDirectorTecnico;
        this.provincia = provincia;
    }

    /** Devuelve el JWT firmado. */
    public String getToken() {
        return token;
    }

    /** Establece el JWT firmado. */
    public void setToken(String token) {
        this.token = token;
    }

    /** Devuelve el tipo de token ("Bearer"). */
    public String getTipo() {
        return tipo;
    }

    /** Establece el tipo de token. */
    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    /** Devuelve el instante (UTC) en que vence el token. */
    public Instant getExpiraEn() {
        return expiraEn;
    }

    /** Establece el instante (UTC) en que vence el token. */
    public void setExpiraEn(Instant expiraEn) {
        this.expiraEn = expiraEn;
    }

    /** Devuelve el id del usuario autenticado. */
    public UUID getUsuarioId() {
        return usuarioId;
    }

    /** Establece el id del usuario autenticado. */
    public void setUsuarioId(UUID usuarioId) {
        this.usuarioId = usuarioId;
    }

    /** Devuelve el nombre del usuario. */
    public String getNombre() {
        return nombre;
    }

    /** Establece el nombre del usuario. */
    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    /** Devuelve el rol del usuario. */
    public RolUsuario getRol() {
        return rol;
    }

    /** Establece el rol del usuario. */
    public void setRol(RolUsuario rol) {
        this.rol = rol;
    }

    /** Devuelve la empresa del usuario (null si no tiene). */
    public UUID getEmpresaId() {
        return empresaId;
    }

    /** Establece la empresa del usuario. */
    public void setEmpresaId(UUID empresaId) {
        this.empresaId = empresaId;
    }

    /** Indica si el usuario administra su empresa. */
    public boolean isEsAdminEmpresa() {
        return esAdminEmpresa;
    }

    /** Establece si el usuario administra su empresa. */
    public void setEsAdminEmpresa(boolean esAdminEmpresa) {
        this.esAdminEmpresa = esAdminEmpresa;
    }

    /** Indica si el usuario es director técnico de su empresa. */
    public boolean isEsDirectorTecnico() {
        return esDirectorTecnico;
    }

    /** Establece si el usuario es director técnico de su empresa. */
    public void setEsDirectorTecnico(boolean esDirectorTecnico) {
        this.esDirectorTecnico = esDirectorTecnico;
    }

    /** Devuelve la provincia del inspector (null para los demás roles). */
    public Provincia getProvincia() {
        return provincia;
    }

    /** Establece la provincia del inspector. */
    public void setProvincia(Provincia provincia) {
        this.provincia = provincia;
    }
}
