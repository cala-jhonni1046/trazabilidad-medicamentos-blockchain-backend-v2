package com.medichain.modules.recepcion;

import com.medichain.config.RespuestasError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
 * Controlador RecepcionController en MediChain.
 * Expone el alta y la consulta de actas de recepción. Nunca recibe ni
 * devuelve la entidad JPA, siempre RecepcionRequestDTO/ResponseDTO.
 */
@RestController
@RequestMapping("/api/recepciones")
@Tag(name = "Recepciones", description = "Alta y consulta de actas de recepción de bultos")
public class RecepcionController {

    private final RecepcionService service;
    private final RecepcionMapper mapper;

    @Autowired
    public RecepcionController(RecepcionService service, RecepcionMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    /** Lista las recepciones de forma paginada. */
    @GetMapping
    @Operation(operationId = "listarRecepciones", summary = "Listar recepciones", description = "Devuelve una página de actas de recepción. Roles: SEDE_CENTRAL, INSPECTOR, DISTRIBUIDOR, FARMACIA.")
    @RespuestasError({400, 401, 403})
    @PreAuthorize("hasAnyRole('SEDE_CENTRAL', 'INSPECTOR', 'LABORATORIO', 'DISTRIBUIDOR', 'FARMACIA')")
    public ResponseEntity<Page<RecepcionResponseDTO>> getAll(
            @ParameterObject @PageableDefault(size = 20, sort = "fechaCreacion", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<RecepcionResponseDTO> page = service.getAll(pageable).map(mapper::toResponseDTO);
        return ResponseEntity.status(HttpStatus.OK).body(page);
    }

    /** Busca una recepción por id. */
    @GetMapping("/{id}")
    @Operation(operationId = "obtenerRecepcion", summary = "Obtener una recepción", description = "Busca un acta de recepción por su id. Roles: SEDE_CENTRAL, INSPECTOR, DISTRIBUIDOR, FARMACIA.")
    @RespuestasError({400, 401, 403, 404})
    @PreAuthorize("hasAnyRole('SEDE_CENTRAL', 'INSPECTOR', 'LABORATORIO', 'DISTRIBUIDOR', 'FARMACIA')")
    public ResponseEntity<RecepcionResponseDTO> getById(@PathVariable UUID id) {
        RecepcionResponseDTO dto = mapper.toResponseDTO(service.getById(id));
        return ResponseEntity.status(HttpStatus.OK).body(dto);
    }

    /** Recibe un bulto escaneando su código (R8). */
    @PostMapping
    @Operation(operationId = "recibirBulto", summary = "Recibir un bulto", description = "Escaneás el código del bulto e informás precinto, cantidad contada y temperatura de llegada. "
            + "Solo la empresa destino actual (tramo 1: distribuidora; tramo 2: farmacia) con el viaje EN_TRANSITO; otra → 404. "
            + "El servidor decide si es conforme (precinto intacto, cantidad igual y temperatura en el rango del medicamento). "
            + "Conforme: tramo 1 → EN_DEPOSITO, tramo 2 → RECIBIDO (cajas EN_STOCK). No conforme o bulto bloqueado (R10): RECHAZADO + cuarentena BULTO. "
            + "Código inexistente → 404 (BULTO_INEXISTENTE); ya recibido → 409 (BULTO_DUPLICADO). "
            + "Cuando no quedan bultos en curso, el viaje se FINALIZA. Roles: DISTRIBUIDOR, FARMACIA. Reglas: R8, R10.")
    @ApiResponse(responseCode = "201", description = "Creado")
    @RespuestasError({400, 401, 403, 404, 409})
    @PreAuthorize("hasAnyRole('DISTRIBUIDOR', 'FARMACIA')")
    public ResponseEntity<RecepcionResponseDTO> recibir(@Valid @RequestBody RecepcionRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponseDTO(service.recibir(dto)));
    }
}
