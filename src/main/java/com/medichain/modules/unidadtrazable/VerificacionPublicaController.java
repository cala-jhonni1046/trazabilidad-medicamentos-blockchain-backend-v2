package com.medichain.modules.unidadtrazable;

import com.medichain.utils.validacion.Gtin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador VerificacionPublicaController en MediChain.
 * Verificación PÚBLICA (sin token) de una caja por GTIN + serie, lo que
 * codifica el DataMatrix: (01) GTIN (21) serie. Siempre 200 con un estado
 * (también NO_EXISTE); 400 solo si el GTIN o la serie están mal formados.
 */
@RestController
@RequestMapping("/api/verificacion")
@Validated
@Tag(name = "Verificación pública", description = "Verificá una caja por GTIN + serie, sin token (lo que usa el paciente)")
public class VerificacionPublicaController {

    private final VerificacionPublicaService service;

    @Autowired
    public VerificacionPublicaController(VerificacionPublicaService service) {
        this.service = service;
    }

    /** Verifica una caja por GTIN + serie. */
    @GetMapping
    @Operation(summary = "Verificar una caja", description = "Público, sin token. Devuelve producto, laboratorio, lote, vencimiento, el estado "
            + "(APTA, YA_DISPENSADA, BLOQUEADA, ROBADA, EN_DISTRIBUCION o NO_EXISTE) con un mensaje y el recorrido resumido. "
            + "Nunca datos del paciente ni de la dispensación. El anclaje en blockchain se completa en el paso 8.")
    public ResponseEntity<VerificacionPublicaResponseDTO> verificar(
            @Parameter(description = "GTIN (14 dígitos, con dígito verificador GS1)", example = "07799000001010")
            @RequestParam("gtin") @NotBlank @Gtin String gtin,
            @Parameter(description = "Serie de la caja (alfanumérica, hasta 20)", example = "L20260001S000011")
            @RequestParam("serie") @NotBlank
            @Pattern(regexp = "[A-Za-z0-9]{1,20}", message = "La serie es alfanumérica de hasta 20 caracteres") String serie) {
        return ResponseEntity.status(HttpStatus.OK).body(service.verificar(gtin, serie));
    }
}
