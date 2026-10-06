package com.medichain.modules.trazabilidad;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;

/**
 * Test unitario VerificadorCadenaTest en MediChain.
 * Arma cadenas en memoria y simula el repositorio por bloques: cadena
 * íntegra, dato alterado, hueco, hashAnterior cambiado y varios bloques.
 */
@ExtendWith(MockitoExtension.class)
class VerificadorCadenaTest {

    @Mock
    private EventoTrazabilidadRepository eventoRepository;

    @Mock
    private CadenaEstadoRepository cadenaEstadoRepository;

    /** Arma una cadena válida de n eventos. */
    private List<EventoTrazabilidad> cadena(int n) {
        List<EventoTrazabilidad> eventos = new ArrayList<>();
        String anterior = EventoTrazabilidad.GENESIS;
        for (long i = 1; i <= n; i++) {
            EventoTrazabilidad evento = new EventoTrazabilidad(i, TipoEvento.BULTO_ARMADO,
                    Instant.parse("2026-01-01T00:00:00Z").plusSeconds(i), "Bulto", UUID.randomUUID(),
                    JsonCanonico.escribir(Map.of("codigo", "BUL-" + i)), null, null, anterior);
            eventos.add(evento);
            anterior = evento.getHash();
        }
        return eventos;
    }

    /** Simula el repositorio por bloques sobre la lista y fija el estado de la cadena. */
    private VerificadorCadena verificadorSobre(List<EventoTrazabilidad> eventos) {
        lenient().when(eventoRepository.findByNumeroGreaterThanOrderByNumeroAsc(anyLong(), any(Limit.class)))
                .thenAnswer(inv -> {
                    long desde = inv.getArgument(0);
                    int max = ((Limit) inv.getArgument(1)).max();
                    return eventos.stream().filter(e -> e.getNumero() > desde).limit(max).toList();
                });
        EventoTrazabilidad ultimo = eventos.isEmpty() ? null : eventos.get(eventos.size() - 1);
        CadenaEstado estado = new CadenaEstado(CadenaEstado.PRINCIPAL,
                ultimo == null ? 0L : ultimo.getNumero(),
                ultimo == null ? EventoTrazabilidad.GENESIS : ultimo.getHash());
        lenient().when(cadenaEstadoRepository.findFirstByNombre(CadenaEstado.PRINCIPAL)).thenReturn(Optional.of(estado));
        return new VerificadorCadena(eventoRepository, cadenaEstadoRepository);
    }

    @Test
    @DisplayName("Cadena íntegra → integra=true con todos los eventos verificados")
    void cadenaIntegra() {
        VerificacionCadenaResponseDTO resultado = verificadorSobre(cadena(5)).verificar();

        assertTrue(resultado.isIntegra());
        assertEquals(5, resultado.getEventosVerificados());
        assertNull(resultado.getPrimerNumeroRoto());
    }

    @Test
    @DisplayName("Cadena vacía (solo GENESIS) → íntegra")
    void cadenaVacia() {
        assertTrue(verificadorSobre(List.of()).verificar().isIntegra());
    }

    @Test
    @DisplayName("Datos alterados en el evento 3 → rota en el 3")
    void datosAlteradosSeDetectan() {
        List<EventoTrazabilidad> eventos = cadena(5);
        ReflectionTestUtils.setField(eventos.get(2), "datosJson", "{\"codigo\":\"BUL-TRUCHO\"}");

        VerificacionCadenaResponseDTO resultado = verificadorSobre(eventos).verificar();

        assertFalse(resultado.isIntegra());
        assertEquals(3L, resultado.getPrimerNumeroRoto());
        assertEquals(2, resultado.getEventosVerificados());
    }

    @Test
    @DisplayName("Falta el evento 3 (hueco) → rota en el 3")
    void huecoSeDetecta() {
        List<EventoTrazabilidad> eventos = cadena(5);
        eventos.remove(2);

        VerificacionCadenaResponseDTO resultado = verificadorSobre(eventos).verificar();

        assertFalse(resultado.isIntegra());
        assertEquals(3L, resultado.getPrimerNumeroRoto());
    }

    @Test
    @DisplayName("hashAnterior cambiado en el evento 4 → rota en el 4")
    void hashAnteriorCambiadoSeDetecta() {
        List<EventoTrazabilidad> eventos = cadena(5);
        ReflectionTestUtils.setField(eventos.get(3), "hashAnterior", "0".repeat(64));

        VerificacionCadenaResponseDTO resultado = verificadorSobre(eventos).verificar();

        assertFalse(resultado.isIntegra());
        assertEquals(4L, resultado.getPrimerNumeroRoto());
    }

    @Test
    @DisplayName("Varios bloques (1203 eventos, bloques de 500) → íntegra")
    void variosBloques() {
        VerificacionCadenaResponseDTO resultado = verificadorSobre(cadena(1203)).verificar();

        assertTrue(resultado.isIntegra());
        assertEquals(1203, resultado.getEventosVerificados());
    }

    @Test
    @DisplayName("Alteración en el segundo bloque (evento 777) → rota en el 777")
    void alteracionEnSegundoBloque() {
        List<EventoTrazabilidad> eventos = cadena(1203);
        ReflectionTestUtils.setField(eventos.get(776), "entidadTipo", "Lote");

        VerificacionCadenaResponseDTO resultado = verificadorSobre(eventos).verificar();

        assertFalse(resultado.isIntegra());
        assertEquals(777L, resultado.getPrimerNumeroRoto());
    }

    @Test
    @DisplayName("Borrado del último evento: el estado de la cadena no coincide → rota")
    void ultimoBorradoSeDetectaPorEstado() {
        List<EventoTrazabilidad> eventos = cadena(5);
        VerificadorCadena verificador = verificadorSobre(eventos);
        eventos.remove(4);

        VerificacionCadenaResponseDTO resultado = verificador.verificar();

        assertFalse(resultado.isIntegra());
        assertEquals(5L, resultado.getPrimerNumeroRoto());
    }
}
