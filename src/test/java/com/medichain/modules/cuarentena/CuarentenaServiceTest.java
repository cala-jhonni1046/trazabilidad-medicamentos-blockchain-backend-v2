package com.medichain.modules.cuarentena;

import com.medichain.exceptions.ReglaNegocioException;
import com.medichain.exceptions.ResourceNotFoundException;
import com.medichain.modules.auth.UsuarioAutenticado;
import com.medichain.modules.bulto.Bulto;
import com.medichain.modules.bulto.BultoRepository;
import com.medichain.modules.bulto.EstadoBulto;
import com.medichain.modules.despachologistico.DespachoLogistico;
import com.medichain.modules.despachologistico.TramoDespacho;
import com.medichain.modules.empresa.Empresa;
import com.medichain.modules.empresa.TipoEmpresa;
import com.medichain.modules.enlacecuit.EnlaceCuit;
import com.medichain.modules.inspectoranmat.InspectorAnmat;
import com.medichain.modules.inspectoranmat.InspectorAnmatRepository;
import com.medichain.modules.lote.EstadoLote;
import com.medichain.modules.lote.Lote;
import com.medichain.modules.lote.LoteRepository;
import com.medichain.modules.recepcion.MotivoRechazoRecepcion;
import com.medichain.modules.recepcion.Recepcion;
import com.medichain.modules.recepcion.RecepcionRepository;
import com.medichain.modules.reporteciudadano.MotivoReporte;
import com.medichain.modules.reporteciudadano.ReporteCiudadano;
import com.medichain.modules.reporteciudadano.ReporteCiudadanoRepository;
import com.medichain.modules.trazabilidad.HashUtil;
import com.medichain.modules.trazabilidad.RegistradorEventos;
import com.medichain.modules.trazabilidad.TipoEvento;
import com.medichain.modules.unidadtrazable.EstadoUnidad;
import com.medichain.modules.unidadtrazable.UnidadTrazable;
import com.medichain.modules.unidadtrazable.UnidadTrazableRepository;
import com.medichain.modules.usuario.RolUsuario;
import com.medichain.testutil.DatosDePrueba;
import com.medichain.utils.enums.Provincia;
import com.medichain.utils.seguridad.UsuarioActual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test unitario CuarentenaServiceTest en MediChain (R8, R9, R12, R14).
 * Apertura manual de LOTE, bandeja, tomar (CUARENTENA_TOMADA), levantar
 * (LOTE → LIBERADO; BULTO → aceptación por dictamen solo con PRECINTO_ROTO;
 * R8, R9, R14) y recall (LOTE → RECALL; DESPACHO → solo bultos).
 */
@ExtendWith(MockitoExtension.class)
class CuarentenaServiceTest {

    @Mock
    private CuarentenaRepository repository;

    @Mock
    private LoteRepository loteRepository;

    @Mock
    private BultoRepository bultoRepository;

    @Mock
    private UnidadTrazableRepository unidadTrazableRepository;

    @Mock
    private RecepcionRepository recepcionRepository;

    @Mock
    private ReporteCiudadanoRepository reporteCiudadanoRepository;

    @Mock
    private InspectorAnmatRepository inspectorAnmatRepository;

    @Mock
    private UsuarioActual usuarioActual;

    @Mock
    private RegistradorEventos registradorEventos;

    private Empresa laboratorio;
    private Empresa distribuidora;
    private Empresa farmacia;
    private Lote lote;
    private EnlaceCuit circuito;
    private InspectorAnmat inspector;
    private UsuarioAutenticado actual;

