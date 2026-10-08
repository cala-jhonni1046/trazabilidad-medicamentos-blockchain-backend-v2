package com.medichain.modules.enlacecuit;

import com.medichain.config.RespuestasError;
import com.medichain.modules.empresa.AsignacionInspectorRequestDTO;
import com.medichain.modules.empresa.MotivoRequestDTO;
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
import java.util.UUID;

/**
 * Controlador EnlaceCuitController en MediChain (circuitos).
 * Ruta /api/circuitos (la entidad sigue siendo EnlaceCuit). Consulta,
 * propuesta y endpoints de acción POST /api/circuitos/{id}/&lt;accion&gt;.
 * Nunca devuelve la entidad JPA.
 */
@RestController
@RequestMapping("/api/circuitos")
@Tag(name = "Circuitos", description = "Circuitos laboratorio → distribuidora → farmacia (R5): propuesta, aceptación, aprobación y suspensión")
public class EnlaceCuitController {

    private final EnlaceCuitService service;
    private final EnlaceCuitMapper mapper;

    @Autowired
    public EnlaceCuitController(EnlaceCuitService service, EnlaceCuitMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    /** Lista los circuitos visibles para el usuario. */
    @GetMapping
    @Operation(operationId = "listarCircuitos", summary = "Listar circuitos", description = "SEDE e INSPECTOR ven todos; cada empresa, los circuitos donde participa. Filtro opcional ?estado=PENDIENTE_EMPRESAS, PENDIENTE_INSPECTOR, APROBADO, RECHAZADO o SUSPENDIDO (una sola por consulta; lo ajeno sigue sin verse). Roles: SEDE_CENTRAL, INSPECTOR, LABORATORIO, DISTRIBUIDOR, FARMACIA.")
    @RespuestasError({400, 401, 403})
    @PreAuthorize("hasAnyRole('SEDE_CENTRAL', 'INSPECTOR', 'LABORATORIO', 'DISTRIBUIDOR', 'FARMACIA')")
    public ResponseEntity<Page<EnlaceCuitResponseDTO>> getAll(
            @Parameter(description = "Estado (opcional): PENDIENTE_EMPRESAS, PENDIENTE_INSPECTOR, APROBADO, RECHAZADO o SUSPENDIDO") @RequestParam(required = false) EstadoEnlaceCuit estado,
            @ParameterObject @PageableDefault(size = 20, sort = "fechaCreacion", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.status(HttpStatus.OK).body(service.getAll(estado, pageable).map(mapper::toResponseDTO));
    }

    /** Circuitos que esperan la aceptación de la empresa del usuario. */
    @GetMapping("/pendientes-aceptacion")
    @Operation(operationId = "listarCircuitosPendientesDeAceptacion", summary = "Pendientes de aceptación", description = "PENDIENTE_EMPRESAS donde tu empresa todavía no aceptó. Roles: DISTRIBUIDOR, FARMACIA (admin de la empresa). Reglas: R5.")
    @RespuestasError({400, 401, 403})
    @PreAuthorize("hasAnyRole('DISTRIBUIDOR', 'FARMACIA')")
    public ResponseEntity<Page<EnlaceCuitResponseDTO>> pendientesDeAceptacion(
            @ParameterObject @PageableDefault(size = 20, sort = "fechaCreacion", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.status(HttpStatus.OK).body(service.pendientesDeAceptacion(pageable).map(mapper::toResponseDTO));
    }

    /** Bandeja del inspector. */
    @GetMapping("/bandeja")
    @Operation(operationId = "bandejaCircuitos", summary = "Bandeja del inspector", description = "Solo PENDIENTE_INSPECTOR: de farmacias de tu provincia sin tomar, más los que tomaste o te asignaron. Roles: INSPECTOR. Reglas: R5.")
    @RespuestasError({400, 401, 403})
    @PreAuthorize("hasRole('INSPECTOR')")
    public ResponseEntity<Page<EnlaceCuitResponseDTO>> bandeja(
            @ParameterObject @PageableDefault(size = 20, sort = "fechaCreacion", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.status(HttpStatus.OK).body(service.bandeja(pageable).map(mapper::toResponseDTO));
    }

    /** Busca un circuito por id. */
    @GetMapping("/{id}")
    @Operation(operationId = "obtenerCircuito", summary = "Obtener un circuito", description = "404 si tu empresa no participa. Roles: SEDE_CENTRAL, INSPECTOR, LABORATORIO, DISTRIBUIDOR, FARMACIA.")
    @RespuestasError({400, 401, 403, 404})
    @PreAuthorize("hasAnyRole('SEDE_CENTRAL', 'INSPECTOR', 'LABORATORIO', 'DISTRIBUIDOR', 'FARMACIA')")
    public ResponseEntity<EnlaceCuitResponseDTO> getById(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.OK).body(mapper.toResponseDTO(service.getById(id)));
    }

    /** El DT del laboratorio propone un circuito por CUIT. */
    @PostMapping
    @Operation(operationId = "proponerCircuito", summary = "Proponer un circuito", description = "CUIT de la distribuidora y de la farmacia (HABILITADA). Exige director técnico. Un solo circuito vigente por par laboratorio–farmacia (R5). Código CIR-0001 generado por el servidor. Evento CIRCUITO_PROPUESTO. Roles: LABORATORIO (DT). Reglas: R5.")
    @ApiResponse(responseCode = "201", description = "Creado")
    @RespuestasError({400, 401, 403, 409})
    @PreAuthorize("hasRole('LABORATORIO')")
    public ResponseEntity<EnlaceCuitResponseDTO> proponer(@Valid @RequestBody EnlaceCuitRequestDTO dto) {
        EnlaceCuit creado = service.proponer(dto.getCuitDistribuidor(), dto.getCuitFarmacia());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponseDTO(creado));
    }

    /** La distribuidora o la farmacia acepta su parte. */
    @PostMapping("/{id}/aceptar")
    @Operation(operationId = "aceptarCircuito", summary = "Aceptar un circuito", description = "Tu empresa acepta su parte; con las dos aceptaciones pasa a PENDIENTE_INSPECTOR. Evento CIRCUITO_ACEPTADO. Roles: DISTRIBUIDOR, FARMACIA (admin). Reglas: R5.")
    @RespuestasError({400, 401, 403, 404, 409})
    @PreAuthorize("hasAnyRole('DISTRIBUIDOR', 'FARMACIA')")
    public ResponseEntity<EnlaceCuitResponseDTO> aceptar(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.OK).body(mapper.toResponseDTO(service.aceptar(id)));
    }

    /** La distribuidora o la farmacia rechaza el circuito. */
    @PostMapping("/{id}/rechazar-empresa")
    @Operation(operationId = "rechazarCircuitoPorEmpresa", summary = "Rechazar un circuito (empresa)", description = "PENDIENTE_EMPRESAS → RECHAZADO (definitivo), con motivo. Evento CIRCUITO_RECHAZADO. Roles: DISTRIBUIDOR, FARMACIA (admin). Reglas: R5.")
    @RespuestasError({400, 401, 403, 404, 409})
    @PreAuthorize("hasAnyRole('DISTRIBUIDOR', 'FARMACIA')")
    public ResponseEntity<EnlaceCuitResponseDTO> rechazarPorEmpresa(@PathVariable UUID id,
                                                                    @Valid @RequestBody MotivoRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.OK).body(mapper.toResponseDTO(service.rechazarPorEmpresa(id, dto.getMotivo())));
    }

