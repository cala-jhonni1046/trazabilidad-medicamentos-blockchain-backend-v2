package com.medichain.modules.registroblockchain;

import com.medichain.modules.trazabilidad.CadenaEstado;
import com.medichain.modules.trazabilidad.CadenaEstadoRepository;
import com.medichain.modules.trazabilidad.EventoTrazabilidad;
import com.medichain.modules.trazabilidad.EventoTrazabilidadRepository;
import com.medichain.modules.trazabilidad.JsonCanonico;
import com.medichain.modules.trazabilidad.TipoEvento;
import com.medichain.modules.trazabilidad.VerificacionCadenaResponseDTO;
import com.medichain.modules.trazabilidad.VerificadorCadena;
import com.medichain.testutil.DatosDePrueba;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Test unitario VerificadorAnclajesTest en MediChain (R15).
 * El escenario central es el ATAQUE CON RECÁLCULO: alguien altera un
 * evento y recalcula todos los hashes siguientes y cadena_estado. La
 * verificación local (VerificadorCadena real) dice "íntegra"; la
 * comparación con los anclajes del contrato dice ALTERADA y acota el rango.
 * También: eventos borrados (truncamiento), tabla de anclajes adulterada,
 * red caída (NO_CONSULTADA) y anclaje deshabilitado (NO_DISPONIBLE).
 */
@ExtendWith(MockitoExtension.class)
class VerificadorAnclajesTest {

    private static final String CONTRATO = "0x" + "1".repeat(40);

    @Mock
    private ClienteBlockchain cliente;

    @Mock
    private EventoTrazabilidadRepository eventoRepository;

    @Mock
    private RegistroBlockchainRepository registroRepository;

    @Mock
    private CadenaEstadoRepository cadenaEstadoRepository;

    private AnclajeProperties propiedades;

    @BeforeEach
    void setUp() {
        propiedades = new AnclajeProperties();
        propiedades.setHabilitado(true);
        propiedades.setContrato(CONTRATO);
        lenient().when(registroRepository.findByEstadoOrderByHastaNumeroAsc(EstadoAnclaje.CONFIRMADO)).thenReturn(List.of());
    }

    /**
     * Cadena válida de n eventos con contenido determinístico. Si alterado > 0,
     * el evento con ese número lleva otro dato y desde él se recalculan todos
     * los hashes (exactamente lo que haría un atacante con acceso a la base).
     */
    private List<EventoTrazabilidad> cadena(int n, int alterado) {
        List<EventoTrazabilidad> eventos = new ArrayList<>();
        String anterior = EventoTrazabilidad.GENESIS;
        for (long i = 1; i <= n; i++) {
            String codigo = i == alterado ? "BUL-FALSO" : "BUL-" + i;
            EventoTrazabilidad evento = new EventoTrazabilidad(i, TipoEvento.BULTO_ARMADO,
                    Instant.parse("2026-10-05T12:00:00Z").plusSeconds(i), "Bulto",
                    UUID.nameUUIDFromBytes(("bulto-" + i).getBytes()), JsonCanonico.escribir(Map.of("codigo", codigo)),
                    null, null, anterior);
            eventos.add(evento);
            anterior = evento.getHash();
        }
        return eventos;
    }

    /** La base (eventos y cadena_estado) contiene exactamente esta cadena. */
    private void baseCon(List<EventoTrazabilidad> eventos) {
        lenient().when(eventoRepository.findByNumeroIn(any())).thenAnswer(inv -> {
            Collection<Long> numeros = inv.getArgument(0);
            return eventos.stream().filter(e -> numeros.contains(e.getNumero())).toList();
        });
        lenient().when(eventoRepository.findByNumeroGreaterThanOrderByNumeroAsc(anyLong(), any(Limit.class)))
                .thenAnswer(inv -> {
                    long desde = inv.getArgument(0);
                    int max = ((Limit) inv.getArgument(1)).max();
                    return eventos.stream().filter(e -> e.getNumero() > desde).limit(max).toList();
                });
        EventoTrazabilidad ultimo = eventos.get(eventos.size() - 1);
        lenient().when(cadenaEstadoRepository.findFirstByNombre(CadenaEstado.PRINCIPAL)).thenReturn(Optional.of(
                new CadenaEstado(CadenaEstado.PRINCIPAL, ultimo.getNumero(), ultimo.getHash())));
    }

    /** El contrato tiene anclados esos números con los hashes de la cadena ORIGINAL. */
    private void contratoCon(List<EventoTrazabilidad> original, long... numeros) {
        List<AnclajeEnContrato> anclajes = new ArrayList<>();
        for (long numero : numeros) {
            anclajes.add(new AnclajeEnContrato(numero, original.get((int) numero - 1).getHash()));
        }
        lenient().when(cliente.cantidadAnclajes()).thenReturn((long) anclajes.size());
        lenient().when(cliente.anclajes(anyLong(), anyInt())).thenAnswer(inv -> {
            long desde = inv.getArgument(0);
            int cantidad = inv.getArgument(1);
            return anclajes.subList((int) Math.min(desde, anclajes.size()),
                    (int) Math.min(desde + cantidad, anclajes.size()));
        });
    }

