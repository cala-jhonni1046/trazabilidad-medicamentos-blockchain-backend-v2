package com.medichain.modules.dispensacion;

import com.medichain.modules.empresa.MotivoRequestDTO;
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
 * Controlador DispensacionController en MediChain.
 * Expone el alta y la consulta de dispensaciones. Nunca recibe ni
 * devuelve la entidad JPA, siempre DispensacionRequestDTO/ResponseDTO.
 */
@RestController
@RequestMapping("/api/dispensaciones")
@Tag(name = "Dispensaciones", description = "Alta y consulta de dispensaciones a pacientes")
public class DispensacionController {

    private final DispensacionService service;
    private final DispensacionMapper mapper;

    @Autowired
    public DispensacionController(DispensacionService service, DispensacionMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    /** Lista las dispensaciones de forma paginada. */
    @GetMapping
    @Operation(summary = "Listar dispensaciones", description = "Devuelve una página de dispensaciones. Roles: SEDE_CENTRAL, INSPECTOR, FARMACIA.")
    @PreAuthorize("hasAnyRole('SEDE_CENTRAL', 'INSPECTOR', 'FARMACIA')")
    public ResponseEntity<Page<DispensacionResponseDTO>> getAll(
            @ParameterObject @PageableDefault(size = 20, sort = "fechaCreacion", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<DispensacionResponseDTO> page = service.getAll(pageable).map(mapper::toResponseDTO);
        return ResponseEntity.status(HttpStatus.OK).body(page);
    }

    /** Busca una dispensación por id. */
    @GetMapping("/{id}")
    @Operation(summary = "Obtener una dispensación", description = "Busca una dispensación por su id. Roles: SEDE_CENTRAL, INSPECTOR, FARMACIA.")
    @PreAuthorize("hasAnyRole('SEDE_CENTRAL', 'INSPECTOR', 'FARMACIA')")
    public ResponseEntity<DispensacionResponseDTO> getById(@PathVariable UUID id) {
        DispensacionResponseDTO dto = mapper.toResponseDTO(service.getById(id));
        return ResponseEntity.status(HttpStatus.OK).body(dto);
    }

    /** Dispensa una caja escaneada (GTIN + serie). */
    @PostMapping
    @Operation(summary = "Dispensar una caja", description = "Escaneás GTIN + serie de una caja EN_STOCK en tu farmacia. Cobertura: particular, u obra social + afiliado. "
            + "DNI opcional: se guarda SOLO enmascarado (*****006). Ya dispensada → 409 R11 (INTENTO_DUPLICADO); robada → 409 R14 (SERIE_ROBADA); "
            + "bloqueada → 409 R10; no está en tu stock → 404. El evento DISPENSACION no lleva ningún dato del paciente. Roles: FARMACIA.")
    @PreAuthorize("hasRole('FARMACIA')")
    public ResponseEntity<DispensacionResponseDTO> dispensar(@Valid @RequestBody DispensacionRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponseDTO(service.dispensar(dto)));
    }

    /** Anula una dispensación dentro de las 2 h. */
    @PostMapping("/{id}/anular")
    @Operation(summary = "Anular una dispensación", description = "Solo la misma farmacia, dentro de las 2 horas (si no, 409 R11), con motivo obligatorio. "
            + "La caja vuelve a EN_STOCK. Evento ANULACION_DISPENSA (al evento va solo el hash del motivo). Roles: FARMACIA.")
    @PreAuthorize("hasRole('FARMACIA')")
    public ResponseEntity<DispensacionResponseDTO> anular(@PathVariable UUID id, @Valid @RequestBody MotivoRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.OK).body(mapper.toResponseDTO(service.anular(id, dto.getMotivo())));
    }
}
