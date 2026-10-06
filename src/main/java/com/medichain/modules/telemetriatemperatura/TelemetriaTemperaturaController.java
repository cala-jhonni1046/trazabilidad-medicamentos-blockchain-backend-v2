package com.medichain.modules.telemetriatemperatura;

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
 * Controlador TelemetriaTemperaturaController en MediChain.
 * Expone el alta y la consulta de lecturas de temperatura. Nunca recibe
 * ni devuelve la entidad JPA, siempre RequestDTO/ResponseDTO.
 */
@RestController
@RequestMapping("/api/telemetria-temperatura")
@Tag(name = "Telemetría de temperatura", description = "Alta y consulta de lecturas de temperatura durante el transporte")
public class TelemetriaTemperaturaController {

    private final TelemetriaTemperaturaService service;
    private final TelemetriaTemperaturaMapper mapper;

    public TelemetriaTemperaturaController(TelemetriaTemperaturaService service, TelemetriaTemperaturaMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    /** Lista las lecturas de temperatura de forma paginada. */
    @GetMapping
    @Operation(summary = "Listar lecturas de temperatura", description = "Devuelve una página de lecturas de temperatura. Roles: SEDE_CENTRAL, INSPECTOR, LABORATORIO, DISTRIBUIDOR.")
    @PreAuthorize("hasAnyRole('SEDE_CENTRAL', 'INSPECTOR', 'LABORATORIO', 'DISTRIBUIDOR')")
    public ResponseEntity<Page<TelemetriaTemperaturaResponseDTO>> getAll(
            @ParameterObject @PageableDefault(size = 20, sort = "fechaCreacion", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<TelemetriaTemperaturaResponseDTO> page = service.getAll(pageable).map(mapper::toResponseDTO);
        return ResponseEntity.status(HttpStatus.OK).body(page);
    }

    /** Busca una lectura de temperatura por id. */
    @GetMapping("/{id}")
    @Operation(summary = "Obtener una lectura", description = "Busca una lectura de temperatura por su id. Roles: SEDE_CENTRAL, INSPECTOR, LABORATORIO, DISTRIBUIDOR.")
    @PreAuthorize("hasAnyRole('SEDE_CENTRAL', 'INSPECTOR', 'LABORATORIO', 'DISTRIBUIDOR')")
    public ResponseEntity<TelemetriaTemperaturaResponseDTO> getById(@PathVariable UUID id) {
        TelemetriaTemperaturaResponseDTO dto = mapper.toResponseDTO(service.getById(id));
        return ResponseEntity.status(HttpStatus.OK).body(dto);
    }

    /** Registra una nueva lectura de temperatura. */
    @PostMapping
    @Operation(summary = "Registrar una lectura", description = "Crea una lectura de temperatura de un despacho. Roles: LABORATORIO, DISTRIBUIDOR.")
    @PreAuthorize("hasAnyRole('LABORATORIO', 'DISTRIBUIDOR')")
    public ResponseEntity<TelemetriaTemperaturaResponseDTO> create(@Valid @RequestBody TelemetriaTemperaturaRequestDTO dto) {
        TelemetriaTemperatura created = service.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponseDTO(created));
    }
}
