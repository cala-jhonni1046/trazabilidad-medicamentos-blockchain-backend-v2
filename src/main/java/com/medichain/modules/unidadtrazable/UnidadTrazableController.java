package com.medichain.modules.unidadtrazable;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

/**
 * Controlador UnidadTrazableController en MediChain.
 * Consulta de cajas (nacen con su lote en POST /api/lotes) y devolución (R14).
 * Nunca devuelve la entidad JPA, siempre UnidadTrazableResponseDTO.
 */
@RestController
@RequestMapping("/api/unidades-trazables")
@Tag(name = "Unidades trazables", description = "Alta y consulta de unidades trazables individuales")
public class UnidadTrazableController {

    private final UnidadTrazableService service;
    private final UnidadTrazableMapper mapper;

    public UnidadTrazableController(UnidadTrazableService service, UnidadTrazableMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    /** Lista las unidades trazables de forma paginada. */
    @GetMapping
    @Operation(summary = "Listar unidades trazables", description = "Devuelve una página de unidades trazables. Roles: SEDE_CENTRAL, INSPECTOR, LABORATORIO, DISTRIBUIDOR, FARMACIA.")
    @PreAuthorize("hasAnyRole('SEDE_CENTRAL', 'INSPECTOR', 'LABORATORIO', 'DISTRIBUIDOR', 'FARMACIA')")
    public ResponseEntity<Page<UnidadTrazableResponseDTO>> getAll(
            @ParameterObject @PageableDefault(size = 20, sort = "fechaCreacion", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<UnidadTrazableResponseDTO> page = service.getAll(pageable).map(mapper::toResponseDTO);
        return ResponseEntity.status(HttpStatus.OK).body(page);
    }

    /** Busca una unidad trazable por id. */
    @GetMapping("/{id}")
    @Operation(summary = "Obtener una unidad trazable", description = "Busca una unidad trazable por su id. Roles: SEDE_CENTRAL, INSPECTOR, LABORATORIO, DISTRIBUIDOR, FARMACIA.")
    @PreAuthorize("hasAnyRole('SEDE_CENTRAL', 'INSPECTOR', 'LABORATORIO', 'DISTRIBUIDOR', 'FARMACIA')")
    public ResponseEntity<UnidadTrazableResponseDTO> getById(@PathVariable UUID id) {
        UnidadTrazableResponseDTO dto = mapper.toResponseDTO(service.getById(id));
        return ResponseEntity.status(HttpStatus.OK).body(dto);
    }

    /** Devuelve una caja del stock de la farmacia (R14). */
    @PostMapping("/devolucion")
    @Operation(summary = "Devolver una caja", description = "La farmacia que tiene la caja EN_STOCK la devuelve (GTIN + serie, motivo DANADA / VENCIDA / RETIRO_DEL_MERCADO / OTRO). "
            + "Queda DEVUELTA y no vuelve a dispensarse. Evento DEVOLUCION. Roles: FARMACIA.")
    @PreAuthorize("hasRole('FARMACIA')")
    public ResponseEntity<UnidadTrazableResponseDTO> devolver(@Valid @RequestBody DevolucionRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.OK).body(mapper.toResponseDTO(service.devolver(dto)));
    }
}
