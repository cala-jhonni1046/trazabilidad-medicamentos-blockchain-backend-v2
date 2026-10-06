package com.medichain.modules.usuario;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio UsuarioRepository en MediChain.
 * Acceso a datos JPA para Usuario (CRUD y paginación heredados de
 * JpaRepository por UUID), más las búsquedas que necesita la
 * autenticación: por email (login) y por rol (usuario inicial).
 */
@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    /** Devuelve una página de los usuarios de una empresa (lo que ve su administrador). */
    Page<Usuario> findByEmpresaId(UUID empresaId, Pageable pageable);

    /** Busca un usuario por su email (el email es único). */
    Optional<Usuario> findByEmail(String email);

    /** Indica si existe al menos un usuario con el rol dado. */
    boolean existsByRol(RolUsuario rol);

    /** Devuelve el primer usuario con el rol dado, si existe alguno. */
    Optional<Usuario> findFirstByRol(RolUsuario rol);

    /** Indica si ya existe un usuario con el email dado. */
    boolean existsByEmail(String email);
}
