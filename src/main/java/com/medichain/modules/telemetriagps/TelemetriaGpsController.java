package com.medichain.modules.telemetriagps;

import io.swagger.v3.oas.annotations.Operation;
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
 * Controlador TelemetriaGpsController en MediChain.
 * Expone el alta y la consulta de lecturas GPS. Nunca recibe ni devuelve
 * la entidad JPA, siempre RequestDTO/ResponseDTO.
 */
@RestController
@RequestMapping("/api/telemetria-gps")
@Tag(name = "Telemetría GPS", description = "Alta y consulta de lecturas de posición durante el transporte")
public class TelemetriaGpsController {

    private final TelemetriaGpsService service;
    private final TelemetriaGpsMapper mapper;

    public TelemetriaGpsController(TelemetriaGpsService service, TelemetriaGpsMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    /** Lista las lecturas GPS de forma paginada. */
    @GetMapping
    @Operation(summary = "Listar lecturas GPS", description = "Devuelve una página de lecturas GPS. Roles: SEDE_CENTRAL, INSPECTOR, LABORATORIO, DISTRIBUIDOR.")
    @PreAuthorize("hasAnyRole('SEDE_CENTRAL', 'INSPECTOR', 'LABORATORIO', 'DISTRIBUIDOR')")
    public ResponseEntity<Page<TelemetriaGpsResponseDTO>> getAll(
            @ParameterObject @PageableDefault(size = 20, sort = "fechaCreacion", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<TelemetriaGpsResponseDTO> page = service.getAll(pageable).map(mapper::toResponseDTO);
        return ResponseEntity.status(HttpStatus.OK).body(page);
    }

    /** Busca una lectura GPS por id. */
    @GetMapping("/{id}")
    @Operation(summary = "Obtener una lectura", description = "Busca una lectura GPS por su id. Roles: SEDE_CENTRAL, INSPECTOR, LABORATORIO, DISTRIBUIDOR.")
    @PreAuthorize("hasAnyRole('SEDE_CENTRAL', 'INSPECTOR', 'LABORATORIO', 'DISTRIBUIDOR')")
    public ResponseEntity<TelemetriaGpsResponseDTO> getById(@PathVariable UUID id) {
        TelemetriaGpsResponseDTO dto = mapper.toResponseDTO(service.getById(id));
        return ResponseEntity.status(HttpStatus.OK).body(dto);
    }

    /** Registra una nueva lectura GPS. */
    @PostMapping
    @Operation(summary = "Registrar una lectura", description = "Crea una lectura GPS de un despacho. Roles: LABORATORIO, DISTRIBUIDOR.")
    @PreAuthorize("hasAnyRole('LABORATORIO', 'DISTRIBUIDOR')")
    public ResponseEntity<TelemetriaGpsResponseDTO> create(@Valid @RequestBody TelemetriaGpsRequestDTO dto) {
        TelemetriaGps created = service.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponseDTO(created));
    }
}
