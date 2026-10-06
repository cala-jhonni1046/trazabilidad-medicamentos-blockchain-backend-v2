package com.medichain.modules.usuario;

import com.medichain.exceptions.ReglaNegocioException;
import com.medichain.exceptions.ResourceNotFoundException;
import com.medichain.modules.auth.UsuarioAutenticado;
import com.medichain.modules.empresa.Empresa;
import com.medichain.modules.empresa.TipoEmpresa;
import com.medichain.utils.seguridad.UsuarioActual;
import com.medichain.utils.seguridad.VerificadorEmpresa;
import com.medichain.utils.seguridad.VerificadorUsuario;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

/**
 * Servicio UsuarioService en MediChain.
 * Alta y lectura de cuentas con las reglas de autorización:
 * <ul>
 *   <li>SEDE_CENTRAL ve todos los usuarios y solo crea usuarios SEDE_CENTRAL.
 *       Los inspectores se crean por /api/inspectores-anmat (R1).</li>
 *   <li>El administrador de una empresa HABILITADA (R2) ve y crea los
 *       empleados de su empresa, siempre con el rol que corresponde al
 *       tipo de la empresa (C1). La empresa sale del token, nunca del body.</li>
 *   <li>Cualquier otro usuario solo se ve a sí mismo.</li>
 * </ul>
 * La contraseña se hashea con BCrypt antes de guardar.
 */
@Service
public class UsuarioService {

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioActual usuarioActual;
    private final VerificadorEmpresa verificadorEmpresa;
    private final VerificadorUsuario verificadorUsuario;

    @Autowired
    public UsuarioService(UsuarioRepository repository, PasswordEncoder passwordEncoder,
                          UsuarioActual usuarioActual, VerificadorEmpresa verificadorEmpresa,
                          VerificadorUsuario verificadorUsuario) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.usuarioActual = usuarioActual;
        this.verificadorEmpresa = verificadorEmpresa;
        this.verificadorUsuario = verificadorUsuario;
    }

    /** Devuelve una página de usuarios según quién consulta. */
    @Transactional(readOnly = true)
    public Page<Usuario> getAll(Pageable pageable) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        if (actual.getRol() == RolUsuario.SEDE_CENTRAL) {
            return repository.findAll(pageable);
        }
        Usuario yo = verificadorUsuario.obtener(actual.getUsuarioId());
        if (esUsuarioDeEmpresa(actual.getRol()) && Boolean.TRUE.equals(yo.getEsAdminEmpresa())
                && actual.getEmpresaId() != null) {
            return repository.findByEmpresaId(actual.getEmpresaId(), pageable);
        }
        return new PageImpl<>(List.of(yo), pageable, 1);
    }

    /**
     * Busca un usuario por id. Visible para SEDE, para uno mismo y para el
     * administrador de la misma empresa; en cualquier otro caso 404.
     */
    @Transactional(readOnly = true)
    public Usuario getById(UUID id) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        Usuario usuario = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id: " + id));
        if (actual.getRol() == RolUsuario.SEDE_CENTRAL || id.equals(actual.getUsuarioId())) {
            return usuario;
        }
        boolean mismaEmpresa = usuario.getEmpresa() != null && actual.getEmpresaId() != null
                && usuario.getEmpresa().getId().equals(actual.getEmpresaId());
        if (mismaEmpresa && Boolean.TRUE.equals(verificadorUsuario.obtener(actual.getUsuarioId()).getEsAdminEmpresa())) {
            return usuario;
        }
        throw new ResourceNotFoundException("Usuario no encontrado con id: " + id);
    }

    /**
     * Persiste un nuevo usuario aplicando C1: la Sede crea solo usuarios
     * SEDE_CENTRAL; el administrador de empresa crea empleados de su
     * empresa con el rol de su tipo. Hashea la contraseña al final.
     */
    @Transactional
    public Usuario create(Usuario entity, UsuarioRequestDTO dto) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        if (dto.getRol() == RolUsuario.INSPECTOR) {
            throw new ReglaNegocioException("R1", "Los inspectores se crean únicamente por /api/inspectores-anmat");
        }
        if (actual.getRol() == RolUsuario.SEDE_CENTRAL) {
            if (dto.getRol() != RolUsuario.SEDE_CENTRAL) {
                throw new ReglaNegocioException("ROL_NO_PERMITIDO", "La Sede solo crea usuarios SEDE_CENTRAL");
            }
            // Las marcas de empresa no aplican a una cuenta de la Sede.
            entity.setEsAdminEmpresa(false);
            entity.setEsDirectorTecnico(false);
        } else if (esUsuarioDeEmpresa(actual.getRol())) {
            verificadorUsuario.exigirAdminEmpresa(actual.getUsuarioId());
            Empresa empresa = verificadorEmpresa.exigirHabilitada(actual.getEmpresaId());
            RolUsuario rolDeLaEmpresa = rolSegunTipo(empresa.getTipo());
            if (dto.getRol() != rolDeLaEmpresa) {
                throw new ReglaNegocioException("ROL_NO_PERMITIDO",
                        "El administrador solo crea empleados con rol " + rolDeLaEmpresa);
            }
            entity.setEmpresa(empresa);
        } else {
            throw new AccessDeniedException("No tenés permiso para crear usuarios");
        }
        // D6: un usuario de empresa nunca queda sin empresa.
        if (esUsuarioDeEmpresa(entity.getRol()) && entity.getEmpresa() == null) {
            throw new ReglaNegocioException("R2", "Un usuario de empresa debe pertenecer a una empresa habilitada");
        }
        entity.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        return repository.save(entity);
    }

    /**
     * Registro público de un paciente. Email repetido → 409
     * REGISTRO_NO_COMPLETADO genérico (no revela si la cuenta existe).
     * No genera evento: los usuarios no entran a la cadena.
     */
    @Transactional
    public Usuario registrarPaciente(RegistroPacienteRequestDTO dto) {
        if (repository.existsByEmail(dto.getEmail())) {
            throw new ReglaNegocioException("REGISTRO_NO_COMPLETADO", "No se pudo completar el registro con esos datos");
        }
        Usuario paciente = new Usuario(dto.getEmail(), passwordEncoder.encode(dto.getPassword()),
                dto.getNombre(), dto.getApellido(), dto.getDni(), RolUsuario.PACIENTE);
        return repository.save(paciente);
    }

    /** Indica si el rol corresponde a un empleado de empresa (LAB, DIST o FARM). */
    private boolean esUsuarioDeEmpresa(RolUsuario rol) {
        return rol == RolUsuario.LABORATORIO || rol == RolUsuario.DISTRIBUIDOR || rol == RolUsuario.FARMACIA;
    }

    /** Rol que tienen los empleados de una empresa de cada tipo. */
    private RolUsuario rolSegunTipo(TipoEmpresa tipo) {
        return switch (tipo) {
            case LABORATORIO -> RolUsuario.LABORATORIO;
            case DISTRIBUIDOR -> RolUsuario.DISTRIBUIDOR;
            case FARMACIA -> RolUsuario.FARMACIA;
        };
    }
}
