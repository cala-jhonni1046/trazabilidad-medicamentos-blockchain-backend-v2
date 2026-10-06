package com.medichain.modules.bulto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Controlador BultoController en MediChain.
 * Armado, desarmado y consulta de bultos. Nunca recibe ni devuelve la
 * entidad JPA, siempre BultoRequestDTO/BultoResponseDTO.
 */
@RestController
@RequestMapping("/api/bultos")
@Tag(name = "Bultos", description = "Alta y consulta de bultos de transporte")
public class BultoController {

    private final BultoService service;
    private final BultoMapper mapper;

    @Autowired
    public BultoController(BultoService service, BultoMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    /** Lista los bultos de forma paginada. */
    @GetMapping
    @Operation(summary = "Listar bultos", description = "Devuelve una página de bultos. Roles: SEDE_CENTRAL, INSPECTOR, LABORATORIO, DISTRIBUIDOR, FARMACIA.")
    @PreAuthorize("hasAnyRole('SEDE_CENTRAL', 'INSPECTOR', 'LABORATORIO', 'DISTRIBUIDOR', 'FARMACIA')")
    public ResponseEntity<Page<BultoResponseDTO>> getAll(
            @ParameterObject @PageableDefault(size = 20, sort = "fechaCreacion", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<BultoResponseDTO> page = service.getAll(pageable).map(mapper::toResponseDTO);
        return ResponseEntity.status(HttpStatus.OK).body(page);
    }

    /** Busca un bulto por id. */
    @GetMapping("/{id}")
    @Operation(summary = "Obtener un bulto", description = "Busca un bulto por su id. Roles: SEDE_CENTRAL, INSPECTOR, LABORATORIO, DISTRIBUIDOR, FARMACIA.")
    @PreAuthorize("hasAnyRole('SEDE_CENTRAL', 'INSPECTOR', 'LABORATORIO', 'DISTRIBUIDOR', 'FARMACIA')")
    public ResponseEntity<BultoResponseDTO> getById(@PathVariable UUID id) {
        BultoResponseDTO dto = mapper.toResponseDTO(service.getById(id));
        return ResponseEntity.status(HttpStatus.OK).body(dto);
    }

    /** Arma un bulto (R6). */
    @PostMapping
    @Operation(summary = "Armar un bulto", description = "Cajas de UN lote LIBERADO de tu laboratorio y un circuito APROBADO (fija la farmacia de destino). "
            + "Enviá exactamente una de: 'series' (las cajas escaneadas) o 'cantidad' (las primeras disponibles del lote). Máximo 1.000. "
            + "Código BUL-0001 generado. Cajas de otro lote, ya en otro bulto o bloqueadas → 409 R6/R10. Evento BULTO_ARMADO. Roles: LABORATORIO.")
    @PreAuthorize("hasRole('LABORATORIO')")
    public ResponseEntity<BultoResponseDTO> armar(@Valid @RequestBody BultoRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponseDTO(service.armar(dto)));
    }

    /** Desarma un bulto ARMADO que no está en ningún viaje. */
    @PostMapping("/{id}/desarmar")
    @Operation(summary = "Desarmar un bulto", description = "ARMADO y sin viaje → DESARMADO; sus cajas quedan libres en el laboratorio. Evento BULTO_DESARMADO. Roles: LABORATORIO dueño.")
    @PreAuthorize("hasRole('LABORATORIO')")
    public ResponseEntity<BultoResponseDTO> desarmar(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.OK).body(mapper.toResponseDTO(service.desarmar(id)));
    }
}