    @BeforeEach
    void setUp() {
        laboratorio = DatosDePrueba.empresaHabilitada(TipoEmpresa.LABORATORIO);
        distribuidora = DatosDePrueba.empresaHabilitada(TipoEmpresa.DISTRIBUIDOR);
        farmacia = DatosDePrueba.empresaHabilitada(TipoEmpresa.FARMACIA);
        lote = DatosDePrueba.loteDe(laboratorio);
        lote.liberar(null);
        circuito = DatosDePrueba.circuitoAprobado(laboratorio, distribuidora, farmacia);
        inspector = DatosDePrueba.inspector(Provincia.CORDOBA);
        actual = DatosDePrueba.autenticadoInspector(inspector);
        lenient().when(usuarioActual.obtener()).thenReturn(actual);
        lenient().when(inspectorAnmatRepository.findByUsuarioId(actual.getUsuarioId())).thenReturn(Optional.of(inspector));
        lenient().when(loteRepository.findById(lote.getId())).thenReturn(Optional.of(lote));
        lenient().when(repository.save(any(Cuarentena.class))).thenAnswer(inv -> {
            Cuarentena c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(UUID.randomUUID());
            }
            return c;
        });
    }

    /** Construye el Service bajo prueba con los mocks. */
    private CuarentenaService service() {
        return new CuarentenaService(repository, loteRepository, bultoRepository, unidadTrazableRepository,
                recepcionRepository, reporteCiudadanoRepository, inspectorAnmatRepository, usuarioActual,
                registradorEventos);
    }

    /** DTO de apertura manual de LOTE. */
    private CuarentenaRequestDTO dto(Lote delLote) {
        CuarentenaRequestDTO dto = new CuarentenaRequestDTO();
        dto.setLoteId(delLote.getId());
        dto.setMotivo(MotivoBloqueo.DEFECTO_CALIDAD);
        dto.setDescripcion("Texto libre del inspector");
        return dto;
    }

    /** Registra la medida en el mock. */
    private Cuarentena enRepositorio(Cuarentena cuarentena) {
        cuarentena.setId(UUID.randomUUID());
        lenient().when(repository.findById(cuarentena.getId())).thenReturn(Optional.of(cuarentena));
        return cuarentena;
    }

    /** Verifica que la acción falle con el código dado. */
    private void fallaCon(String codigo, Runnable accion) {
        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class, accion::run);
        assertEquals(codigo, ex.getCodigoRegla());
    }

    // ---------- Apertura manual de LOTE ----------

    @Test
    @DisplayName("Abrir: el lote pasa a CUARENTENA, la provincia es la del laboratorio y el inspector queda como revisor")
    void abrir() {
        Cuarentena medida = service().abrir(dto(lote));

        assertEquals(EstadoLote.CUARENTENA, lote.getEstado());
        assertEquals(AlcanceCuarentena.LOTE, medida.getAlcance());
        assertEquals(Provincia.CORDOBA, medida.getProvincia());
        assertTrue(medida.esRevisor(inspector));
        verify(registradorEventos).registrar(eq(TipoEvento.CUARENTENA), eq("Cuarentena"), eq(medida.getId()),
                anyMap(), eq(actual));
    }

    @Test
    @DisplayName("Abrir sobre un lote PENDIENTE_LIBERACION o ya en cuarentena → TRANSICION_INVALIDA")
    void abrirLoteNoLiberado() {
        Lote pendiente = DatosDePrueba.loteDe(laboratorio);
        when(loteRepository.findById(pendiente.getId())).thenReturn(Optional.of(pendiente));
        fallaCon("TRANSICION_INVALIDA", () -> service().abrir(dto(pendiente)));

        CuarentenaService service = service();
        service.abrir(dto(lote));
        fallaCon("TRANSICION_INVALIDA", () -> service.abrir(dto(lote)));
    }

    @Test
    @DisplayName("Abrir sobre un lote de un laboratorio de otra provincia → 404")
    void abrirOtraProvincia() {
        InspectorAnmat deMendoza = DatosDePrueba.inspector(Provincia.MENDOZA);
        UsuarioAutenticado otro = DatosDePrueba.autenticadoInspector(deMendoza);
        when(usuarioActual.obtener()).thenReturn(otro);
        when(inspectorAnmatRepository.findByUsuarioId(otro.getUsuarioId())).thenReturn(Optional.of(deMendoza));

        assertThrows(ResourceNotFoundException.class, () -> service().abrir(dto(lote)));
    }

    @Test
    @DisplayName("Abrir desde un reporte EN_INVESTIGACION tomado por el inspector y con caja del lote → vinculada")
    void abrirDesdeReporte() {
        UnidadTrazable caja = DatosDePrueba.cajaEnStock(lote, "S1", farmacia);
        ReporteCiudadano reporte = new ReporteCiudadano("REP-0001", null, caja.getGtin(), "S1",
                MotivoReporte.EFECTO_ADVERSO, Provincia.CORDOBA, null, caja);
        reporte.setId(UUID.randomUUID());
        when(reporteCiudadanoRepository.findById(reporte.getId())).thenReturn(Optional.of(reporte));
        CuarentenaRequestDTO dto = dto(lote);
        dto.setReporteId(reporte.getId());

        fallaCon("TRANSICION_INVALIDA", () -> service().abrir(dto));

        reporte.tomar(inspector);
        Cuarentena medida = service().abrir(dto);
        assertSame(reporte, medida.getReporteOrigen());
    }

    // ---------- Tomar ----------

    @Test
    @DisplayName("Tomar: queda como revisor y se emite CUARENTENA_TOMADA; tomar una ya tomada → TRANSICION_INVALIDA")
    void tomar() {
        Cuarentena medida = enRepositorio(medidaDeDespacho(MotivoBloqueo.RUPTURA_FRIO));
        CuarentenaService service = service();

        service.tomar(medida.getId());

        assertTrue(medida.esRevisor(inspector));
        verify(registradorEventos).registrar(eq(TipoEvento.CUARENTENA_TOMADA), eq("Cuarentena"), eq(medida.getId()),
                anyMap(), eq(actual));
        fallaCon("TRANSICION_INVALIDA", () -> service.tomar(medida.getId()));
    }

    @Test
    @DisplayName("Tomar una medida de otra provincia → 404")
    void tomarOtraProvincia() {
        Cuarentena medida = enRepositorio(Cuarentena.automaticaDeDespacho(MotivoBloqueo.RUPTURA_FRIO, Provincia.MENDOZA,
                viajeEnTransito(), List.of()));

        assertThrows(ResourceNotFoundException.class, () -> service().tomar(medida.getId()));
    }

    // ---------- Levantar ----------

    @Test
    @DisplayName("Levantar sin haber tomado → TRANSICION_INVALIDA")
    void levantarSinTomar() {
        Cuarentena medida = enRepositorio(medidaDeDespacho(MotivoBloqueo.ROBO));

        fallaCon("TRANSICION_INVALIDA", () -> service().levantar(medida.getId(), "x"));
    }

    @Test
    @DisplayName("Levantar LOTE: el lote vuelve a LIBERADO; CUARENTENA_LEVANTADA con el hash del fundamento")
    @SuppressWarnings("unchecked")
    void levantarLote() {
        CuarentenaService service = service();
        Cuarentena medida = service.abrir(dto(lote));
        when(repository.findById(medida.getId())).thenReturn(Optional.of(medida));

        service.levantar(medida.getId(), "Análisis conforme");

        assertEquals(EstadoLote.LIBERADO, lote.getEstado());
        assertEquals(EstadoCuarentena.LEVANTADA, medida.getEstado());
        ArgumentCaptor<Map<String, Object>> datos = ArgumentCaptor.forClass(Map.class);
        verify(registradorEventos).registrar(eq(TipoEvento.CUARENTENA_LEVANTADA), eq("Cuarentena"), eq(medida.getId()),
                datos.capture(), eq(actual));
        assertEquals("LOTE_LIBERADO", datos.getValue().get("efecto"));
        assertEquals(HashUtil.sha256Hex("Análisis conforme"), datos.getValue().get("fundamentoHash"));
    }

    @Test
    @DisplayName("Levantar una ruptura de frío → 409 R9; un robo → 409 R14 (solo cabe recall)")
    void levantarRupturaYRobo() {
        Cuarentena ruptura = enRepositorio(medidaDeDespacho(MotivoBloqueo.RUPTURA_FRIO));
        ruptura.tomar(inspector);
        fallaCon("R9", () -> service().levantar(ruptura.getId(), "x"));

        Cuarentena robo = enRepositorio(medidaDeDespacho(MotivoBloqueo.ROBO));
        robo.tomar(inspector);
        fallaCon("R14", () -> service().levantar(robo.getId(), "x"));
    }

    @Test
    @DisplayName("Levantar BULTO rechazado SOLO por precinto roto (tramo 1) → bulto y cajas EN_DEPOSITO")
    void levantarBultoPrecintoTramoUno() {
        Bulto bulto = DatosDePrueba.bulto("BUL-0001", lote, circuito);
        UnidadTrazable caja = rechazado(bulto, TramoDespacho.LAB_A_DISTRIBUIDOR, distribuidora,
                List.of(MotivoRechazoRecepcion.PRECINTO_ROTO));
        Cuarentena medida = enRepositorio(Cuarentena.automaticaDeBulto(Provincia.CORDOBA, bulto));
        medida.tomar(inspector);

        service().levantar(medida.getId(), "Contenido verificado");

        assertEquals(EstadoBulto.EN_DEPOSITO, bulto.getEstado());
        assertEquals(EstadoUnidad.EN_DEPOSITO, caja.getEstado());
        assertTrue(caja.estaEn(distribuidora));
    }

    @Test
    @DisplayName("Levantar BULTO rechazado SOLO por precinto roto (tramo 2) → RECIBIDO y cajas EN_STOCK")
    void levantarBultoPrecintoTramoDos() {
        Bulto bulto = DatosDePrueba.bulto("BUL-0001", lote, circuito);
        UnidadTrazable caja = rechazado(bulto, TramoDespacho.DISTRIBUIDOR_A_FARMACIA, farmacia,
                List.of(MotivoRechazoRecepcion.PRECINTO_ROTO));
        Cuarentena medida = enRepositorio(Cuarentena.automaticaDeBulto(Provincia.CORDOBA, bulto));
        medida.tomar(inspector);

        service().levantar(medida.getId(), "Contenido verificado");

        assertEquals(EstadoBulto.RECIBIDO, bulto.getEstado());
        assertEquals(EstadoUnidad.EN_STOCK, caja.getEstado());
    }

    @Test
    @DisplayName("Levantar BULTO rechazado por temperatura o por cantidad → 409 R8 (solo cabe recall)")
    void levantarBultoConTemperaturaOCantidad() {
        for (List<MotivoRechazoRecepcion> motivos : List.of(
                List.of(MotivoRechazoRecepcion.TEMPERATURA_FUERA_DE_RANGO),
                List.of(MotivoRechazoRecepcion.PRECINTO_ROTO, MotivoRechazoRecepcion.CANTIDAD_DISTINTA))) {
            Bulto bulto = DatosDePrueba.bulto("BUL-X", lote, circuito);
            UnidadTrazable caja = rechazado(bulto, TramoDespacho.LAB_A_DISTRIBUIDOR, distribuidora, motivos);
            Cuarentena medida = enRepositorio(Cuarentena.automaticaDeBulto(Provincia.CORDOBA, bulto));
            medida.tomar(inspector);

            fallaCon("R8", () -> service().levantar(medida.getId(), "x"));
            assertEquals(EstadoUnidad.RECHAZADA, caja.getEstado());
            assertEquals(EstadoCuarentena.ACTIVA, medida.getEstado());
        }
    }

    // ---------- Recall ----------

    @Test
    @DisplayName("Recall de LOTE: el lote pasa a RECALL y RECALL lleva las cajas afectadas")
    @SuppressWarnings("unchecked")
    void recallLote() {
        CuarentenaService service = service();
        Cuarentena medida = service.abrir(dto(lote));
        when(repository.findById(medida.getId())).thenReturn(Optional.of(medida));
        when(unidadTrazableRepository.countByLoteId(lote.getId())).thenReturn(100L);

        service.recall(medida.getId(), "Defecto confirmado");

        assertEquals(EstadoLote.RECALL, lote.getEstado());
        assertEquals(EstadoCuarentena.CONVERTIDA_EN_RECALL, medida.getEstado());
        ArgumentCaptor<Map<String, Object>> datos = ArgumentCaptor.forClass(Map.class);
        verify(registradorEventos).registrar(eq(TipoEvento.RECALL), eq("Cuarentena"), eq(medida.getId()),
                datos.capture(), eq(actual));
        assertEquals(100L, datos.getValue().get("cajasAfectadas"));
        fallaCon("TRANSICION_INVALIDA", () -> service.levantar(medida.getId(), "x"));
    }

    @Test
    @DisplayName("Recall de DESPACHO (ruptura de frío): solo esos bultos; el lote NO cambia")
    void recallDespacho() {
        Cuarentena medida = enRepositorio(medidaDeDespacho(MotivoBloqueo.RUPTURA_FRIO));
        medida.tomar(inspector);
        when(unidadTrazableRepository.findByBultoIdIn(anyCollection())).thenReturn(List.of());

        service().recall(medida.getId(), "Cadena de frío rota");

        assertEquals(EstadoCuarentena.CONVERTIDA_EN_RECALL, medida.getEstado());
        assertEquals(EstadoLote.LIBERADO, lote.getEstado());
        verify(loteRepository, never()).save(any());
    }

    @Test
    @DisplayName("Visibilidad: el laboratorio ve la medida sobre su lote; otro laboratorio → 404")
    void visibilidad() {
        Cuarentena medida = enRepositorio(Cuarentena.manualDeLote(lote, MotivoBloqueo.PREVENTIVA, null, inspector, null));
        when(usuarioActual.obtener()).thenReturn(DatosDePrueba.autenticado(RolUsuario.LABORATORIO, laboratorio));
        assertSame(medida, service().getById(medida.getId()));

        when(usuarioActual.obtener()).thenReturn(DatosDePrueba.autenticado(RolUsuario.LABORATORIO,
                DatosDePrueba.empresaHabilitada(TipoEmpresa.LABORATORIO)));
        assertThrows(ResourceNotFoundException.class, () -> service().getById(medida.getId()));
    }

    // ---------- Helpers ----------

    /** Viaje del tramo 1 EN_TRANSITO, sin bultos. */
    private DespachoLogistico viajeEnTransito() {
        DespachoLogistico viaje = DatosDePrueba.viaje(TramoDespacho.LAB_A_DISTRIBUIDOR, laboratorio);
        return viaje;
    }

    /** Medida DESPACHO de Córdoba sobre un bulto. */
    private Cuarentena medidaDeDespacho(MotivoBloqueo motivo) {
        return Cuarentena.automaticaDeDespacho(motivo, Provincia.CORDOBA, viajeEnTransito(),
                List.of(DatosDePrueba.bulto("BUL-0009", lote, circuito)));
    }

    /**
     * Lleva el bulto (con una caja) por el tramo dado hasta que la receptora lo
     * rechaza con los motivos dados, y registra esa recepción en el mock.
     */
    private UnidadTrazable rechazado(Bulto bulto, TramoDespacho tramo, Empresa receptora,
                                     List<MotivoRechazoRecepcion> motivos) {
        UnidadTrazable caja = DatosDePrueba.caja(lote, "S-" + UUID.randomUUID().toString().substring(0, 8));
        caja.asignarABulto(bulto);
        DespachoLogistico viaje;
        if (tramo == TramoDespacho.DISTRIBUIDOR_A_FARMACIA) {
            DespachoLogistico primero = DatosDePrueba.viaje(TramoDespacho.LAB_A_DISTRIBUIDOR, laboratorio);
            primero.agregarBulto(bulto);
            primero.registrarSalida();
            bulto.salir();
            caja.salir();
            bulto.recibir(distribuidora);
            caja.recibirEnDeposito(distribuidora);
            viaje = DatosDePrueba.viaje(TramoDespacho.DISTRIBUIDOR_A_FARMACIA, distribuidora);
        } else {
            viaje = DatosDePrueba.viaje(TramoDespacho.LAB_A_DISTRIBUIDOR, laboratorio);
        }
        viaje.agregarBulto(bulto);
        viaje.registrarSalida();
        bulto.salir();
        caja.salir();
        bulto.rechazar(receptora);
        caja.quedarRechazadaEn(receptora);
        Recepcion recepcion = new Recepcion(bulto, viaje, receptora, null, new BigDecimal("20"), false, 10, motivos, null);
        lenient().when(recepcionRepository.findByBultoIdOrderByFechaHoraAsc(bulto.getId())).thenReturn(List.of(recepcion));
        lenient().when(unidadTrazableRepository.findByBultoIdIn(anyCollection())).thenReturn(List.of(caja));
        return caja;
    }
}
