package com.medichain.utils.seguridad;

import com.medichain.exceptions.ResourceNotFoundException;
import com.medichain.modules.usuario.Usuario;
import com.medichain.modules.usuario.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/**
 * Verificador VerificadorUsuario en MediChain.
 * Carga la entidad Usuario del actor (para guardarla como FK:
 * creadoPor, farmaceutico, etc.) y verifica las marcas esAdminEmpresa y
 * esDirectorTecnico contra la base de datos (no viajan en el token y
 * pueden cambiar). Una marca faltante es falta de permiso → 403.
 */
@Component
public class VerificadorUsuario {

    private final UsuarioRepository usuarioRepository;

    @Autowired
    public VerificadorUsuario(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    /** Devuelve la entidad del usuario autenticado. */
    @Transactional(readOnly = true)
    public Usuario obtener(UUID usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id: " + usuarioId));
    }

    /** Devuelve el usuario si es administrador de su empresa; si no, AccessDeniedException (403). */
    @Transactional(readOnly = true)
    public Usuario exigirAdminEmpresa(UUID usuarioId) {
        Usuario usuario = obtener(usuarioId);
        if (!Boolean.TRUE.equals(usuario.getEsAdminEmpresa())) {
            throw new AccessDeniedException("Solo el administrador de la empresa puede realizar esta acción");
        }
        return usuario;
    }

    /** Devuelve el usuario si es director técnico de su empresa; si no, AccessDeniedException (403). */
    @Transactional(readOnly = true)
    public Usuario exigirDirectorTecnico(UUID usuarioId) {
        Usuario usuario = obtener(usuarioId);
        if (!Boolean.TRUE.equals(usuario.getEsDirectorTecnico())) {
            throw new AccessDeniedException("Solo el director técnico de la empresa puede realizar esta acción");
        }
        return usuario;
    }
}
