package com.medichain.modules.bulto;

import com.medichain.config.RespuestasError;
import com.medichain.modules.cuarentena.Bloqueo;
import com.medichain.modules.cuarentena.EvaluadorBloqueo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;
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
    private final EvaluadorBloqueo evaluadorBloqueo;

    @Autowired
    public BultoController(BultoService service, BultoMapper mapper, EvaluadorBloqueo evaluadorBloqueo) {
        this.service = service;
        this.mapper = mapper;
        this.evaluadorBloqueo = evaluadorBloqueo;
    }

    /** Lista los bultos de forma paginada. */
    @GetMapping
    @Operation(operationId = "listarBultos", summary = "Listar bultos", description = "Devuelve una página de bultos. Filtro opcional ?estado=ARMADO, EN_TRANSITO, EN_DEPOSITO, RECIBIDO, RECHAZADO, ROBADO o DESARMADO (una sola por consulta; lo ajeno sigue sin verse). Incluye el indicador de bloqueo R10 (bloqueado, motivoBloqueo, mensajeBloqueo). Roles: SEDE_CENTRAL, INSPECTOR, LABORATORIO, DISTRIBUIDOR, FARMACIA. Reglas: R10.")
    @RespuestasError({400, 401, 403})
    @PreAuthorize("hasAnyRole('SEDE_CENTRAL', 'INSPECTOR', 'LABORATORIO', 'DISTRIBUIDOR', 'FARMACIA')")
    public ResponseEntity<Page<BultoResponseDTO>> getAll(
            @Parameter(description = "Estado (opcional): ARMADO, EN_TRANSITO, EN_DEPOSITO, RECIBIDO, RECHAZADO, ROBADO o DESARMADO") @RequestParam(required = false) EstadoBulto estado,
            @ParameterObject @PageableDefault(size = 20, sort = "fechaCreacion", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<BultoResponseDTO> page = pagina(service.getAll(estado, pageable));
        return ResponseEntity.status(HttpStatus.OK).body(page);
    }

    /** Busca un bulto por id. */
    @GetMapping("/{id}")
    @Operation(operationId = "obtenerBulto", summary = "Obtener un bulto", description = "Busca un bulto por su id. Incluye el indicador de bloqueo R10 (bloqueado, motivoBloqueo, mensajeBloqueo). Roles: SEDE_CENTRAL, INSPECTOR, LABORATORIO, DISTRIBUIDOR, FARMACIA. Reglas: R10.")
    @RespuestasError({400, 401, 403, 404})
    @PreAuthorize("hasAnyRole('SEDE_CENTRAL', 'INSPECTOR', 'LABORATORIO', 'DISTRIBUIDOR', 'FARMACIA')")
    public ResponseEntity<BultoResponseDTO> getById(@PathVariable UUID id) {
        BultoResponseDTO dto = respuesta(service.getById(id));
        return ResponseEntity.status(HttpStatus.OK).body(dto);
    }

    /** Arma un bulto (R6). */
    @PostMapping
    @Operation(operationId = "armarBulto", summary = "Armar un bulto", description = "Cajas de UN lote LIBERADO de tu laboratorio y un circuito APROBADO (fija la farmacia de destino). "
            + "Enviá exactamente una de: 'series' (las cajas escaneadas) o 'cantidad' (las primeras disponibles del lote). Máximo 1.000. "
            + "Código BUL-0001 generado. Cajas de otro lote, ya en otro bulto o bloqueadas → 409 R6/R10. Evento BULTO_ARMADO. Roles: LABORATORIO. Reglas: R6, R10.")
    @ApiResponse(responseCode = "201", description = "Creado")
    @RespuestasError({400, 401, 403, 404, 409})
    @PreAuthorize("hasRole('LABORATORIO')")
    public ResponseEntity<BultoResponseDTO> armar(@Valid @RequestBody BultoRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta(service.armar(dto)));
    }

    /** Desarma un bulto ARMADO que no está en ningún viaje. */
    @PostMapping("/{id}/desarmar")
    @Operation(operationId = "desarmarBulto", summary = "Desarmar un bulto", description = "ARMADO y sin viaje → DESARMADO; sus cajas quedan libres en el laboratorio. Evento BULTO_DESARMADO. Roles: LABORATORIO dueño. Reglas: R6.")
    @RespuestasError({400, 401, 403, 404, 409})
    @PreAuthorize("hasRole('LABORATORIO')")
    public ResponseEntity<BultoResponseDTO> desarmar(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.OK).body(respuesta(service.desarmar(id)));
    }

    /** DTO de un bulto con su bloqueo R10. */
    private BultoResponseDTO respuesta(Bulto bulto) {
        return mapper.toResponseDTO(bulto, evaluadorBloqueo.bloqueosDeBultos(List.of(bulto)).get(bulto.getId()));
    }

    /** Página de bultos con el bloqueo R10 de cada uno, calculado para toda la página de una vez (sin N+1). */
    private Page<BultoResponseDTO> pagina(Page<Bulto> bultos) {
        Map<UUID, Bloqueo> bloqueos = evaluadorBloqueo.bloqueosDeBultos(bultos.getContent());
        return bultos.map(bulto -> mapper.toResponseDTO(bulto, bloqueos.get(bulto.getId())));
    }
}
