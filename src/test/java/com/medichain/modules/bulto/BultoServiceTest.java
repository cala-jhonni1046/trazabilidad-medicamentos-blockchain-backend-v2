package com.medichain.modules.bulto;

import com.medichain.exceptions.ReglaNegocioException;
import com.medichain.exceptions.ResourceNotFoundException;
import com.medichain.modules.auth.UsuarioAutenticado;
import com.medichain.modules.cuarentena.EvaluadorBloqueo;
import com.medichain.modules.despachologistico.TramoDespacho;
import com.medichain.modules.empresa.Empresa;
import com.medichain.modules.empresa.TipoEmpresa;
import com.medichain.modules.enlacecuit.EnlaceCuit;
import com.medichain.modules.enlacecuit.EnlaceCuitRepository;
import com.medichain.modules.lote.Lote;
import com.medichain.modules.lote.LoteRepository;
import com.medichain.modules.trazabilidad.HashUtil;
import com.medichain.modules.trazabilidad.RegistradorEventos;
import com.medichain.modules.trazabilidad.TipoEvento;
import com.medichain.modules.unidadtrazable.EstadoUnidad;
import com.medichain.modules.unidadtrazable.UnidadTrazable;
import com.medichain.modules.unidadtrazable.UnidadTrazableRepository;
import com.medichain.modules.usuario.RolUsuario;
import com.medichain.testutil.DatosDePrueba;
import com.medichain.utils.seguridad.UsuarioActual;
import com.medichain.utils.seguridad.VerificadorEmpresa;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test unitario BultoServiceTest en MediChain.
 * Armado (R6, R10), desarmado y visibilidad de bultos con Mockito.
 */
@ExtendWith(MockitoExtension.class)
class BultoServiceTest {

    @Mock
    private BultoRepository repository;

    @Mock
    private LoteRepository loteRepository;

    @Mock
    private EnlaceCuitRepository enlaceCuitRepository;

    @Mock
    private UnidadTrazableRepository unidadTrazableRepository;

    @Mock
    private EvaluadorBloqueo evaluadorBloqueo;

    @Mock
    private UsuarioActual usuarioActual;

    @Mock
    private VerificadorEmpresa verificadorEmpresa;

    @Mock
    private RegistradorEventos registradorEventos;

    private Empresa laboratorio;
    private Empresa distribuidora;
    private Lote lote;
    private EnlaceCuit circuito;
    private UsuarioAutenticado actual;

    @BeforeEach
    void setUp() {
        laboratorio = DatosDePrueba.empresaHabilitada(TipoEmpresa.LABORATORIO);
        distribuidora = DatosDePrueba.empresaHabilitada(TipoEmpresa.DISTRIBUIDOR);
        lote = DatosDePrueba.loteDe(laboratorio);
        lote.liberar(null);
        circuito = DatosDePrueba.circuitoAprobado(laboratorio, distribuidora,
                DatosDePrueba.empresaHabilitada(TipoEmpresa.FARMACIA));
        actual = DatosDePrueba.autenticado(RolUsuario.LABORATORIO, laboratorio);
    }

    /** Construye el Service bajo prueba con los mocks. */
    private BultoService service() {
        return new BultoService(repository, loteRepository, enlaceCuitRepository, unidadTrazableRepository,
                evaluadorBloqueo, usuarioActual, verificadorEmpresa, registradorEventos);
    }

    /** Prepara el laboratorio autenticado, el lote, el circuito, el código y el guardado. */
    private void prepararArmado() {
        when(usuarioActual.obtener()).thenReturn(actual);
        lenient().when(loteRepository.findById(lote.getId())).thenReturn(Optional.of(lote));
        lenient().when(enlaceCuitRepository.findById(circuito.getId())).thenReturn(Optional.of(circuito));
        lenient().when(evaluadorBloqueo.bloqueoDeLote(lote)).thenReturn(Optional.empty());
        lenient().when(repository.siguienteNumeroCodigo()).thenReturn(1L);
        lenient().when(repository.save(any(Bulto.class))).thenAnswer(inv -> {
            Bulto b = inv.getArgument(0);
            if (b.getId() == null) {
                b.setId(java.util.UUID.randomUUID());
            }
            return b;
        });
    }

