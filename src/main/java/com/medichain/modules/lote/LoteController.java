package com.medichain.modules.lote;

import com.medichain.config.RespuestasError;
import com.medichain.modules.cuarentena.Bloqueo;
import com.medichain.modules.cuarentena.EvaluadorBloqueo;
import com.medichain.modules.unidadtrazable.UnidadTrazable;
import com.medichain.modules.unidadtrazable.UnidadTrazableMapper;
import com.medichain.modules.unidadtrazable.UnidadTrazableResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controlador LoteController en MediChain.
 * Registro de lotes con sus cajas, liberación (R4), bandeja de liberación
 * del inspector y consulta de las cajas de un lote. Nunca devuelve
 * entidades JPA.
 */
@RestController
@RequestMapping("/api/lotes")
@Tag(name = "Lotes", description = "Registro de lotes con sus series (R3) y liberación (R4)")
public class LoteController {

    private final LoteService service;
    private final LoteMapper mapper;
    private final UnidadTrazableMapper unidadTrazableMapper;
    private final EvaluadorBloqueo evaluadorBloqueo;

    @Autowired
    public LoteController(LoteService service, LoteMapper mapper, UnidadTrazableMapper unidadTrazableMapper,
                          EvaluadorBloqueo evaluadorBloqueo) {
        this.service = service;
        this.mapper = mapper;
        this.unidadTrazableMapper = unidadTrazableMapper;
        this.evaluadorBloqueo = evaluadorBloqueo;
    }

