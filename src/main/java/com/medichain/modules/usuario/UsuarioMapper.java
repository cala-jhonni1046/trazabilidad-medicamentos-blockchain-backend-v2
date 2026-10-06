package com.medichain.modules.usuario;

import org.springframework.stereotype.Component;

/**
 * Mapper UsuarioMapper en MediChain.
 * Convierte entre UsuarioRequestDTO/UsuarioResponseDTO y la entidad Usuario,
 * sin lógica de negocio. El DTO de salida nunca copia passwordHash.
 * La relación con Empresa no se completa acá: el controller arma un
 * "stub" con el id y el service lo resuelve contra la base. Tampoco
 * hashea la contraseña: toEntity() deja passwordHash en null a propósito
 * (nunca copia dto.getPassword() ahí); es UsuarioService.create() quien
 * calcula passwordEncoder.encode(dto.getPassword()) antes de guardar.
 */
@Component
public class UsuarioMapper {

    /** Convierte el DTO de alta en una entidad Usuario nueva (sin passwordHash todavía). */
    public Usuario toEntity(UsuarioRequestDTO dto) {
        if (dto == null) {
            return null;
        }
        Usuario entity = new Usuario(dto.getEmail(), null, dto.getNombre(),
                dto.getApellido(), dto.getDni(), dto.getRol());
        if (dto.getEsAdminEmpresa() != null) {
            entity.setEsAdminEmpresa(dto.getEsAdminEmpresa());
        }
        if (dto.getEsDirectorTecnico() != null) {
            entity.setEsDirectorTecnico(dto.getEsDirectorTecnico());
        }
        return entity;
    }

    /** Convierte una entidad Usuario en su DTO de salida (sin passwordHash). */
    public UsuarioResponseDTO toResponseDTO(Usuario entity) {
        if (entity == null) {
            return null;
        }
        UsuarioResponseDTO dto = new UsuarioResponseDTO();
        dto.setId(entity.getId());
        dto.setFechaCreacion(entity.getFechaCreacion());
        dto.setFechaActualizacion(entity.getFechaActualizacion());
        dto.setVersion(entity.getVersion());
        dto.setEmail(entity.getEmail());
        dto.setNombre(entity.getNombre());
        dto.setApellido(entity.getApellido());
        dto.setDni(entity.getDni());
        dto.setRol(entity.getRol());
        dto.setActivo(entity.getActivo());
        dto.setEsAdminEmpresa(entity.getEsAdminEmpresa());
        dto.setEsDirectorTecnico(entity.getEsDirectorTecnico());
        dto.setUltimoLogin(entity.getUltimoLogin());
        dto.setEmpresaId(entity.getEmpresa() != null ? entity.getEmpresa().getId() : null);
        return dto;
    }
}
