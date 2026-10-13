package com.medichain.modules.trazabilidad;

import com.medichain.config.RespuestasError;
import com.medichain.modules.registroblockchain.VerificadorAnclajes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
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
import org.springframework.web.bind.annotation.RequestParam;
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
    private final TrazabilidadPorCodigoService trazabilidadPorCodigo;

    @Autowired
    public EventoTrazabilidadController(EventoTrazabilidadService service, EventoTrazabilidadMapper mapper,
                                        VerificadorCadena verificadorCadena, VerificadorAnclajes verificadorAnclajes,
                                        TrazabilidadPorCodigoService trazabilidadPorCodigo) {
        this.service = service;
        this.mapper = mapper;
        this.verificadorCadena = verificadorCadena;
        this.verificadorAnclajes = verificadorAnclajes;
        this.trazabilidadPorCodigo = trazabilidadPorCodigo;
    }

    /** Recalcula la cadena completa y devuelve si está íntegra. */
    @GetMapping("/verificacion")
    @Operation(operationId = "verificarCadena", summary = "Verificar la cadena", description = "1) Cadena local: recorre todos los eventos, recalcula cada hash y comprueba el encadenado y la numeración sin huecos (integra). 2) Blockchain: compara cada anclaje del contrato en Sepolia con el evento local de ese número (blockchain.estado VERIFICADA, ALTERADA, NO_CONSULTADA o NO_DISPONIBLE); detecta una alteración aunque se hayan recalculado todos los hashes. Roles: SEDE_CENTRAL, INSPECTOR. Reglas: R15.")
    @RespuestasError({401, 403})
    @PreAuthorize("hasAnyRole('SEDE_CENTRAL', 'INSPECTOR')")
    public ResponseEntity<VerificacionCadenaResponseDTO> verificar() {
        VerificacionCadenaResponseDTO resultado = verificadorCadena.verificar();
        resultado.setBlockchain(verificadorAnclajes.verificar());
        resultado.setResumen(VerificadorAnclajes.resumen(resultado, resultado.getBlockchain()));
        return ResponseEntity.status(HttpStatus.OK).body(resultado);
    }

    /** Lista los eventos de trazabilidad de forma paginada. */
    @GetMapping
    @Operation(operationId = "listarEventos", summary = "Listar eventos", description = "Devuelve una página de eventos de trazabilidad, del más reciente al más antiguo. Filtro opcional ?entidadId= (los eventos de esa entidad: lote, bulto, viaje, empresa…). Roles: SEDE_CENTRAL, INSPECTOR. Reglas: R15.")
    @RespuestasError({400, 401, 403})
    @PreAuthorize("hasAnyRole('SEDE_CENTRAL', 'INSPECTOR')")
    public ResponseEntity<Page<EventoTrazabilidadResponseDTO>> getAll(
            @Parameter(description = "Id de la entidad (opcional): solo los eventos de esa entidad") @RequestParam(required = false) UUID entidadId,
            @ParameterObject @PageableDefault(size = 20, sort = "numero", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<EventoTrazabilidadResponseDTO> page = service.getAll(entidadId, pageable).map(mapper::toResponseDTO);
        return ResponseEntity.status(HttpStatus.OK).body(page);
    }

    /** Trazabilidad por código: busca un lote, un bulto o una caja y devuelve sus eventos. */
    @GetMapping("/recorrido")
    @Operation(operationId = "buscarRecorridoPorCodigo", summary = "Trazabilidad por código", description = "Busca UNO de: ?lote= (código; con ?laboratorioId= opcional, porque el código solo es único por laboratorio), ?bulto= (código) o ?gtin= y ?serie= (una caja), y devuelve por cada coincidencia sus eventos (los del lote, bulto o caja y los de lo que lo rodea: medidas, viajes, el lote y el bulto de una caja), del más viejo al más nuevo, con el laboratorio de cada resultado. Solo lo que ya ves de cada entidad (la Sede y los inspectores, todo; cada empresa, lo suyo): lo ajeno responde 404. Roles: SEDE_CENTRAL, INSPECTOR, LABORATORIO, DISTRIBUIDOR, FARMACIA. Reglas: R15.")
    @RespuestasError({400, 401, 403, 404})
    @PreAuthorize("hasAnyRole('SEDE_CENTRAL', 'INSPECTOR', 'LABORATORIO', 'DISTRIBUIDOR', 'FARMACIA')")
    public ResponseEntity<RecorridoCodigoResponseDTO> recorrido(
            @Parameter(description = "Código del lote") @RequestParam(required = false) @Size(max = 50, message = "El código buscado no puede superar 50 caracteres") String lote,
            @Parameter(description = "Laboratorio del lote (opcional, solo con lote)") @RequestParam(required = false) UUID laboratorioId,
            @Parameter(description = "Código del bulto") @RequestParam(required = false) @Size(max = 50, message = "El código buscado no puede superar 50 caracteres") String bulto,
            @Parameter(description = "GTIN de la caja (con serie)") @RequestParam(required = false) @Size(max = 14, message = "El GTIN no puede superar 14 caracteres") String gtin,
            @Parameter(description = "Serie de la caja (con gtin)") @RequestParam(required = false) @Size(max = 20, message = "La serie no puede superar 20 caracteres") String serie) {
        RecorridoCodigoResponseDTO respuesta = new RecorridoCodigoResponseDTO();
        respuesta.setResultados(trazabilidadPorCodigo.buscar(lote, laboratorioId, bulto, gtin, serie));
        return ResponseEntity.status(HttpStatus.OK).body(respuesta);
    }

    /** Busca un evento de trazabilidad por id. */
    @GetMapping("/{id}")
    @Operation(operationId = "obtenerEvento", summary = "Obtener un evento", description = "Busca un evento de trazabilidad por su id. Roles: SEDE_CENTRAL, INSPECTOR. Reglas: R15.")
    @RespuestasError({400, 401, 403, 404})
    @PreAuthorize("hasAnyRole('SEDE_CENTRAL', 'INSPECTOR')")
    public ResponseEntity<EventoTrazabilidadResponseDTO> getById(@PathVariable UUID id) {
        EventoTrazabilidadResponseDTO dto = mapper.toResponseDTO(service.getById(id));
        return ResponseEntity.status(HttpStatus.OK).body(dto);
    }
}
