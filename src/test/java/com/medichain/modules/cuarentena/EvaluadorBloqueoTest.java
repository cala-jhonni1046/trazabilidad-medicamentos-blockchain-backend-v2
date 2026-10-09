package com.medichain.modules.cuarentena;

import com.medichain.modules.bulto.Bulto;
import com.medichain.modules.empresa.Empresa;
import com.medichain.modules.empresa.TipoEmpresa;
import com.medichain.modules.enlacecuit.EnlaceCuit;
import com.medichain.modules.lote.Lote;
import com.medichain.modules.unidadtrazable.UnidadTrazable;
import com.medichain.testutil.DatosDePrueba;
import com.medichain.utils.Calendario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test unitario EvaluadorBloqueoTest en MediChain (R10 calculado).
 * Cada causa por separado: lote vencido, medida vigente sobre el lote y
 * medida vigente sobre el bulto; y la evaluación en bloque.
 */
@ExtendWith(MockitoExtension.class)
class EvaluadorBloqueoTest {

    @Mock
    private CuarentenaRepository cuarentenaRepository;

    private EvaluadorBloqueo evaluador;
    private Lote lote;
    private EnlaceCuit circuito;

    @BeforeEach
    void setUp() {
        evaluador = new EvaluadorBloqueo(cuarentenaRepository, new Calendario(Clock.systemUTC(), ZoneId.of("America/Argentina/Buenos_Aires")));
        Empresa laboratorio = DatosDePrueba.empresaHabilitada(TipoEmpresa.LABORATORIO);
        lote = DatosDePrueba.loteDe(laboratorio);
        circuito = DatosDePrueba.circuitoAprobado(laboratorio,
                DatosDePrueba.empresaHabilitada(TipoEmpresa.DISTRIBUIDOR), DatosDePrueba.empresaHabilitada(TipoEmpresa.FARMACIA));
        lenient().when(cuarentenaRepository.findLotesConMedidaVigente(anyCollection())).thenReturn(List.of());
        lenient().when(cuarentenaRepository.findBultosConMedidaVigente(anyCollection())).thenReturn(List.of());
    }

    @Test
    @DisplayName("Lote sano y bulto sin medidas → no bloqueado")
    void sinBloqueo() {
        assertTrue(evaluador.bloqueoDeLote(lote).isEmpty());
        assertTrue(evaluador.bloqueosDeBultos(List.of(DatosDePrueba.bulto("BUL-1", lote, circuito))).isEmpty());
    }

    @Test
    @DisplayName("Lote vencido → LOTE_VENCIDO en el lote y en sus bultos")
    void loteVencido() {
        Lote vencido = new Lote("L-OLD", LocalDate.of(2020, 1, 1), LocalDate.of(2021, 1, 1), 1, lote.getMedicamento());
        vencido.setId(UUID.randomUUID());
        Bulto bulto = DatosDePrueba.bulto("BUL-1", vencido, circuito);

        Bloqueo delLote = evaluador.bloqueoDeLote(vencido).orElseThrow();
        assertEquals(CausaBloqueo.LOTE_VENCIDO, delLote.getCausa());
        assertTrue(delLote.getMensaje().contains("vencido"));
        assertEquals(CausaBloqueo.LOTE_VENCIDO, evaluador.bloqueosDeBultos(List.of(bulto)).get(bulto.getId()).getCausa());
    }

    @Test
    @DisplayName("Lote en CUARENTENA → LOTE_EN_CUARENTENA; en RECALL → LOTE_EN_RECALL")
    void loteEnCuarentenaORecall() {
        lote.liberar(null);
        lote.entrarEnCuarentena();
        assertEquals(CausaBloqueo.LOTE_EN_CUARENTENA, evaluador.bloqueoDeLote(lote).orElseThrow().getCausa());
        lote.pasarARecall();
        assertEquals(CausaBloqueo.LOTE_EN_RECALL, evaluador.bloqueoDeLote(lote).orElseThrow().getCausa());
    }

    @Test
    @DisplayName("Medida vigente de alcance LOTE (con el lote LIBERADO) → LOTE_CON_MEDIDA_VIGENTE en el lote y sus bultos")
    void medidaSobreElLote() {
        when(cuarentenaRepository.findLotesConMedidaVigente(anyCollection())).thenReturn(List.of(lote.getId()));
        Bulto bulto = DatosDePrueba.bulto("BUL-1", lote, circuito);

        assertEquals(CausaBloqueo.LOTE_CON_MEDIDA_VIGENTE, evaluador.bloqueoDeLote(lote).orElseThrow().getCausa());
        assertEquals(CausaBloqueo.LOTE_CON_MEDIDA_VIGENTE,
                evaluador.bloqueosDeBultos(List.of(bulto)).get(bulto.getId()).getCausa());
    }

    @Test
    @DisplayName("Medida vigente sobre UN bulto (DESPACHO o BULTO) → solo ese bulto, BULTO_CON_MEDIDA_VIGENTE")
    void medidaSobreUnBulto() {
        Bulto afectado = DatosDePrueba.bulto("BUL-1", lote, circuito);
        Bulto sano = DatosDePrueba.bulto("BUL-2", lote, circuito);
        when(cuarentenaRepository.findBultosConMedidaVigente(anyCollection())).thenReturn(List.of(afectado.getId()));

        Map<UUID, Bloqueo> bloqueados = evaluador.bloqueosDeBultos(List.of(afectado, sano));

        assertEquals(1, bloqueados.size());
        assertEquals(CausaBloqueo.BULTO_CON_MEDIDA_VIGENTE, bloqueados.get(afectado.getId()).getCausa());
    }

    @Test
    @DisplayName("Cajas en bloque: la caja en un bulto hereda el del bulto; la caja sin bulto, el de su lote; dos consultas para todas")
    void cajasEnBloque() {
        Bulto afectado = DatosDePrueba.bulto("BUL-1", lote, circuito);
        UnidadTrazable enBultoAfectado = caja("S1", lote);
        enBultoAfectado.asignarABulto(afectado);
        UnidadTrazable sinBulto = caja("S2", lote);
        when(cuarentenaRepository.findBultosConMedidaVigente(anyCollection())).thenReturn(List.of(afectado.getId()));

        Map<UUID, Bloqueo> bloqueadas = evaluador.bloqueosDeCajas(List.of(enBultoAfectado, sinBulto));

        assertEquals(1, bloqueadas.size());
        assertEquals(CausaBloqueo.BULTO_CON_MEDIDA_VIGENTE, bloqueadas.get(enBultoAfectado.getId()).getCausa());
        assertTrue(evaluador.bloqueoDeCaja(sinBulto).isEmpty());
        verify(cuarentenaRepository, times(1)).findBultosConMedidaVigente(anyCollection());
    }

    /** Caja del lote dado, con id. */
    private static UnidadTrazable caja(String serie, Lote delLote) {
        UnidadTrazable caja = new UnidadTrazable(serie, delLote);
        caja.setId(UUID.randomUUID());
        return caja;
    }
}
