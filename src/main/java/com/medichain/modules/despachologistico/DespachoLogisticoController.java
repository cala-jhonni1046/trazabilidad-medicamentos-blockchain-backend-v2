package com.medichain.modules.despachologistico;

import com.medichain.modules.empresa.MotivoRequestDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Controlador DespachoLogisticoController en MediChain (viajes).
 * Ruta /api/viajes (la entidad sigue siendo DespachoLogistico). Consulta,
 * creación y acciones POST /api/viajes/{id}/&lt;accion&gt;: salida, cancelar
 * y robo. Nunca devuelve la entidad JPA.
 */
@RestController
@RequestMapping("/api/viajes")
@Tag(name = "Viajes", description = "Viajes de tramo 1 (laboratorio → distribuidora) y tramo 2 (distribuidora → farmacias): salida, cancelación y robo (R7, R10, R14)")
public class DespachoLogisticoController {

    private final DespachoLogisticoService service;
    private final DespachoLogisticoMapper mapper;

    @Autowired
    public DespachoLogisticoController(DespachoLogisticoService service, DespachoLogisticoMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    /** Lista los viajes visibles para el usuario. */
    @GetMapping
    @Operation(summary = "Listar viajes", description = "SEDE e INSPECTOR ven todos; LABORATORIO los que origina; DISTRIBUIDOR los que origina y los del tramo 1 que vienen a su depósito; FARMACIA los del tramo 2 con parada en ella.")
    @PreAuthorize("hasAnyRole('SEDE_CENTRAL', 'INSPECTOR', 'LABORATORIO', 'DISTRIBUIDOR', 'FARMACIA')")
    public ResponseEntity<Page<DespachoLogisticoResponseDTO>> getAll(
            @ParameterObject @PageableDefault(size = 20, sort = "fechaCreacion", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.status(HttpStatus.OK).body(service.getAll(pageable).map(mapper::toResponseDTO));
    }

    /** Busca un viaje por id. */
    @GetMapping("/{id}")
    @Operation(summary = "Obtener un viaje", description = "404 si no te corresponde. Roles: SEDE_CENTRAL, INSPECTOR, LABORATORIO, DISTRIBUIDOR, FARMACIA.")
    @PreAuthorize("hasAnyRole('SEDE_CENTRAL', 'INSPECTOR', 'LABORATORIO', 'DISTRIBUIDOR', 'FARMACIA')")
    public ResponseEntity<DespachoLogisticoResponseDTO> getById(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.OK).body(mapper.toResponseDTO(service.getById(id)));
    }

    /** Crea un viaje PROGRAMADO con sus bultos. */
    @PostMapping
    @Operation(summary = "Crear un viaje", description = "El tramo lo decide tu rol. LABORATORIO (tramo 1): bultos ARMADO propios hacia UNA distribuidora. "
            + "DISTRIBUIDOR (tramo 2): bultos EN_DEPOSITO en tu depósito, varias farmacias. Ningún bulto bloqueado (R10). "
            + "Los bultos quedan fijos. Código VJ-0001 generado. Evento VIAJE_CREADO. Roles: LABORATORIO, DISTRIBUIDOR.")
    @PreAuthorize("hasAnyRole('LABORATORIO', 'DISTRIBUIDOR')")
    public ResponseEntity<DespachoLogisticoResponseDTO> crear(@Valid @RequestBody DespachoLogisticoRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponseDTO(service.crear(dto)));
    }

    /** Registra la salida del viaje. */
    @PostMapping("/{id}/salida")
    @Operation(summary = "Registrar la salida", description = "PROGRAMADO → EN_TRANSITO; bultos y cajas pasan a EN_TRANSITO. Si algún bulto está bloqueado → 409 R10. Evento VIAJE_SALIDA. Roles: la empresa origen del viaje.")
    @PreAuthorize("hasAnyRole('LABORATORIO', 'DISTRIBUIDOR')")
    public ResponseEntity<DespachoLogisticoResponseDTO> salida(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.OK).body(mapper.toResponseDTO(service.salida(id)));
    }

    /** Cancela un viaje que no salió. */
    @PostMapping("/{id}/cancelar")
    @Operation(summary = "Cancelar un viaje", description = "PROGRAMADO → CANCELADO, con motivo; sus bultos quedan sin viaje. Evento VIAJE_CANCELADO. Roles: la empresa origen del viaje.")
    @PreAuthorize("hasAnyRole('LABORATORIO', 'DISTRIBUIDOR')")
    public ResponseEntity<DespachoLogisticoResponseDTO> cancelar(@PathVariable UUID id, @Valid @RequestBody MotivoRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.OK).body(mapper.toResponseDTO(service.cancelar(id, dto.getMotivo())));
    }

    /** Reporta robo o extravío del viaje. */
    @PostMapping("/{id}/robo")
    @Operation(summary = "Reportar robo o extravío", description = "EN_TRANSITO → ROBADO; bultos ROBADO, cajas ROBADA y cuarentena DESPACHO automática (R14). Eventos ROBO_EXTRAVIO y CUARENTENA. Roles: la empresa origen del viaje.")
    @PreAuthorize("hasAnyRole('LABORATORIO', 'DISTRIBUIDOR')")
    public ResponseEntity<DespachoLogisticoResponseDTO> robo(@PathVariable UUID id, @Valid @RequestBody MotivoRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.OK).body(mapper.toResponseDTO(service.robo(id, dto.getMotivo())));
    }
}
