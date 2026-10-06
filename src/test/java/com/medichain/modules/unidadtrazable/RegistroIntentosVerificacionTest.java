package com.medichain.modules.unidadtrazable;

import com.medichain.modules.trazabilidad.RegistradorEventos;
import com.medichain.modules.trazabilidad.TipoEvento;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test unitario RegistroIntentosVerificacionTest en MediChain.
 * Anti-spam de la verificación pública: un evento por (tipo, GTIN, serie)
 * por día y tope diario global de SERIE_INEXISTENTE.
 */
@ExtendWith(MockitoExtension.class)
class RegistroIntentosVerificacionTest {

    @Mock
    private IntentoVerificacionRepository repository;

    @Mock
    private RegistradorEventos registradorEventos;

    /** Registra un intento de SERIE_INEXISTENTE. */
    private boolean registrar() {
        return new RegistroIntentosVerificacion(repository, registradorEventos).registrarSiCorresponde(
                TipoEvento.SERIE_INEXISTENTE, "07799000001010", "FALSA1", "Medicamento", UUID.randomUUID(), Map.of());
    }

    @Test
    @DisplayName("Primera vez en el día → guarda el intento y registra el evento (actor sistema)")
    void primeraVez() {
        assertTrue(registrar());
        verify(repository).saveAndFlush(any(IntentoVerificacion.class));
        verify(registradorEventos).registrar(eq(TipoEvento.SERIE_INEXISTENTE), eq("Medicamento"), any(), anyMap(),
                eq((UUID) null), eq((UUID) null));
    }

    @Test
    @DisplayName("Misma serie el mismo día → sin evento nuevo")
    void yaRegistradaHoy() {
        when(repository.existsByTipoAndGtinAndSerieAndFecha(any(), any(), any(), any())).thenReturn(true);

        assertFalse(registrar());
        verify(registradorEventos, never()).registrar(any(), any(), any(), anyMap(), any(UUID.class), any(UUID.class));
    }

    @Test
    @DisplayName("Tope diario global alcanzado → sin evento")
    void topeDiario() {
        when(repository.countByTipoAndFecha(eq(TipoEvento.SERIE_INEXISTENTE), any()))
                .thenReturn((long) RegistroIntentosVerificacion.TOPE_DIARIO_INEXISTENTES);

        assertFalse(registrar());
        verify(repository, never()).saveAndFlush(any());
    }
}
