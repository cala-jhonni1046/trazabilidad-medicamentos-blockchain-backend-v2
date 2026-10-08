package com.medichain.modules.usuario;

import com.medichain.config.RespuestasError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

/**
 * Controlador UsuarioController en MediChain.
 * Expone el alta y la consulta de usuarios. Nunca recibe ni devuelve la
 * entidad JPA, siempre UsuarioRequestDTO/UsuarioResponseDTO (que nunca
 * incluye passwordHash).
 */
@RestController
@RequestMapping("/api/usuarios")
@Tag(name = "Usuarios", description = "Alta y consulta de cuentas de usuario")
public class UsuarioController {

    private final UsuarioService service;
    private final UsuarioMapper mapper;

    public UsuarioController(UsuarioService service, UsuarioMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    /** Lista los usuarios de forma paginada. */
    @GetMapping
    @Operation(operationId = "listarUsuarios", summary = "Listar usuarios", description = "Devuelve una página de usuarios. Roles: SEDE_CENTRAL, LABORATORIO, DISTRIBUIDOR, FARMACIA.")
    @RespuestasError({400, 401, 403})
    @PreAuthorize("hasAnyRole('SEDE_CENTRAL', 'LABORATORIO', 'DISTRIBUIDOR', 'FARMACIA')")
    public ResponseEntity<Page<UsuarioResponseDTO>> getAll(
            @ParameterObject @PageableDefault(size = 20, sort = "fechaCreacion", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<UsuarioResponseDTO> page = service.getAll(pageable).map(mapper::toResponseDTO);
        return ResponseEntity.status(HttpStatus.OK).body(page);
    }

    /** Busca un usuario por id. */
    @GetMapping("/{id}")
    @Operation(operationId = "obtenerUsuario", summary = "Obtener un usuario", description = "Busca un usuario por su id. Roles: cualquier usuario autenticado.")
    @RespuestasError({400, 401, 404})
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UsuarioResponseDTO> getById(@PathVariable UUID id) {
        UsuarioResponseDTO dto = mapper.toResponseDTO(service.getById(id));
        return ResponseEntity.status(HttpStatus.OK).body(dto);
    }

    /** Da de alta un usuario. */
    @PostMapping
    @Operation(operationId = "crearUsuario", summary = "Registrar un usuario", description = "Crea una cuenta de usuario activa. Roles: SEDE_CENTRAL, LABORATORIO, DISTRIBUIDOR, FARMACIA. Reglas: R1, R2.")
    @ApiResponse(responseCode = "201", description = "Creado")
    @RespuestasError({400, 401, 403, 409})
    @PreAuthorize("hasAnyRole('SEDE_CENTRAL', 'LABORATORIO', 'DISTRIBUIDOR', 'FARMACIA')")
    public ResponseEntity<UsuarioResponseDTO> create(@Valid @RequestBody UsuarioRequestDTO dto) {
        Usuario entity = mapper.toEntity(dto);
        Usuario created = service.create(entity, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponseDTO(created));
    }
}
