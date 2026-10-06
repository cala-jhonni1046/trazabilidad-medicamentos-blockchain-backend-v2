package com.medichain.modules.empresa;

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
import org.springframework.http.MediaType;
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
 * Controlador EmpresaController en MediChain.
 * Consulta de empresas y endpoints de acción de la habilitación
 * (POST /api/empresas/{id}/&lt;accion&gt;). El alta es pública y está en
 * RegistroController. Nunca devuelve la entidad JPA.
 */
@RestController
@RequestMapping("/api/empresas")
@Tag(name = "Empresas", description = "Consulta de empresas y acciones de habilitación")
public class EmpresaController {

    private final EmpresaService service;
    private final EmpresaMapper mapper;

    @Autowired
    public EmpresaController(EmpresaService service, EmpresaMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    /** Lista las empresas de forma paginada. */
    @GetMapping
    @Operation(summary = "Listar empresas", description = "Devuelve una página de empresas. Roles: SEDE_CENTRAL, INSPECTOR, LABORATORIO, DISTRIBUIDOR, FARMACIA.")
    @PreAuthorize("hasAnyRole('SEDE_CENTRAL', 'INSPECTOR', 'LABORATORIO', 'DISTRIBUIDOR', 'FARMACIA')")
    public ResponseEntity<Page<EmpresaResponseDTO>> getAll(
            @ParameterObject @PageableDefault(size = 20, sort = "fechaCreacion", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<EmpresaResponseDTO> page = service.getAll(pageable).map(mapper::toResponseDTO);
        return ResponseEntity.status(HttpStatus.OK).body(page);
    }

    /** Bandeja del inspector: solicitudes PENDIENTE de su provincia y las que tiene tomadas o asignadas. */
    @GetMapping("/bandeja")
    @Operation(summary = "Bandeja del inspector", description = "Solicitudes PENDIENTE de la provincia del inspector sin tomar, más las que tomó o le asignaron. Roles: INSPECTOR.")
    @PreAuthorize("hasRole('INSPECTOR')")
    public ResponseEntity<Page<EmpresaResponseDTO>> bandeja(
            @ParameterObject @PageableDefault(size = 20, sort = "fechaCreacion", direction = Sort.Direction.ASC) Pageable pageable) {
        Page<EmpresaResponseDTO> page = service.bandeja(pageable).map(mapper::toResponseDTO);
        return ResponseEntity.status(HttpStatus.OK).body(page);
    }

    /** Busca una distribuidora o farmacia HABILITADA por CUIT, para armar un circuito. */
    @GetMapping("/por-cuit")
    @Operation(summary = "Buscar empresa por CUIT", description = "Para armar un circuito: CUIT con o sin guiones y tipo DISTRIBUIDOR o FARMACIA. Devuelve una sola empresa HABILITADA (resumen) o 404. Roles: LABORATORIO.")
    @PreAuthorize("hasRole('LABORATORIO')")
    public ResponseEntity<EmpresaResumenDTO> buscarPorCuit(@RequestParam("cuit") String cuit,
                                                           @RequestParam("tipo") TipoEmpresa tipo) {
        return ResponseEntity.status(HttpStatus.OK).body(mapper.toResumenDTO(service.buscarPorCuit(cuit, tipo)));
    }

    /** Busca una empresa por id. */
    @GetMapping("/{id}")
    @Operation(summary = "Obtener una empresa", description = "Busca una empresa por su id. Roles: SEDE_CENTRAL, INSPECTOR, LABORATORIO, DISTRIBUIDOR, FARMACIA.")
    @PreAuthorize("hasAnyRole('SEDE_CENTRAL', 'INSPECTOR', 'LABORATORIO', 'DISTRIBUIDOR', 'FARMACIA')")
    public ResponseEntity<EmpresaResponseDTO> getById(@PathVariable UUID id) {
        EmpresaResponseDTO dto = mapper.toResponseDTO(service.getById(id));
        return ResponseEntity.status(HttpStatus.OK).body(dto);
    }

    /** Descarga el PDF de habilitación de la empresa. */
    @GetMapping(value = "/{id}/documento", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Descargar el PDF de habilitación", description = "Roles: SEDE_CENTRAL; INSPECTOR de la provincia de la empresa o su revisor.")
    @PreAuthorize("hasAnyRole('SEDE_CENTRAL', 'INSPECTOR')")
    public ResponseEntity<byte[]> documento(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_PDF).body(service.documento(id));
    }

    /** El inspector toma una solicitud de su provincia. */
    @PostMapping("/{id}/tomar")
    @Operation(summary = "Tomar una solicitud", description = "PENDIENTE sin revisor → el inspector queda como revisor. Evento SOLICITUD_TOMADA. Roles: INSPECTOR de la provincia de la empresa.")
    @PreAuthorize("hasRole('INSPECTOR')")
    public ResponseEntity<EmpresaResponseDTO> tomar(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.OK).body(mapper.toResponseDTO(service.tomar(id)));
    }

    /** La Sede asigna la solicitud a un inspector cuando la provincia no tiene inspectores (R2). */
    @PostMapping("/{id}/asignar")
    @Operation(summary = "Asignar una solicitud", description = "Solo si la provincia de la empresa no tiene inspectores activos (R2). Evento SOLICITUD_ASIGNADA. Roles: SEDE_CENTRAL.")
    @PreAuthorize("hasRole('SEDE_CENTRAL')")
    public ResponseEntity<EmpresaResponseDTO> asignar(@PathVariable UUID id,
                                                      @Valid @RequestBody AsignacionInspectorRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.OK).body(mapper.toResponseDTO(service.asignar(id, dto.getInspectorId())));
    }

