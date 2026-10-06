package com.medichain.modules.trazabilidad;

import com.medichain.modules.auth.UsuarioAutenticado;
import com.medichain.modules.usuario.RolUsuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Test unitario RegistradorEventosTest en MediChain.
 * Encadenado con Mockito: GENESIS en el primero, enlace por hash en el
 * segundo, avance de CadenaEstado y omisión del actor PACIENTE.
 */
@ExtendWith(MockitoExtension.class)
class RegistradorEventosTest {

    @Mock
    private CadenaEstadoRepository cadenaEstadoRepository;

    @Mock
    private EventoTrazabilidadRepository eventoRepository;

    private CadenaEstado estado;
    private RegistradorEventos registrador;

    @BeforeEach
    void setUp() {
        estado = new CadenaEstado(CadenaEstado.PRINCIPAL, 0L, EventoTrazabilidad.GENESIS);
        when(cadenaEstadoRepository.findByNombre(CadenaEstado.PRINCIPAL)).thenReturn(Optional.of(estado));
        when(eventoRepository.save(any(EventoTrazabilidad.class))).thenAnswer(inv -> inv.getArgument(0));
        Clock reloj = Clock.fixed(Instant.parse("2026-01-15T12:00:00.123456789Z"), ZoneOffset.UTC);
        registrador = new RegistradorEventos(cadenaEstadoRepository, eventoRepository, reloj);
    }

    @Test
    @DisplayName("Primer evento: numero 1, hashAnterior GENESIS, fecha en micros; el estado avanza")
    void primerEventoUsaGenesis() {
        EventoTrazabilidad evento = registrador.registrar(TipoEvento.MEDICAMENTO_REGISTRADO, "Medicamento",
                UUID.randomUUID(), Map.of("gtin", "07799000001010"), null, null);

        assertEquals(1L, evento.getNumero());
        assertEquals(EventoTrazabilidad.GENESIS, evento.getHashAnterior());
        assertEquals(Instant.parse("2026-01-15T12:00:00.123456Z"), evento.getFechaHora());
        assertEquals(64, evento.getHash().length());
        assertEquals(1L, estado.getUltimoNumero());
        assertEquals(evento.getHash(), estado.getUltimoHash());
    }

    @Test
    @DisplayName("Segundo evento: numero 2 y hashAnterior = hash del primero")
    void segundoEventoEncadenaConElPrimero() {
        EventoTrazabilidad primero = registrador.registrar(TipoEvento.MEDICAMENTO_REGISTRADO, "Medicamento",
                UUID.randomUUID(), Map.of("gtin", "1"), null, null);
        EventoTrazabilidad segundo = registrador.registrar(TipoEvento.LOTE_REGISTRADO, "Lote",
                UUID.randomUUID(), Map.of("codigo", "L2026-0001"), null, null);

        assertEquals(2L, segundo.getNumero());
        assertEquals(primero.getHash(), segundo.getHashAnterior());
        assertEquals(segundo.getHash(), estado.getUltimoHash());
    }

    @Test
    @DisplayName("Actor PACIENTE: se omiten usuario y empresa del actor (R13)")
    void actorPacienteSeOmite() {
        UsuarioAutenticado paciente = new UsuarioAutenticado(UUID.randomUUID(), "p@demo.com",
                RolUsuario.PACIENTE, null, null);

        EventoTrazabilidad evento = registrador.registrar(TipoEvento.REPORTE_CIUDADANO, "ReporteCiudadano",
                UUID.randomUUID(), Map.of("codigo", "REP-0001"), paciente);

        assertNull(evento.getActorUsuarioId());
        assertNull(evento.getActorEmpresaId());
    }

    @Test
    @DisplayName("Actor de empresa: guarda usuario y empresa")
    void actorDeEmpresaSeGuarda() {
        UUID empresaId = UUID.randomUUID();
        UsuarioAutenticado farmacia = new UsuarioAutenticado(UUID.randomUUID(), "f@demo.com",
                RolUsuario.FARMACIA, empresaId, null);

        EventoTrazabilidad evento = registrador.registrar(TipoEvento.DISPENSACION, "Dispensacion",
                UUID.randomUUID(), Map.of("serie", "0001S000001"), farmacia);

        assertEquals(farmacia.getUsuarioId(), evento.getActorUsuarioId());
        assertEquals(empresaId, evento.getActorEmpresaId());
    }
}