    /** Verificador bajo prueba. */
    private VerificadorAnclajes verificador() {
        return new VerificadorAnclajes(cliente, eventoRepository, registroRepository,
                new PasosAnclaje(registroRepository, cadenaEstadoRepository, eventoRepository, propiedades,
                        Clock.systemUTC()), propiedades);
    }

    @Test
    @DisplayName("Cadena intacta → VERIFICADA: anclajes que coinciden, último anclado y eventos sin anclar")
    void verificada() {
        List<EventoTrazabilidad> original = cadena(10, 0);
        baseCon(original);
        contratoCon(original, 4, 8);

        VerificacionBlockchainDTO resultado = verificador().verificar();

        assertEquals(EstadoVerificacionBlockchain.VERIFICADA, resultado.getEstado());
        assertEquals(2, resultado.getAnclajesVerificados());
        assertEquals(8L, resultado.getUltimoNumeroAnclado());
        assertEquals(2L, resultado.getEventosSinAnclar());
    }

    @Test
    @DisplayName("ATAQUE: evento #6 alterado y hashes recalculados → local ÍNTEGRA, blockchain ALTERADA entre #5 y #8")
    void ataqueConRecalculo() {
        List<EventoTrazabilidad> original = cadena(10, 0);
        List<EventoTrazabilidad> atacada = cadena(10, 6);
        baseCon(atacada);
        contratoCon(original, 4, 8);

        VerificacionCadenaResponseDTO local = new VerificadorCadena(eventoRepository, cadenaEstadoRepository).verificar();
        VerificacionBlockchainDTO blockchain = verificador().verificar();

        assertTrue(local.isIntegra(), "la cadena recalculada es coherente consigo misma");
        assertEquals(EstadoVerificacionBlockchain.ALTERADA, blockchain.getEstado());
        assertEquals(5L, blockchain.getAlteradoDesde());
        assertEquals(8L, blockchain.getAlteradoHasta());
        assertEquals(1, blockchain.getAnclajesVerificados(), "el anclaje #4 todavía coincide");
        assertTrue(VerificadorAnclajes.resumen(local, blockchain).contains("NO coincide con lo anclado"));
    }

    @Test
    @DisplayName("Eventos borrados (la base llega al #8, el contrato ancló el #10) → ALTERADA: la base no lo tiene")
    void eventosBorrados() {
        List<EventoTrazabilidad> original = cadena(10, 0);
        baseCon(original.subList(0, 8));
        contratoCon(original, 4, 10);

        VerificacionBlockchainDTO resultado = verificador().verificar();

        assertEquals(EstadoVerificacionBlockchain.ALTERADA, resultado.getEstado());
        assertEquals(10L, resultado.getAlteradoHasta());
        assertTrue(resultado.getMotivo().contains("la base no lo tiene"), resultado.getMotivo());
    }

    @Test
    @DisplayName("Fila de registros_blockchain adulterada (otro hash para un anclaje CONFIRMADO) → ALTERADA")
    void tablaDeAnclajesAdulterada() {
        List<EventoTrazabilidad> original = cadena(10, 0);
        baseCon(original);
        contratoCon(original, 4, 8);
        when(registroRepository.findByEstadoOrderByHastaNumeroAsc(EstadoAnclaje.CONFIRMADO))
                .thenReturn(List.of(DatosDePrueba.anclajeConfirmado(5, 8, "ff".repeat(32), CONTRATO)));

        VerificacionBlockchainDTO resultado = verificador().verificar();

        assertEquals(EstadoVerificacionBlockchain.ALTERADA, resultado.getEstado());
        assertTrue(resultado.getMotivo().contains("se adulteró la tabla de anclajes"), resultado.getMotivo());
    }

    @Test
    @DisplayName("La red no responde → NO_CONSULTADA (no es lo mismo que alterada)")
    void redCaida() {
        baseCon(cadena(3, 0));
        when(cliente.cantidadAnclajes()).thenThrow(new ErrorBlockchainException("No se pudo leer: timeout"));

        VerificacionBlockchainDTO resultado = verificador().verificar();

        assertEquals(EstadoVerificacionBlockchain.NO_CONSULTADA, resultado.getEstado());
        assertTrue(resultado.getMotivo().contains("NO indica una alteración"));
    }

    @Test
    @DisplayName("Anclaje deshabilitado → NO_DISPONIBLE, sin consultar la red")
    void deshabilitado() {
        propiedades.setHabilitado(false);

        VerificacionBlockchainDTO resultado = verificador().verificar();

        assertEquals(EstadoVerificacionBlockchain.NO_DISPONIBLE, resultado.getEstado());
        verifyNoInteractions(cliente);
    }
}
