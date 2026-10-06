package com.medichain.modules.trazabilidad;

import com.medichain.modules.registroblockchain.VerificadorAnclajes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

/**
 * Controlador EventoTrazabilidadController en MediChain.
 * Expone solo la consulta de la cadena de eventos de trazabilidad: es un
 * registro append-only e inmutable que genera el propio backend, no la
 * API pública (sin POST/PUT/DELETE). Nunca devuelve la entidad JPA,
 * siempre EventoTrazabilidadResponseDTO.
 */
@RestController
@RequestMapping("/api/eventos-trazabilidad")
@Tag(name = "Eventos de trazabilidad", description = "Consulta de la cadena inmutable de eventos (solo lectura)")
public class EventoTrazabilidadController {

    private final EventoTrazabilidadService service;
    private final EventoTrazabilidadMapper mapper;
    private final VerificadorCadena verificadorCadena;
    private final VerificadorAnclajes verificadorAnclajes;

    @Autowired
    public EventoTrazabilidadController(EventoTrazabilidadService service, EventoTrazabilidadMapper mapper,
                                        VerificadorCadena verificadorCadena, VerificadorAnclajes verificadorAnclajes) {
        this.service = service;
        this.mapper = mapper;
        this.verificadorCadena = verificadorCadena;
        this.verificadorAnclajes = verificadorAnclajes;
    }

    /** Recalcula la cadena completa y devuelve si está íntegra. */
    @GetMapping("/verificacion")
    @Operation(summary = "Verificar la cadena", description = "1) Cadena local: recorre todos los eventos, recalcula cada hash y comprueba el encadenado y la numeración sin huecos (integra). 2) Blockchain: compara cada anclaje del contrato en Sepolia con el evento local de ese número (blockchain.estado VERIFICADA, ALTERADA, NO_CONSULTADA o NO_DISPONIBLE); detecta una alteración aunque se hayan recalculado todos los hashes. Roles: SEDE_CENTRAL, INSPECTOR.")
    @PreAuthorize("hasAnyRole('SEDE_CENTRAL', 'INSPECTOR')")
    public ResponseEntity<VerificacionCadenaResponseDTO> verificar() {
        VerificacionCadenaResponseDTO resultado = verificadorCadena.verificar();
        resultado.setBlockchain(verificadorAnclajes.verificar());
        resultado.setResumen(VerificadorAnclajes.resumen(resultado, resultado.getBlockchain()));
        return ResponseEntity.status(HttpStatus.OK).body(resultado);
    }

    /** Lista los eventos de trazabilidad de forma paginada. */
    @GetMapping
    @Operation(summary = "Listar eventos", description = "Devuelve una página de eventos de trazabilidad, del más reciente al más antiguo. Roles: SEDE_CENTRAL, INSPECTOR.")
    @PreAuthorize("hasAnyRole('SEDE_CENTRAL', 'INSPECTOR')")
    public ResponseEntity<Page<EventoTrazabilidadResponseDTO>> getAll(
            @ParameterObject @PageableDefault(size = 20, sort = "numero", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<EventoTrazabilidadResponseDTO> page = service.getAll(pageable).map(mapper::toResponseDTO);
        return ResponseEntity.status(HttpStatus.OK).body(page);
    }

    /** Busca un evento de trazabilidad por id. */
    @GetMapping("/{id}")
    @Operation(summary = "Obtener un evento", description = "Busca un evento de trazabilidad por su id. Roles: SEDE_CENTRAL, INSPECTOR.")
    @PreAuthorize("hasAnyRole('SEDE_CENTRAL', 'INSPECTOR')")
    public ResponseEntity<EventoTrazabilidadResponseDTO> getById(@PathVariable UUID id) {
        EventoTrazabilidadResponseDTO dto = mapper.toResponseDTO(service.getById(id));
        return ResponseEntity.status(HttpStatus.OK).body(dto);
    }
}