    /** DTO de armado con series. */
    private BultoRequestDTO conSeries(String... series) {
        BultoRequestDTO dto = new BultoRequestDTO();
        dto.setCircuitoId(circuito.getId());
        dto.setLoteId(lote.getId());
        dto.setPrecinto("PRE-1");
        dto.setSeries(List.of(series));
        return dto;
    }

    /** Cajas disponibles del lote con las series dadas. */
    private List<UnidadTrazable> cajas(String... series) {
        List<UnidadTrazable> cajas = new ArrayList<>();
        for (String serie : series) {
            cajas.add(DatosDePrueba.caja(lote, serie));
        }
        return cajas;
    }

    /** Verifica que la acción falle con el código dado. */
    private void fallaCon(String codigo, Runnable accion) {
        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class, accion::run);
        assertEquals(codigo, ex.getCodigoRegla());
    }

    // ---------- Armado ----------

    @Test
    @DisplayName("Armar con series: BUL-0001, cajas asignadas, cantidad = cajas y evento con seriesHash")
    @SuppressWarnings("unchecked")
    void armarConSeries() {
        prepararArmado();
        List<UnidadTrazable> cajas = cajas("S1", "S2");
        when(unidadTrazableRepository.findByLoteIdAndSerieIn(eq(lote.getId()), anyCollection())).thenReturn(cajas);

        Bulto bulto = service().armar(conSeries("S1", "S2"));

        assertEquals("BUL-0001", bulto.getCodigo());
        assertEquals(2, bulto.getCantidad());
        assertEquals(EstadoBulto.ARMADO, bulto.getEstado());
        assertSame(laboratorio, bulto.getUbicacion());
        assertSame(bulto, cajas.get(0).getBulto());
        ArgumentCaptor<Map<String, Object>> datos = ArgumentCaptor.forClass(Map.class);
        verify(registradorEventos).registrar(eq(TipoEvento.BULTO_ARMADO), eq("Bulto"), eq(bulto.getId()),
                datos.capture(), eq(actual));
        assertEquals(HashUtil.seriesHash(List.of("S1", "S2")), datos.getValue().get("seriesHash"));
    }

    @Test
    @DisplayName("Armar con cantidad: toma las primeras cajas disponibles del lote")
    void armarConCantidad() {
        prepararArmado();
        when(unidadTrazableRepository.findByLoteIdAndEstadoAndBultoIsNullOrderBySerieAsc(eq(lote.getId()),
                eq(EstadoUnidad.EN_LABORATORIO), any(Limit.class))).thenReturn(cajas("S1", "S2", "S3"));
        BultoRequestDTO dto = conSeries();
        dto.setSeries(null);
        dto.setCantidad(3);

        assertEquals(3, service().armar(dto).getCantidad());
    }

    @Test
    @DisplayName("R6: cantidad mayor a las cajas disponibles del lote")
    void armarSinCajasSuficientes() {
        prepararArmado();
        when(unidadTrazableRepository.findByLoteIdAndEstadoAndBultoIsNullOrderBySerieAsc(eq(lote.getId()),
                eq(EstadoUnidad.EN_LABORATORIO), any(Limit.class))).thenReturn(cajas("S1"));
        BultoRequestDTO dto = conSeries();
        dto.setSeries(null);
        dto.setCantidad(5);

        fallaCon("R6", () -> service().armar(dto));
    }

    @Test
    @DisplayName("R6: lote no LIBERADO")
    void loteNoLiberado() {
        lote = DatosDePrueba.loteDe(laboratorio);
        prepararArmado();

        fallaCon("R6", () -> service().armar(conSeries("S1")));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("R10: lote bloqueado (vencido o con medida vigente)")
    void loteBloqueado() {
        prepararArmado();
        when(evaluadorBloqueo.bloqueoDeLote(lote)).thenReturn(Optional.of("el lote está vencido"));

        fallaCon("R10", () -> service().armar(conSeries("S1")));
    }

    @Test
    @DisplayName("R6: circuito no APROBADO")
    void circuitoNoAprobado() {
        circuito = DatosDePrueba.circuito(laboratorio, distribuidora, DatosDePrueba.empresaHabilitada(TipoEmpresa.FARMACIA));
        prepararArmado();

        fallaCon("R6", () -> service().armar(conSeries("S1")));
    }

    @Test
    @DisplayName("Lote o circuito de otro laboratorio → 404")
    void loteAjeno() {
        Lote ajeno = DatosDePrueba.loteDe(DatosDePrueba.empresaHabilitada(TipoEmpresa.LABORATORIO));
        ajeno.liberar(null);
        when(usuarioActual.obtener()).thenReturn(actual);
        when(loteRepository.findById(ajeno.getId())).thenReturn(Optional.of(ajeno));
        BultoRequestDTO dto = conSeries("S1");
        dto.setLoteId(ajeno.getId());

        assertThrows(ResourceNotFoundException.class, () -> service().armar(dto));
    }

    @Test
    @DisplayName("R6: una serie que no es caja de ESE lote")
    void cajaDeOtroLote() {
        prepararArmado();
        when(unidadTrazableRepository.findByLoteIdAndSerieIn(eq(lote.getId()), anyCollection())).thenReturn(cajas("S1"));

        fallaCon("R6", () -> service().armar(conSeries("S1", "DE-OTRO-LOTE")));
    }

    @Test
    @DisplayName("R6: una caja que ya está en otro bulto")
    void cajaYaEnBulto() {
        prepararArmado();
        List<UnidadTrazable> cajas = cajas("S1");
        cajas.get(0).asignarABulto(DatosDePrueba.bulto("BUL-0099", lote, circuito));
        when(unidadTrazableRepository.findByLoteIdAndSerieIn(eq(lote.getId()), anyCollection())).thenReturn(cajas);

        fallaCon("R6", () -> service().armar(conSeries("S1")));
    }

    @Test
    @DisplayName("R6: series repetidas en la lista")
    void seriesRepetidas() {
        prepararArmado();

        fallaCon("R6", () -> service().armar(conSeries("S1", "S1")));
    }

    // ---------- Desarmar ----------

    @Test
    @DisplayName("Desarmar: DESARMADO, cajas liberadas y evento BULTO_DESARMADO")
    void desarmar() {
        Bulto bulto = DatosDePrueba.bulto("BUL-0001", lote, circuito);
        List<UnidadTrazable> cajas = cajas("S1", "S2");
        cajas.forEach(c -> c.asignarABulto(bulto));
        when(usuarioActual.obtener()).thenReturn(actual);
        when(repository.findById(bulto.getId())).thenReturn(Optional.of(bulto));
        when(repository.save(any(Bulto.class))).thenAnswer(inv -> inv.getArgument(0));
        when(unidadTrazableRepository.findByBultoId(bulto.getId())).thenReturn(cajas);

        service().desarmar(bulto.getId());

        assertEquals(EstadoBulto.DESARMADO, bulto.getEstado());
        assertNull(cajas.get(0).getBulto());
        verify(registradorEventos).registrar(eq(TipoEvento.BULTO_DESARMADO), eq("Bulto"), eq(bulto.getId()),
                any(), eq(actual));
    }

    @Test
    @DisplayName("Desarmar un bulto asignado a un viaje → TRANSICION_INVALIDA")
    void desarmarConViaje() {
        Bulto bulto = DatosDePrueba.bulto("BUL-0001", lote, circuito);
        DatosDePrueba.viaje(TramoDespacho.LAB_A_DISTRIBUIDOR, laboratorio).agregarBulto(bulto);
        when(usuarioActual.obtener()).thenReturn(actual);
        when(repository.findById(bulto.getId())).thenReturn(Optional.of(bulto));

        fallaCon("TRANSICION_INVALIDA", () -> service().desarmar(bulto.getId()));
    }

    // ---------- Visibilidad ----------

    @Test
    @DisplayName("La distribuidora del circuito ve el bulto desde ARMADO; otra distribuidora → 404")
    void visibilidadDistribuidora() {
        Bulto bulto = DatosDePrueba.bulto("BUL-0001", lote, circuito);
        when(repository.findById(bulto.getId())).thenReturn(Optional.of(bulto));

        when(usuarioActual.obtener()).thenReturn(DatosDePrueba.autenticado(RolUsuario.DISTRIBUIDOR, distribuidora));
        assertSame(bulto, service().getById(bulto.getId()));

        when(usuarioActual.obtener()).thenReturn(DatosDePrueba.autenticado(RolUsuario.DISTRIBUIDOR,
                DatosDePrueba.empresaHabilitada(TipoEmpresa.DISTRIBUIDOR)));
        assertThrows(ResourceNotFoundException.class, () -> service().getById(bulto.getId()));
    }
}
