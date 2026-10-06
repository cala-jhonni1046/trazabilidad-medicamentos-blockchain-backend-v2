package com.medichain.modules.reporteciudadano;

import com.medichain.utils.seguridad.UsuarioActual;
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
 * Controlador ReporteCiudadanoController en MediChain.
 * El paciente reporta una caja (GTIN + serie) y ve sus reportes; un
 * inspector de la provincia los toma y los cierra. La respuesta depende de
 * quién lee: el paciente no ve la conclusión ni datos internos de la
 * investigación.
 */
@RestController
@RequestMapping("/api/reportes-ciudadanos")
@Tag(name = "Reportes ciudadanos", description = "Reportes de pacientes por GTIN + serie: presentar, bandeja del inspector, tomar y cerrar")
public class ReporteCiudadanoController {

    private final ReporteCiudadanoService service;
    private final ReporteCiudadanoMapper mapper;
    private final UsuarioActual usuarioActual;

    @Autowired
    public ReporteCiudadanoController(ReporteCiudadanoService service, ReporteCiudadanoMapper mapper,
                                      UsuarioActual usuarioActual) {
        this.service = service;
        this.mapper = mapper;
        this.usuarioActual = usuarioActual;
    }

    /** Convierte al DTO según el rol de quien lee. */
    private ReporteCiudadanoResponseDTO dto(ReporteCiudadano reporte) {
        return mapper.toResponseDTO(reporte, usuarioActual.obtener().getRol());
    }

    /** Lista los reportes visibles para el usuario. */
    @GetMapping
    @Operation(summary = "Listar reportes", description = "PACIENTE: los suyos (estado y fechas). SEDE e INSPECTOR: todos. Roles: SEDE_CENTRAL, INSPECTOR, PACIENTE.")
    @PreAuthorize("hasAnyRole('SEDE_CENTRAL', 'INSPECTOR', 'PACIENTE')")
    public ResponseEntity<Page<ReporteCiudadanoResponseDTO>> getAll(
            @ParameterObject @PageableDefault(size = 20, sort = "fechaReporte", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.status(HttpStatus.OK).body(service.getAll(pageable).map(this::dto));
    }

    /** Bandeja del inspector. */
    @GetMapping("/bandeja")
    @Operation(summary = "Bandeja de reportes", description = "ABIERTO de tu provincia, más los que investigás. Roles: INSPECTOR.")
    @PreAuthorize("hasRole('INSPECTOR')")
    public ResponseEntity<Page<ReporteCiudadanoResponseDTO>> bandeja(
            @ParameterObject @PageableDefault(size = 20, sort = "fechaReporte", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.status(HttpStatus.OK).body(service.bandeja(pageable).map(this::dto));
    }

    /** Busca un reporte por id. */
    @GetMapping("/{id}")
    @Operation(summary = "Obtener un reporte", description = "El paciente solo ve los suyos (ajeno → 404). Roles: SEDE_CENTRAL, INSPECTOR, PACIENTE.")
    @PreAuthorize("hasAnyRole('SEDE_CENTRAL', 'INSPECTOR', 'PACIENTE')")
    public ResponseEntity<ReporteCiudadanoResponseDTO> getById(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.OK).body(dto(service.getById(id)));
    }

    /** El paciente presenta un reporte. */
    @PostMapping
    @Operation(summary = "Reportar una caja", description = "GTIN + serie escaneados (la caja puede no existir), motivo, provincia donde la conseguiste y descripción opcional. "
            + "Código REP-0001 generado. Ni tus datos ni la descripción van a la cadena. Evento REPORTE_CIUDADANO. Roles: PACIENTE.")
    @PreAuthorize("hasRole('PACIENTE')")
    public ResponseEntity<ReporteCiudadanoResponseDTO> reportar(@Valid @RequestBody ReporteCiudadanoRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(dto(service.reportar(dto)));
    }

    /** Un inspector toma el reporte. */
    @PostMapping("/{id}/tomar")
    @Operation(summary = "Tomar un reporte", description = "ABIERTO → EN_INVESTIGACION. Reporte de tu provincia (si no, 404). Evento REPORTE_TOMADO. Roles: INSPECTOR.")
    @PreAuthorize("hasRole('INSPECTOR')")
    public ResponseEntity<ReporteCiudadanoResponseDTO> tomar(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.OK).body(dto(service.tomar(id)));
    }

    /** El inspector que investiga cierra el reporte. */
    @PostMapping("/{id}/cerrar")
    @Operation(summary = "Cerrar un reporte", description = "EN_INVESTIGACION → CERRADO, con conclusión (exige haberlo tomado). El paciente no ve la conclusión; al evento va su hash. "
            + "Evento REPORTE_CERRADO. Roles: INSPECTOR.")
    @PreAuthorize("hasRole('INSPECTOR')")
    public ResponseEntity<ReporteCiudadanoResponseDTO> cerrar(@PathVariable UUID id, @Valid @RequestBody ConclusionRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.OK).body(dto(service.cerrar(id, dto.getConclusion())));
    }
}
