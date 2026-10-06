package com.medichain.modules.lote;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test unitario LoteRequestDTOTest en MediChain.
 * Validaciones de formato del registro de lote (400): exactamente una
 * forma de series, máximo 10.000, fechas y formato del código.
 */
class LoteRequestDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    /** DTO válido con cantidad. */
    private LoteRequestDTO valido() {
        LoteRequestDTO dto = new LoteRequestDTO();
        dto.setCodigo("L2026-0002");
        dto.setFechaFabricacion(LocalDate.now().minusDays(10));
        dto.setFechaVencimiento(LocalDate.now().plusYears(1));
        dto.setMedicamentoId(UUID.randomUUID());
        dto.setCantidad(20);
        return dto;
    }

    /** Campos con error. */
    private Set<String> camposConError(LoteRequestDTO dto) {
        return validator.validate(dto).stream().map(ConstraintViolation::getPropertyPath)
                .map(Object::toString).collect(Collectors.toSet());
    }

    @Test
    @DisplayName("Un DTO completo es válido")
    void dtoValido() {
        assertTrue(camposConError(valido()).isEmpty());
    }

    @Test
    @DisplayName("Series y cantidad juntas, o ninguna → error en 'series'")
    void exactamenteUnaForma() {
        LoteRequestDTO ambas = valido();
        ambas.setSeries(List.of("A1"));
        assertEquals(Set.of("series"), camposConError(ambas));

        LoteRequestDTO ninguna = valido();
        ninguna.setCantidad(null);
        assertEquals(Set.of("series"), camposConError(ninguna));
    }

    @Test
    @DisplayName("Más de 10.000 cajas (cantidad o lista) → error")
    void maximoDiezMil() {
        LoteRequestDTO cantidad = valido();
        cantidad.setCantidad(10_001);
        assertEquals(Set.of("cantidad"), camposConError(cantidad));

        LoteRequestDTO lista = valido();
        lista.setCantidad(null);
        lista.setSeries(Collections.nCopies(10_001, "A1"));
        assertEquals(Set.of("series"), camposConError(lista));
    }

    @Test
    @DisplayName("Vencimiento pasado o anterior a la fabricación; fabricación futura → error en el campo")
    void fechas() {
        LoteRequestDTO vencido = valido();
        vencido.setFechaVencimiento(LocalDate.now().minusDays(1));
        assertTrue(camposConError(vencido).contains("fechaVencimiento"));

        LoteRequestDTO futuro = valido();
        futuro.setFechaFabricacion(LocalDate.now().plusDays(1));
        assertTrue(camposConError(futuro).contains("fechaFabricacion"));
    }

    @Test
    @DisplayName("Código: más de 12 caracteres o con espacios → error")
    void formatoDelCodigo() {
        LoteRequestDTO largo = valido();
        largo.setCodigo("L2026-0002-XYZ");
        assertEquals(Set.of("codigo"), camposConError(largo));

        LoteRequestDTO espacios = valido();
        espacios.setCodigo("L 2026");
        assertEquals(Set.of("codigo"), camposConError(espacios));
    }
}
