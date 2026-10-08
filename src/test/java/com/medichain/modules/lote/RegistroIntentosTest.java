package com.medichain.modules.lote;

import com.medichain.modules.auth.UsuarioAutenticado;
import com.medichain.modules.empresa.TipoEmpresa;
import com.medichain.modules.medicamento.Medicamento;
import com.medichain.modules.trazabilidad.RegistradorEventos;
import com.medichain.modules.trazabilidad.TipoEvento;
import com.medichain.modules.unidadtrazable.UnidadTrazableRepository;
import com.medichain.modules.usuario.RolUsuario;
import com.medichain.testutil.DatosDePrueba;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test unitario RegistroIntentosTest en MediChain.
 * Carrera de series (R3): registrarChoqueDeSeries consulta las series que ya
 * existen en tandas de 1000, registra INTENTO_SERIE_INVALIDA con la cantidad
 * de existentes y una muestra de hasta 10, y devuelve las existentes.
 */
@ExtendWith(MockitoExtension.class)
class RegistroIntentosTest {

    @Mock
    private RegistradorEventos registradorEventos;

    @Mock
    private UnidadTrazableRepository unidadTrazableRepository;

    @Test
    @DisplayName("Choque de series: 2500 series → 3 consultas por GTIN; evento con yaExistentes y muestra de 10")
    @SuppressWarnings("unchecked")
    void choqueDeSeriesEnTandas() {
        Medicamento medicamento = DatosDePrueba.loteDe(DatosDePrueba.empresaHabilitada(TipoEmpresa.LABORATORIO))
                .getMedicamento();
        UsuarioAutenticado actor = DatosDePrueba.autenticado(RolUsuario.LABORATORIO, medicamento.getLaboratorio());
        List<String> series = new ArrayList<>();
        for (int i = 1; i <= 2500; i++) {
            series.add("S" + i);
        }
        List<String> chocan = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            chocan.add("S" + i);
        }
        when(unidadTrazableRepository.findSeriesExistentes(eq(medicamento.getGtin()), anyList()))
                .thenReturn(chocan, List.of(), List.of());

        List<String> existentes = new RegistroIntentos(registradorEventos, unidadTrazableRepository)
                .registrarChoqueDeSeries("L2026-0001", medicamento, series, "hash-series", actor);

        assertEquals(chocan, existentes);
        verify(unidadTrazableRepository, times(3)).findSeriesExistentes(eq(medicamento.getGtin()), anyList());
        ArgumentCaptor<Map<String, Object>> datos = ArgumentCaptor.forClass(Map.class);
        verify(registradorEventos).registrar(eq(TipoEvento.INTENTO_SERIE_INVALIDA), eq("Medicamento"),
                eq(medicamento.getId()), datos.capture(), eq(actor));
        assertEquals(2500, datos.getValue().get("cantidadRecibida"));
        assertEquals(12, datos.getValue().get("yaExistentes"));
        assertEquals(10, ((List<String>) datos.getValue().get("muestra")).size());
        assertEquals("hash-series", datos.getValue().get("seriesHash"));
    }
}
