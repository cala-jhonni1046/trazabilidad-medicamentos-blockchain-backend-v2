package com.medichain.modules.registroblockchain;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

/**
 * Controlador RegistroBlockchainController en MediChain (R15).
 * Consulta de los anclajes en Ethereum Sepolia, el tablero de estado del
 * anclaje y "anclar ya" (para la demo, sin esperar la tarea de 5 minutos).
 * Los anclajes son append-only: no hay PUT ni DELETE. Nunca devuelve la
 * entidad JPA.
 */
@RestController
@RequestMapping("/api/registros-blockchain")
@Tag(name = "Registros blockchain", description = "Anclaje del último hash de la cadena en el contrato MediChainAnchor (Ethereum Sepolia): consulta, estado y anclar ya")
public class RegistroBlockchainController {

    private final RegistroBlockchainService service;
    private final RegistroBlockchainMapper mapper;

    @Autowired
    public RegistroBlockchainController(RegistroBlockchainService service, RegistroBlockchainMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    /** Lista los anclajes de forma paginada. */
    @GetMapping
    @Operation(summary = "Listar anclajes", description = "Página de anclajes (más nuevos primero) con estado, transacción, bloque, confirmaciones, gas, costo y enlace a Etherscan. Roles: SEDE_CENTRAL, INSPECTOR.")
    @PreAuthorize("hasAnyRole('SEDE_CENTRAL', 'INSPECTOR')")
    public ResponseEntity<Page<RegistroBlockchainResponseDTO>> getAll(
            @ParameterObject @PageableDefault(size = 20, sort = "fechaCreacion", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<RegistroBlockchainResponseDTO> page = service.getAll(pageable).map(mapper::toResponseDTO);
        return ResponseEntity.status(HttpStatus.OK).body(page);
    }

    /** Tablero del anclaje: red, contrato, billetera, saldo, costo y último anclaje. */
    @GetMapping("/estado")
    @Operation(summary = "Estado del anclaje", description = "Red, contrato y billetera (con enlaces a Etherscan), saldo, comisión actual; gas REAL del próximo anclaje (eth_estimateGas; primerAnclaje indica si es el primero del contrato, que cuesta más), límite de gas (estimación + 30 % dentro de [gasMinimo; gasMaximo]), costo, cuántos anclajes alcanzan y saldo mínimo de la tarea automática; si la tarea está frenada (FALLIDO por revert / sin gas / gas sobre el máximo, o saldo) y por qué, en mensaje; último evento local y anclado, anclaje en curso. Con el anclaje deshabilitado devuelve solo lo local. Roles: SEDE_CENTRAL, INSPECTOR.")
    @PreAuthorize("hasAnyRole('SEDE_CENTRAL', 'INSPECTOR')")
    public ResponseEntity<EstadoAnclajeResponseDTO> estado() {
        return ResponseEntity.status(HttpStatus.OK).body(service.estado());
    }

    /** Ancla ya el hash del último evento. */
    @PostMapping("/anclar")
    @Operation(summary = "Anclar ya", description = "Ancla en Sepolia el hash del último evento sin esperar la tarea de 5 minutos (para la demo). Antes de enviar simula y estima el gas: si el contrato lo rechazaría o la estimación + 30 % supera gas-maximo, NO envía y responde 202 con el anclaje FALLIDO y el motivo (sin gastar). Si no, 202 con el anclaje ENVIADO (transactionHash y enlace a Etherscan); CONFIRMADO llega solo, unos 40 s después (GET /api/registros-blockchain/{id}). Destraba la tarea automática si estaba frenada. 409 ANCLAJE_DESHABILITADO, ANCLAJE_EN_CURSO, SIN_EVENTOS_NUEVOS, SALDO_INSUFICIENTE (no alcanza para un anclaje), o R15 si la cadena local no coincide con el último anclaje del contrato. 503 si Sepolia no responde. Roles: SEDE_CENTRAL.")
    @PreAuthorize("hasRole('SEDE_CENTRAL')")
    public ResponseEntity<RegistroBlockchainResponseDTO> anclar() {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(mapper.toResponseDTO(service.anclarAhora()));
    }

    /** Busca un anclaje por id. */
    @GetMapping("/{id}")
    @Operation(summary = "Obtener un anclaje", description = "Busca un anclaje por su id. Roles: SEDE_CENTRAL, INSPECTOR.")
    @PreAuthorize("hasAnyRole('SEDE_CENTRAL', 'INSPECTOR')")
    public ResponseEntity<RegistroBlockchainResponseDTO> getById(@PathVariable UUID id) {
        RegistroBlockchainResponseDTO dto = mapper.toResponseDTO(service.getById(id));
        return ResponseEntity.status(HttpStatus.OK).body(dto);
    }
}