    /** Un inspector de la provincia de la farmacia toma el circuito. */
    @PostMapping("/{id}/tomar")
    @Operation(operationId = "tomarCircuito", summary = "Tomar un circuito", description = "PENDIENTE_INSPECTOR sin revisor → el inspector queda como revisor. Evento CIRCUITO_TOMADO. Roles: INSPECTOR de la provincia de la farmacia. Reglas: R5.")
    @RespuestasError({400, 401, 403, 404, 409})
    @PreAuthorize("hasRole('INSPECTOR')")
    public ResponseEntity<EnlaceCuitResponseDTO> tomar(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.OK).body(mapper.toResponseDTO(service.tomar(id)));
    }

    /** La Sede asigna el circuito cuando la provincia de la farmacia no tiene inspectores. */
    @PostMapping("/{id}/asignar")
    @Operation(operationId = "asignarCircuito", summary = "Asignar un circuito", description = "Solo si la provincia de la farmacia no tiene inspectores ACTIVO. Evento CIRCUITO_ASIGNADO. Roles: SEDE_CENTRAL. Reglas: R5.")
    @RespuestasError({400, 401, 403, 404, 409})
    @PreAuthorize("hasRole('SEDE_CENTRAL')")
    public ResponseEntity<EnlaceCuitResponseDTO> asignar(@PathVariable UUID id,
                                                         @Valid @RequestBody AsignacionInspectorRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.OK).body(mapper.toResponseDTO(service.asignar(id, dto.getInspectorId())));
    }

    /** El revisor aprueba el circuito. */
    @PostMapping("/{id}/aprobar")
    @Operation(operationId = "aprobarCircuito", summary = "Aprobar un circuito", description = "PENDIENTE_INSPECTOR → APROBADO. Exige haberlo tomado (o tenerlo asignado) y las tres empresas HABILITADA. Evento CIRCUITO_APROBADO. Roles: INSPECTOR. Reglas: R5.")
    @RespuestasError({400, 401, 403, 404, 409})
    @PreAuthorize("hasRole('INSPECTOR')")
    public ResponseEntity<EnlaceCuitResponseDTO> aprobar(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.OK).body(mapper.toResponseDTO(service.aprobar(id)));
    }

    /** El revisor rechaza el circuito. */
    @PostMapping("/{id}/rechazar")
    @Operation(operationId = "rechazarCircuito", summary = "Rechazar un circuito (inspector)", description = "PENDIENTE_INSPECTOR → RECHAZADO (definitivo), con motivo; el laboratorio puede volver a proponer. Evento CIRCUITO_RECHAZADO. Roles: INSPECTOR. Reglas: R5.")
    @RespuestasError({400, 401, 403, 404, 409})
    @PreAuthorize("hasRole('INSPECTOR')")
    public ResponseEntity<EnlaceCuitResponseDTO> rechazar(@PathVariable UUID id, @Valid @RequestBody MotivoRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.OK).body(mapper.toResponseDTO(service.rechazarPorInspector(id, dto.getMotivo())));
    }

    /** Suspensión manual. */
    @PostMapping("/{id}/suspender")
    @Operation(operationId = "suspenderCircuito", summary = "Suspender un circuito", description = "APROBADO → SUSPENDIDO, con motivo. Evento CIRCUITO_SUSPENDIDO. Roles: INSPECTOR de la provincia de la farmacia, SEDE_CENTRAL. Reglas: R5.")
    @RespuestasError({400, 401, 403, 404, 409})
    @PreAuthorize("hasAnyRole('INSPECTOR', 'SEDE_CENTRAL')")
    public ResponseEntity<EnlaceCuitResponseDTO> suspender(@PathVariable UUID id, @Valid @RequestBody MotivoRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.OK).body(mapper.toResponseDTO(service.suspender(id, dto.getMotivo())));
    }

    /** Rehabilitación manual. */
    @PostMapping("/{id}/rehabilitar")
    @Operation(operationId = "rehabilitarCircuito", summary = "Rehabilitar un circuito", description = "SUSPENDIDO a mano → APROBADO, con las tres empresas HABILITADA (los suspendidos por una empresa vuelven al rehabilitarla). Evento CIRCUITO_REHABILITADO. Roles: INSPECTOR de la provincia de la farmacia, SEDE_CENTRAL. Reglas: R5.")
    @RespuestasError({400, 401, 403, 404, 409})
    @PreAuthorize("hasAnyRole('INSPECTOR', 'SEDE_CENTRAL')")
    public ResponseEntity<EnlaceCuitResponseDTO> rehabilitar(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.OK).body(mapper.toResponseDTO(service.rehabilitar(id)));
    }
}
