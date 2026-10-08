package com.medichain.modules.cuarentena;

import com.medichain.config.RespuestasError;
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
import java.util.UUID;

/**
 * Controlador CuarentenaController en MediChain.
 * Expone el alta y la consulta de medidas sanitarias. Nunca recibe ni
 * devuelve la entidad JPA, siempre CuarentenaRequestDTO/ResponseDTO.
 */
@RestController
@RequestMapping("/api/cuarentenas")
@Tag(name = "Cuarentenas", description = "Medidas sanitarias: apertura de lote, bandeja, tomar y dictamen (levantar o recall), R12")
public class CuarentenaController {

    private final CuarentenaService service;
    private final CuarentenaMapper mapper;

    @Autowired
    public CuarentenaController(CuarentenaService service, CuarentenaMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    /** Lista las medidas sanitarias de forma paginada. */
    @GetMapping
    @Operation(operationId = "listarCuarentenas", summary = "Listar cuarentenas", description = "Devuelve una página de medidas sanitarias. Filtro opcional ?estado=ACTIVA, LEVANTADA o CONVERTIDA_EN_RECALL (una sola por consulta; lo ajeno sigue sin verse). Roles: SEDE_CENTRAL, INSPECTOR, LABORATORIO, DISTRIBUIDOR, FARMACIA.")
    @RespuestasError({400, 401, 403})
    @PreAuthorize("hasAnyRole('SEDE_CENTRAL', 'INSPECTOR', 'LABORATORIO', 'DISTRIBUIDOR', 'FARMACIA')")
    public ResponseEntity<Page<CuarentenaResponseDTO>> getAll(
            @Parameter(description = "Estado (opcional): ACTIVA, LEVANTADA o CONVERTIDA_EN_RECALL") @RequestParam(required = false) EstadoCuarentena estado,
            @ParameterObject @PageableDefault(size = 20, sort = "fechaCreacion", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<CuarentenaResponseDTO> page = service.getAll(estado, pageable).map(mapper::toResponseDTO);
        return ResponseEntity.status(HttpStatus.OK).body(page);
    }

    /** Busca una medida sanitaria por id. */
    @GetMapping("/{id}")
    @Operation(operationId = "obtenerCuarentena", summary = "Obtener una cuarentena", description = "Busca una medida sanitaria por su id. Roles: SEDE_CENTRAL, INSPECTOR, LABORATORIO, DISTRIBUIDOR, FARMACIA.")
    @RespuestasError({400, 401, 403, 404})
    @PreAuthorize("hasAnyRole('SEDE_CENTRAL', 'INSPECTOR', 'LABORATORIO', 'DISTRIBUIDOR', 'FARMACIA')")
    public ResponseEntity<CuarentenaResponseDTO> getById(@PathVariable UUID id) {
        CuarentenaResponseDTO dto = mapper.toResponseDTO(service.getById(id));
        return ResponseEntity.status(HttpStatus.OK).body(dto);
    }

    /** Bandeja del inspector. */
    @GetMapping("/bandeja")
    @Operation(operationId = "bandejaCuarentenas", summary = "Bandeja de cuarentenas", description = "Medidas ACTIVA de tu provincia sin tomar, más las que tomaste. Roles: INSPECTOR. Reglas: R12.")
    @RespuestasError({400, 401, 403})
    @PreAuthorize("hasRole('INSPECTOR')")
    public ResponseEntity<Page<CuarentenaResponseDTO>> bandeja(
            @ParameterObject @PageableDefault(size = 20, sort = "fechaInicio", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.status(HttpStatus.OK).body(service.bandeja(pageable).map(mapper::toResponseDTO));
    }

    /** Abre una cuarentena manual de LOTE. */
    @PostMapping
    @Operation(operationId = "abrirCuarentena", summary = "Abrir una cuarentena de lote", description = "Lote LIBERADO de un laboratorio de tu provincia (si no, 404), que pasa a CUARENTENA. "
            + "Motivo PREVENTIVA o DEFECTO_CALIDAD. Quedás como revisor. Opcional: reporteId de un reporte que investigás. "
            + "Evento CUARENTENA. Roles: INSPECTOR. Reglas: R12.")
    @ApiResponse(responseCode = "201", description = "Creado")
    @RespuestasError({400, 401, 403, 404, 409})
    @PreAuthorize("hasRole('INSPECTOR')")
    public ResponseEntity<CuarentenaResponseDTO> abrir(@Valid @RequestBody CuarentenaRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponseDTO(service.abrir(dto)));
    }

    /** Toma una medida para dictaminarla. */
    @PostMapping("/{id}/tomar")
    @Operation(operationId = "tomarCuarentena", summary = "Tomar una cuarentena", description = "Medida ACTIVA de tu provincia sin revisor → quedás como revisor. Evento CUARENTENA_TOMADA. Roles: INSPECTOR. Reglas: R12.")
    @RespuestasError({400, 401, 403, 404, 409})
    @PreAuthorize("hasRole('INSPECTOR')")
    public ResponseEntity<CuarentenaResponseDTO> tomar(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.OK).body(mapper.toResponseDTO(service.tomar(id)));
    }

    /** Levanta la medida (dictamen). */
    @PostMapping("/{id}/levantar")
    @Operation(operationId = "levantarCuarentena", summary = "Levantar una cuarentena", description = "ACTIVA → LEVANTADA, con fundamento; exige haberla tomado. LOTE: el lote vuelve a LIBERADO. "
            + "BULTO: el bulto y sus cajas se aceptan en la empresa que los tiene, SOLO si el rechazo fue únicamente por precinto roto (si no, 409 R8). "
            + "Ruptura de frío → 409 R9; robo → 409 R14 (solo cabe recall). Evento CUARENTENA_LEVANTADA. Roles: INSPECTOR. Reglas: R12, R8, R9, R14.")
    @RespuestasError({400, 401, 403, 404, 409})
    @PreAuthorize("hasRole('INSPECTOR')")
    public ResponseEntity<CuarentenaResponseDTO> levantar(@PathVariable UUID id, @Valid @RequestBody DictamenRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.OK).body(mapper.toResponseDTO(service.levantar(id, dto.getFundamento())));
    }

    /** Convierte la medida en recall (dictamen). */
    @PostMapping("/{id}/recall")
    @Operation(operationId = "convertirCuarentenaEnRecall", summary = "Convertir en recall", description = "ACTIVA → CONVERTIDA_EN_RECALL, con fundamento; exige haberla tomado. LOTE: el lote pasa a RECALL "
            + "y todas sus cajas quedan bloqueadas estén donde estén. DESPACHO o BULTO: solo esos bultos, para siempre. Evento RECALL. Roles: INSPECTOR. Reglas: R12.")
    @RespuestasError({400, 401, 403, 404, 409})
    @PreAuthorize("hasRole('INSPECTOR')")
    public ResponseEntity<CuarentenaResponseDTO> recall(@PathVariable UUID id, @Valid @RequestBody DictamenRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.OK).body(mapper.toResponseDTO(service.recall(id, dto.getFundamento())));
    }
}
