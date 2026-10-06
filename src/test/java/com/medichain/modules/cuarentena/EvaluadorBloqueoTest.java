package com.medichain.modules.cuarentena;

import com.medichain.modules.bulto.Bulto;
import com.medichain.modules.empresa.Empresa;
import com.medichain.modules.empresa.TipoEmpresa;
import com.medichain.modules.enlacecuit.EnlaceCuit;
import com.medichain.modules.lote.Lote;
import com.medichain.testutil.DatosDePrueba;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
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
        evaluador = new EvaluadorBloqueo(cuarentenaRepository);
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
        assertTrue(evaluador.bultosBloqueados(List.of(DatosDePrueba.bulto("BUL-1", lote, circuito))).isEmpty());
    }

    @Test
    @DisplayName("Lote vencido → bloqueado (lote y sus bultos)")
    void loteVencido() {
        Lote vencido = new Lote("L-OLD", LocalDate.of(2020, 1, 1), LocalDate.of(2021, 1, 1), 1, lote.getMedicamento());
        vencido.setId(java.util.UUID.randomUUID());

        assertTrue(evaluador.bloqueoDeLote(vencido).orElseThrow().contains("vencido"));
        assertEquals(1, evaluador.bultosBloqueados(List.of(DatosDePrueba.bulto("BUL-1", vencido, circuito))).size());
    }

    @Test
    @DisplayName("Medida vigente de alcance LOTE → lote y bultos bloqueados")
    void medidaSobreElLote() {
        when(cuarentenaRepository.findLotesConMedidaVigente(anyCollection())).thenReturn(List.of(lote.getId()));

        assertTrue(evaluador.bloqueoDeLote(lote).isPresent());
        assertEquals(1, evaluador.bultosBloqueados(List.of(DatosDePrueba.bulto("BUL-1", lote, circuito))).size());
    }

    @Test
    @DisplayName("Medida vigente sobre UN bulto (DESPACHO o BULTO) → solo ese bulto bloqueado")
    void medidaSobreUnBulto() {
        Bulto afectado = DatosDePrueba.bulto("BUL-1", lote, circuito);
        Bulto sano = DatosDePrueba.bulto("BUL-2", lote, circuito);
        when(cuarentenaRepository.findBultosConMedidaVigente(anyCollection())).thenReturn(List.of(afectado.getId()));

        Map<String, String> bloqueados = evaluador.bultosBloqueados(List.of(afectado, sano));

        assertEquals(1, bloqueados.size());
        assertTrue(bloqueados.containsKey("BUL-1"));
    }
}
