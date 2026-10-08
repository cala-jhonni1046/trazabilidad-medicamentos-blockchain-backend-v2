package com.medichain.modules.unidadtrazable;

import com.medichain.config.RespuestasError;
import com.medichain.modules.cuarentena.Bloqueo;
import com.medichain.modules.cuarentena.EvaluadorBloqueo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controlador UnidadTrazableController en MediChain.
 * Consulta de cajas (nacen con su lote en POST /api/lotes) y devolución (R14).
 * Nunca devuelve la entidad JPA, siempre UnidadTrazableResponseDTO.
 */
@RestController
@RequestMapping("/api/unidades-trazables")
@Tag(name = "Unidades trazables", description = "Alta y consulta de unidades trazables individuales")
public class UnidadTrazableController {

    private final UnidadTrazableService service;
    private final UnidadTrazableMapper mapper;
    private final EvaluadorBloqueo evaluadorBloqueo;

    @Autowired
    public UnidadTrazableController(UnidadTrazableService service, UnidadTrazableMapper mapper,
                                    EvaluadorBloqueo evaluadorBloqueo) {
        this.service = service;
        this.mapper = mapper;
        this.evaluadorBloqueo = evaluadorBloqueo;
    }

    /** Lista las unidades trazables de forma paginada. */
    @GetMapping
    @Operation(operationId = "listarCajas", summary = "Listar unidades trazables", description = "Devuelve una página de unidades trazables. Incluye el indicador de bloqueo R10 (bloqueado, motivoBloqueo, mensajeBloqueo). Roles: SEDE_CENTRAL, INSPECTOR, LABORATORIO, DISTRIBUIDOR, FARMACIA. Reglas: R10.")
    @RespuestasError({400, 401, 403})
    @PreAuthorize("hasAnyRole('SEDE_CENTRAL', 'INSPECTOR', 'LABORATORIO', 'DISTRIBUIDOR', 'FARMACIA')")
    public ResponseEntity<Page<UnidadTrazableResponseDTO>> getAll(
            @ParameterObject @PageableDefault(size = 20, sort = "fechaCreacion", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<UnidadTrazableResponseDTO> page = pagina(service.getAll(pageable));
        return ResponseEntity.status(HttpStatus.OK).body(page);
    }

    /** Busca una unidad trazable por id. */
    @GetMapping("/{id}")
    @Operation(operationId = "obtenerCaja", summary = "Obtener una unidad trazable", description = "Busca una unidad trazable por su id. Incluye el indicador de bloqueo R10 (bloqueado, motivoBloqueo, mensajeBloqueo). Roles: SEDE_CENTRAL, INSPECTOR, LABORATORIO, DISTRIBUIDOR, FARMACIA. Reglas: R10.")
    @RespuestasError({400, 401, 403, 404})
    @PreAuthorize("hasAnyRole('SEDE_CENTRAL', 'INSPECTOR', 'LABORATORIO', 'DISTRIBUIDOR', 'FARMACIA')")
    public ResponseEntity<UnidadTrazableResponseDTO> getById(@PathVariable UUID id) {
        UnidadTrazableResponseDTO dto = respuesta(service.getById(id));
        return ResponseEntity.status(HttpStatus.OK).body(dto);
    }

    /** Devuelve una caja del stock de la farmacia (R14). */
    @PostMapping("/devolucion")
    @Operation(operationId = "devolverCaja", summary = "Devolver una caja", description = "La farmacia que tiene la caja EN_STOCK la devuelve (GTIN + serie, motivo DANADA / VENCIDA / RETIRO_DEL_MERCADO / OTRO). "
            + "Queda DEVUELTA y no vuelve a dispensarse. Evento DEVOLUCION. Roles: FARMACIA. Reglas: R14.")
    @RespuestasError({400, 401, 403, 404, 409})
    @PreAuthorize("hasRole('FARMACIA')")
    public ResponseEntity<UnidadTrazableResponseDTO> devolver(@Valid @RequestBody DevolucionRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.OK).body(respuesta(service.devolver(dto)));
    }

    /** DTO de una caja con su bloqueo R10. */
    private UnidadTrazableResponseDTO respuesta(UnidadTrazable caja) {
        return mapper.toResponseDTO(caja, evaluadorBloqueo.bloqueosDeCajas(List.of(caja)).get(caja.getId()));
    }

    /** Página de cajas con el bloqueo R10 de cada una, calculado para toda la página de una vez (sin N+1). */
    private Page<UnidadTrazableResponseDTO> pagina(Page<UnidadTrazable> cajas) {
        Map<UUID, Bloqueo> bloqueos = evaluadorBloqueo.bloqueosDeCajas(cajas.getContent());
        return cajas.map(caja -> mapper.toResponseDTO(caja, bloqueos.get(caja.getId())));
    }
}