    /** El inspector revisor habilita la empresa. */
    @PostMapping("/{id}/habilitar")
    @Operation(summary = "Habilitar una empresa", description = "PENDIENTE → HABILITADA. Exige haber tomado o tener asignada la solicitud. Evento HABILITACION_APROBADA. Roles: INSPECTOR.")
    @PreAuthorize("hasRole('INSPECTOR')")
    public ResponseEntity<EmpresaResponseDTO> habilitar(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.OK).body(mapper.toResponseDTO(service.habilitar(id)));
    }

    /** El inspector revisor rechaza la solicitud con motivo. */
    @PostMapping("/{id}/rechazar")
    @Operation(summary = "Rechazar una solicitud", description = "PENDIENTE → RECHAZADA, con motivo obligatorio. Evento HABILITACION_RECHAZADA. Roles: INSPECTOR.")
    @PreAuthorize("hasRole('INSPECTOR')")
    public ResponseEntity<EmpresaResponseDTO> rechazar(@PathVariable UUID id, @Valid @RequestBody MotivoRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.OK).body(mapper.toResponseDTO(service.rechazar(id, dto.getMotivo())));
    }

    /** La Sede suspende la empresa y sus circuitos aprobados. */
    @PostMapping("/{id}/suspender")
    @Operation(summary = "Suspender una empresa", description = "HABILITADA → SUSPENDIDA, con motivo obligatorio; sus circuitos APROBADO pasan a SUSPENDIDO. Eventos EMPRESA_SUSPENDIDA y CIRCUITO_SUSPENDIDO. Roles: SEDE_CENTRAL.")
    @PreAuthorize("hasRole('SEDE_CENTRAL')")
    public ResponseEntity<EmpresaResponseDTO> suspender(@PathVariable UUID id, @Valid @RequestBody MotivoRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.OK).body(mapper.toResponseDTO(service.suspender(id, dto.getMotivo())));
    }

    /** La Sede rehabilita la empresa y los circuitos que se suspendieron por ella. */
    @PostMapping("/{id}/rehabilitar")
    @Operation(summary = "Rehabilitar una empresa", description = "SUSPENDIDA → HABILITADA; vuelven a APROBADO los circuitos suspendidos por cascada cuyas tres empresas estén HABILITADA. Eventos EMPRESA_REHABILITADA y CIRCUITO_REHABILITADO. Roles: SEDE_CENTRAL.")
    @PreAuthorize("hasRole('SEDE_CENTRAL')")
    public ResponseEntity<EmpresaResponseDTO> rehabilitar(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.OK).body(mapper.toResponseDTO(service.rehabilitar(id)));
    }
}
