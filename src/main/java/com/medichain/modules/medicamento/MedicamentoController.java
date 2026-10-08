package com.medichain.modules.medicamento;

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
 * Controlador MedicamentoController en MediChain.
 * Expone el alta y la consulta de medicamentos. Nunca recibe ni devuelve
 * la entidad JPA, siempre MedicamentoRequestDTO/ResponseDTO.
 */
@RestController
@RequestMapping("/api/medicamentos")
@Tag(name = "Medicamentos", description = "Alta y consulta del catálogo de medicamentos")
public class MedicamentoController {

    private final MedicamentoService service;
    private final MedicamentoMapper mapper;

    public MedicamentoController(MedicamentoService service, MedicamentoMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    /** Lista los medicamentos de forma paginada. */
    @GetMapping
    @Operation(operationId = "listarMedicamentos", summary = "Listar medicamentos", description = "Devuelve una página de medicamentos. Roles: SEDE_CENTRAL, INSPECTOR, LABORATORIO, DISTRIBUIDOR, FARMACIA.")
    @RespuestasError({400, 401, 403})
    @PreAuthorize("hasAnyRole('SEDE_CENTRAL', 'INSPECTOR', 'LABORATORIO', 'DISTRIBUIDOR', 'FARMACIA')")
    public ResponseEntity<Page<MedicamentoResponseDTO>> getAll(
            @ParameterObject @PageableDefault(size = 20, sort = "fechaCreacion", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<MedicamentoResponseDTO> page = service.getAll(pageable).map(mapper::toResponseDTO);
        return ResponseEntity.status(HttpStatus.OK).body(page);
    }

    /** Busca un medicamento por id. */
    @GetMapping("/{id}")
    @Operation(operationId = "obtenerMedicamento", summary = "Obtener un medicamento", description = "Busca un medicamento por su id. Roles: SEDE_CENTRAL, INSPECTOR, LABORATORIO, DISTRIBUIDOR, FARMACIA.")
    @RespuestasError({400, 401, 403, 404})
    @PreAuthorize("hasAnyRole('SEDE_CENTRAL', 'INSPECTOR', 'LABORATORIO', 'DISTRIBUIDOR', 'FARMACIA')")
    public ResponseEntity<MedicamentoResponseDTO> getById(@PathVariable UUID id) {
        MedicamentoResponseDTO dto = mapper.toResponseDTO(service.getById(id));
        return ResponseEntity.status(HttpStatus.OK).body(dto);
    }

    /** Registra un nuevo medicamento. */
    @PostMapping
    @Operation(operationId = "registrarMedicamento", summary = "Registrar un medicamento", description = "Crea un medicamento en el catálogo. Roles: LABORATORIO. Reglas: R2, R3.")
    @ApiResponse(responseCode = "201", description = "Creado")
    @RespuestasError({400, 401, 403, 409})
    @PreAuthorize("hasAnyRole('LABORATORIO')")
    public ResponseEntity<MedicamentoResponseDTO> create(@Valid @RequestBody MedicamentoRequestDTO dto) {
        Medicamento entity = mapper.toEntity(dto);
        Medicamento created = service.create(entity, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponseDTO(created));
    }
}