    /** Lista los lotes visibles para el usuario. */
    @GetMapping
    @Operation(operationId = "listarLotes", summary = "Listar lotes", description = "SEDE e INSPECTOR ven todos; el laboratorio, los suyos. Filtro opcional ?estado=PENDIENTE_LIBERACION, LIBERADO, CUARENTENA o RECALL (una sola por consulta; lo ajeno sigue sin verse). Incluye el indicador de bloqueo R10 (bloqueado, motivoBloqueo, mensajeBloqueo). Roles: SEDE_CENTRAL, INSPECTOR, LABORATORIO, DISTRIBUIDOR, FARMACIA. Reglas: R10.")
    @RespuestasError({400, 401, 403})
    @PreAuthorize("hasAnyRole('SEDE_CENTRAL', 'INSPECTOR', 'LABORATORIO', 'DISTRIBUIDOR', 'FARMACIA')")
    public ResponseEntity<Page<LoteResponseDTO>> getAll(
            @Parameter(description = "Estado (opcional): PENDIENTE_LIBERACION, LIBERADO, CUARENTENA o RECALL") @RequestParam(required = false) EstadoLote estado,
            @ParameterObject @PageableDefault(size = 20, sort = "fechaCreacion", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.status(HttpStatus.OK).body(pagina(service.getAll(estado, pageable)));
    }

    /** Bandeja de liberación del inspector. */
    @GetMapping("/bandeja-liberacion")
    @Operation(operationId = "bandejaLiberacionLotes", summary = "Bandeja de liberación", description = "Lotes de medicamentos BIOLÓGICOS en PENDIENTE_LIBERACION de laboratorios de tu provincia. Roles: INSPECTOR. Reglas: R4.")
    @RespuestasError({400, 401, 403})
    @PreAuthorize("hasRole('INSPECTOR')")
    public ResponseEntity<Page<LoteResponseDTO>> bandejaLiberacion(
            @ParameterObject @PageableDefault(size = 20, sort = "fechaCreacion", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.status(HttpStatus.OK).body(pagina(service.bandejaLiberacion(pageable)));
    }

    /** Busca un lote por id. */
    @GetMapping("/{id}")
    @Operation(operationId = "obtenerLote", summary = "Obtener un lote", description = "404 si no es de tu laboratorio. Incluye el indicador de bloqueo R10 (bloqueado, motivoBloqueo, mensajeBloqueo). Roles: SEDE_CENTRAL, INSPECTOR, LABORATORIO, DISTRIBUIDOR, FARMACIA. Reglas: R10.")
    @RespuestasError({400, 401, 403, 404})
    @PreAuthorize("hasAnyRole('SEDE_CENTRAL', 'INSPECTOR', 'LABORATORIO', 'DISTRIBUIDOR', 'FARMACIA')")
    public ResponseEntity<LoteResponseDTO> getById(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.OK).body(respuesta(service.getById(id)));
    }

    /** Cajas del lote, paginadas por serie. */
    @GetMapping("/{id}/unidades")
    @Operation(operationId = "listarCajasDelLote", summary = "Cajas de un lote", description = "Cada caja con su GTIN y serie. Incluye el indicador de bloqueo R10 (bloqueado, motivoBloqueo, mensajeBloqueo). Roles: SEDE_CENTRAL, INSPECTOR, LABORATORIO dueño (los demás, 404). Reglas: R10.")
    @RespuestasError({400, 401, 403, 404})
    @PreAuthorize("hasAnyRole('SEDE_CENTRAL', 'INSPECTOR', 'LABORATORIO')")
    public ResponseEntity<Page<UnidadTrazableResponseDTO>> unidades(@PathVariable UUID id,
            @ParameterObject @PageableDefault(size = 50, sort = "serie", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(paginaDeCajas(service.unidades(id, pageable)));
    }

    /** Registra un lote con todas sus cajas. */
    @PostMapping
    @Operation(operationId = "registrarLote", summary = "Registrar un lote con sus series", description = "Una sola operación: el lote nace PENDIENTE_LIBERACION con todas sus cajas EN_LABORATORIO. "
            + "Enviá exactamente una de: 'series' (lista) o 'cantidad' (el servidor genera L2026-0003 → L20260003S000001…). Máximo 10.000. "
            + "Código único por laboratorio (409 LOTE_DUPLICADO). Series (R3): alfanuméricas, hasta 20, no empiezan con 779, únicas por GTIN; "
            + "si alguna falla no se crea nada (409 R3) y queda el evento INTENTO_SERIE_INVALIDA. Evento LOTE_REGISTRADO. Roles: LABORATORIO. Reglas: R3.")
    @ApiResponse(responseCode = "201", description = "Creado")
    @RespuestasError({400, 401, 403, 404, 409})
    @PreAuthorize("hasRole('LABORATORIO')")
    public ResponseEntity<LoteResponseDTO> registrar(@Valid @RequestBody LoteRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta(service.registrar(dto)));
    }

    /** Libera el lote (R4). */
    @PostMapping("/{id}/liberar")
    @Operation(operationId = "liberarLote", summary = "Liberar un lote", description = "PENDIENTE_LIBERACION → LIBERADO. Común: el director técnico de su laboratorio. Biológico: un inspector de la provincia del laboratorio. "
            + "Al revés → 409 R4; vencido → 409 R10. Evento LOTE_LIBERADO. Roles: LABORATORIO (DT), INSPECTOR. Reglas: R4, R10.")
    @RespuestasError({400, 401, 403, 404, 409})
    @PreAuthorize("hasAnyRole('LABORATORIO', 'INSPECTOR')")
    public ResponseEntity<LoteResponseDTO> liberar(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.OK).body(respuesta(service.liberar(id)));
    }

    /** DTO de un lote con su bloqueo R10. */
    private LoteResponseDTO respuesta(Lote lote) {
        return mapper.toResponseDTO(lote, evaluadorBloqueo.bloqueosDeLotes(List.of(lote)).get(lote.getId()));
    }

    /** Página de lotes con el bloqueo R10 de cada uno, calculado para toda la página de una vez (sin N+1). */
    private Page<LoteResponseDTO> pagina(Page<Lote> lotes) {
        Map<UUID, Bloqueo> bloqueos = evaluadorBloqueo.bloqueosDeLotes(lotes.getContent());
        return lotes.map(lote -> mapper.toResponseDTO(lote, bloqueos.get(lote.getId())));
    }

    /** Página de cajas del lote con el bloqueo R10 de cada una (sin N+1). */
    private Page<UnidadTrazableResponseDTO> paginaDeCajas(Page<UnidadTrazable> cajas) {
        Map<UUID, Bloqueo> bloqueos = evaluadorBloqueo.bloqueosDeCajas(cajas.getContent());
        return cajas.map(caja -> unidadTrazableMapper.toResponseDTO(caja, bloqueos.get(caja.getId())));
    }
}
