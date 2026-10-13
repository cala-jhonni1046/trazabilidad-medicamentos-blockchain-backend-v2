package com.medichain.modules.tablero;

import com.medichain.config.RespuestasError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador TableroController en MediChain.
 * Totales para el tablero de la Sede y para las insignias de la bandeja del
 * inspector, en un solo GET cada uno. Solo lectura: no hay estados que cambiar.
 */
@RestController
@RequestMapping("/api/tablero")
@Tag(name = "Tablero", description = "Totales para el tablero de la Sede y los pendientes de la bandeja del inspector")
public class TableroController {

    private final TableroService service;

    @Autowired
    public TableroController(TableroService service) {
        this.service = service;
    }

    /** Totales del tablero de la Sede. */
    @GetMapping("/resumen")
    @Operation(operationId = "obtenerResumenTablero", summary = "Resumen del tablero de la Sede", description = "Totales de todo el sistema: empresas, empresas pendientes, inspectores activos, circuitos aprobados, cuarentenas activas, eventos de la cadena y anclajes CONFIRMADO. Roles: SEDE_CENTRAL.")
    @RespuestasError({401, 403})
    @PreAuthorize("hasRole('SEDE_CENTRAL')")
    public ResponseEntity<TableroResumenResponseDTO> resumen() {
        return ResponseEntity.status(HttpStatus.OK).body(service.resumen());
    }

    /** Pendientes de cada bandeja del inspector. */
    @GetMapping("/bandeja-inspector")
    @Operation(operationId = "obtenerBandejaInspector", summary = "Pendientes de la bandeja del inspector", description = "Cuántos pendientes tiene el inspector en cada bandeja (empresas, circuitos, lotes biológicos, cuarentenas y reportes): el mismo total que cada listado de bandeja, para mostrar insignias sin pedir las cinco listas. Roles: INSPECTOR. Reglas: R12.")
    @RespuestasError({401, 403})
    @PreAuthorize("hasRole('INSPECTOR')")
    public ResponseEntity<BandejaInspectorResponseDTO> bandejaInspector() {
        return ResponseEntity.status(HttpStatus.OK).body(service.bandejaInspector());
    }
}
