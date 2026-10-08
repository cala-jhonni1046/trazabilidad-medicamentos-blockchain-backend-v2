package com.medichain.modules.inspectoranmat;

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
 * Controlador InspectorAnmatController en MediChain.
 * Expone el alta y la consulta de inspectores ANMAT. Nunca recibe ni
 * devuelve la entidad JPA, siempre InspectorAnmatRequestDTO/ResponseDTO.
 */
@RestController
@RequestMapping("/api/inspectores-anmat")
@Tag(name = "Inspectores ANMAT", description = "Alta y consulta de inspectores ANMAT")
public class InspectorAnmatController {

    private final InspectorAnmatService service;
    private final InspectorAnmatMapper mapper;

    public InspectorAnmatController(InspectorAnmatService service, InspectorAnmatMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    /** Lista los inspectores de forma paginada. */
    @GetMapping
    @Operation(operationId = "listarInspectores", summary = "Listar inspectores", description = "Devuelve una página de inspectores ANMAT. Roles: SEDE_CENTRAL, INSPECTOR.")
    @RespuestasError({400, 401, 403})
    @PreAuthorize("hasAnyRole('SEDE_CENTRAL', 'INSPECTOR')")
    public ResponseEntity<Page<InspectorAnmatResponseDTO>> getAll(
            @ParameterObject @PageableDefault(size = 20, sort = "fechaCreacion", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<InspectorAnmatResponseDTO> page = service.getAll(pageable).map(mapper::toResponseDTO);
        return ResponseEntity.status(HttpStatus.OK).body(page);
    }

    /** Busca un inspector por id. */
    @GetMapping("/{id}")
    @Operation(operationId = "obtenerInspector", summary = "Obtener un inspector", description = "Busca un inspector ANMAT por su id. Roles: SEDE_CENTRAL, INSPECTOR.")
    @RespuestasError({400, 401, 403, 404})
    @PreAuthorize("hasAnyRole('SEDE_CENTRAL', 'INSPECTOR')")
    public ResponseEntity<InspectorAnmatResponseDTO> getById(@PathVariable UUID id) {
        InspectorAnmatResponseDTO dto = mapper.toResponseDTO(service.getById(id));
        return ResponseEntity.status(HttpStatus.OK).body(dto);
    }

    /** Da de alta un inspector ANMAT. */
    @PostMapping
    @Operation(operationId = "crearInspector", summary = "Registrar un inspector", description = "Crea un inspector ANMAT en estado ACTIVO. Roles: SEDE_CENTRAL. Reglas: R1.")
    @ApiResponse(responseCode = "201", description = "Creado")
    @RespuestasError({400, 401, 403, 409})
    @PreAuthorize("hasAnyRole('SEDE_CENTRAL')")
    public ResponseEntity<InspectorAnmatResponseDTO> create(@Valid @RequestBody InspectorAnmatRequestDTO dto) {
        InspectorAnmat entity = mapper.toEntity(dto);
        InspectorAnmat created = service.create(entity, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponseDTO(created));
    }

    /** Da de baja a un inspector (R1). */
    @PostMapping("/{id}/baja")
    @Operation(operationId = "darDeBajaInspector", summary = "Dar de baja un inspector", description = "ACTIVO → BAJA: su cuenta queda inactiva y sus solicitudes tomadas vuelven a la bandeja. Evento BAJA_INSPECTOR. Roles: SEDE_CENTRAL. Reglas: R1.")
    @RespuestasError({400, 401, 403, 404, 409})
    @PreAuthorize("hasRole('SEDE_CENTRAL')")
    public ResponseEntity<InspectorAnmatResponseDTO> darDeBaja(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.OK).body(mapper.toResponseDTO(service.darDeBaja(id)));
    }

    /** Reactiva a un inspector dado de baja (R1). */
    @PostMapping("/{id}/reactivar")
    @Operation(operationId = "reactivarInspector", summary = "Reactivar un inspector", description = "BAJA → ACTIVO y su cuenta vuelve a estar activa (no recupera solicitudes). Evento REACTIVACION_INSPECTOR. Roles: SEDE_CENTRAL. Reglas: R1.")
    @RespuestasError({400, 401, 403, 404, 409})
    @PreAuthorize("hasRole('SEDE_CENTRAL')")
    public ResponseEntity<InspectorAnmatResponseDTO> reactivar(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.OK).body(mapper.toResponseDTO(service.reactivar(id)));
    }
}
