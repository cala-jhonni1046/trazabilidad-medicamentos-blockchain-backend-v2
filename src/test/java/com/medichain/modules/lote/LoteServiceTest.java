package com.medichain.modules.lote;

import com.medichain.exceptions.ReglaNegocioException;
import com.medichain.exceptions.ResourceNotFoundException;
import com.medichain.modules.auth.UsuarioAutenticado;
import com.medichain.modules.empresa.Empresa;
import com.medichain.modules.empresa.TipoEmpresa;
import com.medichain.modules.inspectoranmat.InspectorAnmat;
import com.medichain.modules.inspectoranmat.InspectorAnmatRepository;
import com.medichain.modules.medicamento.Medicamento;
import com.medichain.modules.medicamento.MedicamentoRepository;
import com.medichain.modules.trazabilidad.HashUtil;
import com.medichain.modules.trazabilidad.JsonCanonico;
import com.medichain.modules.trazabilidad.RegistradorEventos;
import com.medichain.modules.trazabilidad.TipoEvento;
import com.medichain.modules.unidadtrazable.EstadoUnidad;
import com.medichain.modules.unidadtrazable.UnidadTrazable;
import com.medichain.modules.unidadtrazable.UnidadTrazableRepository;
import com.medichain.modules.usuario.RolUsuario;
import com.medichain.modules.usuario.Usuario;
import com.medichain.testutil.DatosDePrueba;
import com.medichain.utils.enums.Provincia;
import com.medichain.utils.seguridad.UsuarioActual;
import com.medichain.utils.seguridad.VerificadorEmpresa;
import com.medichain.utils.seguridad.VerificadorUsuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test unitario LoteServiceTest en MediChain.
 * Con Mockito: visibilidad, registro del lote con sus series (R3: formato,
 * repetidas, existentes por GTIN; INTENTO_SERIE_INVALIDA aunque el lote no
 * se cree; código único por laboratorio; carreras de código y de series
 * contra los índices únicos) y liberación (R4, R10).
 */
@ExtendWith(MockitoExtension.class)
class LoteServiceTest {

    @Mock
    private RegistradorEventos registradorEventos;

    @Mock
    private LoteRepository repository;

    @Mock
    private UsuarioActual usuarioActual;

    @Mock
    private MedicamentoRepository medicamentoRepository;

    @Mock
    private UnidadTrazableRepository unidadTrazableRepository;

    @Mock
    private InspectorAnmatRepository inspectorAnmatRepository;

    @Mock
    private VerificadorEmpresa verificadorEmpresa;

    @Mock
    private VerificadorUsuario verificadorUsuario;

    @Mock
    private RegistroIntentos registroIntentos;

    private final Pageable pagina = PageRequest.of(0, 20);
    private Empresa laboratorio;
    private Medicamento medicamento;

    @BeforeEach
    void setUp() {
        laboratorio = DatosDePrueba.empresaHabilitada(TipoEmpresa.LABORATORIO);
        medicamento = DatosDePrueba.loteDe(laboratorio).getMedicamento();
    }

    /** Construye el Service bajo prueba con los mocks. */
    private LoteService service() {
        return new LoteService(repository, medicamentoRepository, unidadTrazableRepository, inspectorAnmatRepository,
                usuarioActual, verificadorEmpresa, verificadorUsuario, registroIntentos, registradorEventos);
    }

    /** Autentica como usuario del laboratorio y prepara medicamento propio, guardado y sin series existentes. */
    private UsuarioAutenticado comoLaboratorio() {
        UsuarioAutenticado actual = DatosDePrueba.autenticado(RolUsuario.LABORATORIO, laboratorio);
        when(usuarioActual.obtener()).thenReturn(actual);
        lenient().when(verificadorEmpresa.exigirHabilitada(laboratorio.getId())).thenReturn(laboratorio);
        lenient().when(medicamentoRepository.findById(medicamento.getId())).thenReturn(Optional.of(medicamento));
        lenient().when(repository.saveAndFlush(any(Lote.class))).thenAnswer(inv -> {
            Lote lote = inv.getArgument(0);
            if (lote.getId() == null) {
                lote.setId(UUID.randomUUID());
            }
            return lote;
        });
        lenient().when(unidadTrazableRepository.findSeriesExistentes(anyString(), anyCollection())).thenReturn(List.of());
        return actual;
    }

    /** DTO de lote con las fechas válidas. */
    private LoteRequestDTO dto(String codigo) {
        LoteRequestDTO dto = new LoteRequestDTO();
        dto.setCodigo(codigo);
        dto.setFechaFabricacion(LocalDate.now().minusMonths(1));
        dto.setFechaVencimiento(LocalDate.now().plusYears(2));
        dto.setMedicamentoId(medicamento.getId());
        return dto;
    }

    /** Verifica que la acción falle con el código dado. */
    private void fallaCon(String codigo, Runnable accion) {
        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class, accion::run);
        assertEquals(codigo, ex.getCodigoRegla());
    }

    /** Lote del laboratorio, del medicamento dado, registrado en el mock. */
    private Lote loteEnRepositorio(Medicamento delMedicamento) {
        Lote lote = new Lote("L2026-0009", LocalDate.now().minusMonths(1), LocalDate.now().plusYears(1), 10,
                delMedicamento);
        lote.setId(UUID.randomUUID());
        lenient().when(repository.findById(lote.getId())).thenReturn(Optional.of(lote));
        lenient().when(repository.save(any(Lote.class))).thenAnswer(inv -> inv.getArgument(0));
        return lote;
    }

    // ---------- Visibilidad ----------

    @Test
    @DisplayName("Ve lo suyo: el laboratorio ve un lote de sus medicamentos")
    void laboratorioVeSuLote() {
        Lote lote = loteEnRepositorio(medicamento);
        when(usuarioActual.obtener()).thenReturn(DatosDePrueba.autenticado(RolUsuario.LABORATORIO, laboratorio));

        assertSame(lote, service().getById(lote.getId()));
    }

    @Test
    @DisplayName("No ve lo ajeno: un lote de otro laboratorio responde 404 (también sus cajas)")
    void laboratorioNoVeLoteAjeno() {
        Lote lote = loteEnRepositorio(medicamento);
        Empresa otro = DatosDePrueba.empresaHabilitada(TipoEmpresa.LABORATORIO);
        when(usuarioActual.obtener()).thenReturn(DatosDePrueba.autenticado(RolUsuario.LABORATORIO, otro));

        assertThrows(ResourceNotFoundException.class, () -> service().getById(lote.getId()));
        assertThrows(ResourceNotFoundException.class, () -> service().unidades(lote.getId(), pagina));
        verify(unidadTrazableRepository, never()).findByLoteId(any(), any());
    }

    @Test
    @DisplayName("D7: la farmacia lista solo los lotes con bultos que ya salieron hacia ella, nunca todo")
    void farmaciaNoListaTodo() {
        Empresa farmacia = DatosDePrueba.empresaHabilitada(TipoEmpresa.FARMACIA);
        when(usuarioActual.obtener()).thenReturn(DatosDePrueba.autenticado(RolUsuario.FARMACIA, farmacia));
        when(repository.findVisiblesParaFarmacia(farmacia.getId(), pagina)).thenReturn(Page.empty(pagina));

        Page<Lote> resultado = service().getAll(pagina);

        assertTrue(resultado.isEmpty());
        verify(repository, never()).findAll(any(Pageable.class));
    }

    // ---------- Registro ----------

    @Test
    @DisplayName("Registro con series generadas: L2026-0002 → L20260002S000001…, cajas EN_LABORATORIO con el GTIN, evento con seriesHash")
    @SuppressWarnings("unchecked")
    void registroGenerado() {
        UsuarioAutenticado actual = comoLaboratorio();
        LoteRequestDTO dto = dto("L2026-0002");
        dto.setCantidad(3);

        Lote lote = service().registrar(dto);

        assertEquals(EstadoLote.PENDIENTE_LIBERACION, lote.getEstado());
        assertEquals(3, lote.getCantidad());
        assertSame(laboratorio, lote.getLaboratorio());
        ArgumentCaptor<List<UnidadTrazable>> cajas = ArgumentCaptor.forClass(List.class);
        verify(unidadTrazableRepository).saveAllAndFlush(cajas.capture());
        List<String> series = cajas.getValue().stream().map(UnidadTrazable::getSerie).toList();
        assertEquals(List.of("L20260002S000001", "L20260002S000002", "L20260002S000003"), series);
        UnidadTrazable primera = cajas.getValue().get(0);
        assertEquals(EstadoUnidad.EN_LABORATORIO, primera.getEstado());
        assertEquals(medicamento.getGtin(), primera.getGtin());
        assertSame(laboratorio, primera.getEmpresaActual());

        ArgumentCaptor<Map<String, Object>> datos = ArgumentCaptor.forClass(Map.class);
        verify(registradorEventos).registrar(eq(TipoEvento.LOTE_REGISTRADO), eq("Lote"), eq(lote.getId()),
                datos.capture(), eq(actual));
        assertEquals(HashUtil.seriesHash(series), datos.getValue().get("seriesHash"));
        assertEquals(3, datos.getValue().get("cantidadSeries"));
        assertEquals("GENERADAS", datos.getValue().get("origenSeries"));
        assertFalse(JsonCanonico.escribir(datos.getValue()).contains("S000001"), "el evento no lleva la lista de series");
        verify(registroIntentos, never()).registrarIntentoSerieInvalida(any(), any(), anyInt(), anyInt(), anyInt(),
                anyInt(), anyList(), any(), any());
    }

    @Test
    @DisplayName("Registro con lista explícita: seriesHash coincide con HashUtil.seriesHash de la lista")
    @SuppressWarnings("unchecked")
    void registroConLista() {
        comoLaboratorio();
        LoteRequestDTO dto = dto("ABC-1");
        dto.setSeries(List.of("ZZ02", "AA01", "MM03"));

        service().registrar(dto);

        ArgumentCaptor<Map<String, Object>> datos = ArgumentCaptor.forClass(Map.class);
        verify(registradorEventos).registrar(eq(TipoEvento.LOTE_REGISTRADO), eq("Lote"), any(), datos.capture(),
                any(UsuarioAutenticado.class));
        assertEquals(HashUtil.seriesHash(List.of("AA01", "MM03", "ZZ02")), datos.getValue().get("seriesHash"));
        assertEquals("LISTA", datos.getValue().get("origenSeries"));
    }

    @Test
    @DisplayName("R3: serie que empieza con 779, de 21 caracteres o con guion → 409 R3, no se crea el lote, sí el intento")
    void seriesInvalidas() {
        UsuarioAutenticado actual = comoLaboratorio();
        LoteRequestDTO dto = dto("L2026-0003");
        dto.setSeries(List.of("OK001", "779123", "A".repeat(21), "CON-GUION"));

        fallaCon("R3", () -> service().registrar(dto));

        verify(registroIntentos).registrarIntentoSerieInvalida(eq("L2026-0003"), eq(medicamento), eq(4), eq(3),
                eq(0), eq(0), anyList(), anyString(), eq(actual));
        verify(repository, never()).saveAndFlush(any());
        verify(unidadTrazableRepository, never()).saveAllAndFlush(any());
        verify(registradorEventos, never()).registrar(any(), any(), any(), anyMap(), any(UsuarioAutenticado.class));
    }

    @Test
    @DisplayName("R3: serie repetida dentro de la lista → 409 R3 con el intento registrado")
    void serieRepetidaEnLaLista() {
        comoLaboratorio();
        LoteRequestDTO dto = dto("L2026-0004");
        dto.setSeries(List.of("A1", "B2", "A1"));

        fallaCon("R3", () -> service().registrar(dto));

        verify(registroIntentos).registrarIntentoSerieInvalida(any(), any(), eq(3), eq(0), eq(1), eq(0),
                eq(List.of("A1")), anyString(), any());
    }

    @Test
    @DisplayName("R3: serie que ya existe para el MISMO GTIN → 409 R3; la consulta es por el GTIN del medicamento")
    void serieExistenteMismoGtin() {
        comoLaboratorio();
        when(unidadTrazableRepository.findSeriesExistentes(eq(medicamento.getGtin()), anyCollection()))
                .thenReturn(List.of("B2"));
        LoteRequestDTO dto = dto("L2026-0005");
        dto.setSeries(List.of("A1", "B2"));

        fallaCon("R3", () -> service().registrar(dto));

        verify(registroIntentos).registrarIntentoSerieInvalida(any(), any(), eq(2), eq(0), eq(0), eq(1),
                eq(List.of("B2")), anyString(), any());
    }

    @Test
    @DisplayName("La misma serie en OTRO GTIN se acepta (la consulta de existentes es por GTIN)")
    void mismaSerieEnOtroGtinSeAcepta() {
        comoLaboratorio();
        // La serie B2 existe para otro GTIN, no para el de este medicamento.
        lenient().when(unidadTrazableRepository.findSeriesExistentes(eq("07791234567898"), anyCollection()))
                .thenReturn(List.of());
        lenient().when(unidadTrazableRepository.findSeriesExistentes(eq("07799999999999"), anyCollection()))
                .thenReturn(List.of("B2"));
        LoteRequestDTO dto = dto("L2026-0006");
        dto.setSeries(List.of("A1", "B2"));

        service().registrar(dto);

        verify(unidadTrazableRepository).saveAllAndFlush(anyList());
    }

    @Test
    @DisplayName("Código de lote ya usado por el MISMO laboratorio → 409 LOTE_DUPLICADO; en otro laboratorio se acepta")
    void codigoDuplicadoPorLaboratorio() {
        comoLaboratorio();
        when(repository.existsByLaboratorioIdAndCodigo(laboratorio.getId(), "L2026-0001")).thenReturn(true);
        LoteRequestDTO dto = dto("L2026-0001");
        dto.setCantidad(1);
        fallaCon("LOTE_DUPLICADO", () -> service().registrar(dto));

        // Otro laboratorio con el mismo código: la verificación es por laboratorio, así que pasa.
        laboratorio = DatosDePrueba.empresaHabilitada(TipoEmpresa.LABORATORIO);
        medicamento = DatosDePrueba.loteDe(laboratorio).getMedicamento();
        comoLaboratorio();
        LoteRequestDTO otro = dto("L2026-0001");
        otro.setCantidad(1);
        service().registrar(otro);
        verify(unidadTrazableRepository).saveAllAndFlush(anyList());
    }

    @Test
    @DisplayName("Carrera de código: otro alta del mismo código entró al mismo tiempo (ux_lote_laboratorio_codigo) → 409 LOTE_DUPLICADO")
    void carreraDeCodigoDeLote() {
        comoLaboratorio();
        doThrow(new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"ux_lote_laboratorio_codigo\""))
                .when(repository).saveAndFlush(any(Lote.class));
        LoteRequestDTO dto = dto("L2026-0010");
        dto.setCantidad(2);

        fallaCon("LOTE_DUPLICADO", () -> service().registrar(dto));
        verify(unidadTrazableRepository, never()).saveAllAndFlush(any());
        verify(registroIntentos, never()).registrarChoqueDeSeries(any(), any(), anyList(), any(), any());
        verify(registradorEventos, never()).registrar(any(), any(), any(), anyMap(), any(UsuarioAutenticado.class));
    }

    @Test
    @DisplayName("Carrera de series: otro lote del mismo GTIN las registró al mismo tiempo (ux_unidad_gtin_serie) → 409 R3 + intento")
    void carreraDeSeries() {
        UsuarioAutenticado actual = comoLaboratorio();
        doThrow(new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"ux_unidad_gtin_serie\""))
                .when(unidadTrazableRepository).saveAllAndFlush(anyList());
        List<String> series = List.of("A1", "B2", "C3");
        when(registroIntentos.registrarChoqueDeSeries("L2026-0011", medicamento, series,
                HashUtil.seriesHash(series), actual)).thenReturn(List.of("B2"));
        LoteRequestDTO dto = dto("L2026-0011");
        dto.setSeries(series);

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class, () -> service().registrar(dto));
        assertEquals("R3", ex.getCodigoRegla());
        assertTrue(ex.getMessage().contains("[B2]"), ex.getMessage());
        verify(registradorEventos, never()).registrar(eq(TipoEvento.LOTE_REGISTRADO), any(), any(), anyMap(),
                any(UsuarioAutenticado.class));
    }

    @Test
    @DisplayName("Otra violación de integridad al guardar las cajas se propaga sin traducir ni registrar intento")
    void otraViolacionEnCajasSePropaga() {
        comoLaboratorio();
        DataIntegrityViolationException otra = new DataIntegrityViolationException("otra restricción");
        doThrow(otra).when(unidadTrazableRepository).saveAllAndFlush(anyList());
        LoteRequestDTO dto = dto("L2026-0012");
        dto.setCantidad(1);

        assertSame(otra, assertThrows(DataIntegrityViolationException.class, () -> service().registrar(dto)));
        verify(registroIntentos, never()).registrarChoqueDeSeries(any(), any(), anyList(), any(), any());
    }

    @Test
    @DisplayName("Medicamento de otro laboratorio → 404; laboratorio no habilitado → R2")
    void medicamentoAjenoYLaboratorioNoHabilitado() {
        comoLaboratorio();
        Medicamento ajeno = DatosDePrueba.loteDe(DatosDePrueba.empresaHabilitada(TipoEmpresa.LABORATORIO)).getMedicamento();
        when(medicamentoRepository.findById(ajeno.getId())).thenReturn(Optional.of(ajeno));
        LoteRequestDTO dto = dto("L2026-0007");
        dto.setMedicamentoId(ajeno.getId());
        dto.setCantidad(1);
        assertThrows(ResourceNotFoundException.class, () -> service().registrar(dto));

        when(verificadorEmpresa.exigirHabilitada(laboratorio.getId()))
                .thenThrow(new ReglaNegocioException("R2", "no habilitada"));
        fallaCon("R2", () -> service().registrar(dto));
    }

    // ---------- Liberación ----------

    /** Medicamento biológico del laboratorio. */
    private Medicamento biologico() {
        Medicamento vacuna = new Medicamento("07799000002024", "Andivax", "Vacuna", "0,5 ml", "Inyectable", "x1");
        vacuna.setId(UUID.randomUUID());
        vacuna.setLaboratorio(laboratorio);
        vacuna.setBiologico(true);
        return vacuna;
    }

    /** Autentica como DT del laboratorio. */
    private Usuario comoDirectorTecnico() {
        UsuarioAutenticado actual = DatosDePrueba.autenticado(RolUsuario.LABORATORIO, laboratorio);
        when(usuarioActual.obtener()).thenReturn(actual);
        Usuario dt = new Usuario("dt@lab.demo", "hash", "Elena", "Sosa", "12345678", RolUsuario.LABORATORIO);
        lenient().when(verificadorUsuario.exigirDirectorTecnico(actual.getUsuarioId())).thenReturn(dt);
        lenient().when(verificadorEmpresa.exigirHabilitada(laboratorio.getId())).thenReturn(laboratorio);
        return dt;
    }

    /** Autentica como el inspector dado. */
    private void comoInspector(InspectorAnmat inspector) {
        UsuarioAutenticado actual = DatosDePrueba.autenticadoInspector(inspector);
        when(usuarioActual.obtener()).thenReturn(actual);
        when(inspectorAnmatRepository.findByUsuarioId(actual.getUsuarioId())).thenReturn(Optional.of(inspector));
    }

    @Test
    @DisplayName("Común liberado por su DT → LIBERADO, evento LOTE_LIBERADO como DIRECTOR_TECNICO")
    @SuppressWarnings("unchecked")
    void comunLiberadoPorDt() {
        Lote lote = loteEnRepositorio(medicamento);
        Usuario dt = comoDirectorTecnico();

        service().liberar(lote.getId());

        assertEquals(EstadoLote.LIBERADO, lote.getEstado());
        assertSame(dt, lote.getLiberadoPor());
        ArgumentCaptor<Map<String, Object>> datos = ArgumentCaptor.forClass(Map.class);
        verify(registradorEventos).registrar(eq(TipoEvento.LOTE_LIBERADO), eq("Lote"), eq(lote.getId()),
                datos.capture(), any(UsuarioAutenticado.class));
        assertEquals("DIRECTOR_TECNICO", datos.getValue().get("liberadoComo"));
    }

    @Test
    @DisplayName("Liberar sin ser DT → 403")
    void liberarSinSerDt() {
        Lote lote = loteEnRepositorio(medicamento);
        UsuarioAutenticado actual = DatosDePrueba.autenticado(RolUsuario.LABORATORIO, laboratorio);
        when(usuarioActual.obtener()).thenReturn(actual);
        when(verificadorUsuario.exigirDirectorTecnico(actual.getUsuarioId())).thenThrow(new AccessDeniedException("no DT"));

        assertThrows(AccessDeniedException.class, () -> service().liberar(lote.getId()));
        assertEquals(EstadoLote.PENDIENTE_LIBERACION, lote.getEstado());
    }

    @Test
    @DisplayName("DT de otro laboratorio → 404")
    void dtDeOtroLaboratorio() {
        Lote lote = loteEnRepositorio(medicamento);
        Empresa otro = DatosDePrueba.empresaHabilitada(TipoEmpresa.LABORATORIO);
        when(usuarioActual.obtener()).thenReturn(DatosDePrueba.autenticado(RolUsuario.LABORATORIO, otro));

        assertThrows(ResourceNotFoundException.class, () -> service().liberar(lote.getId()));
    }

    @Test
    @DisplayName("Biológico liberado por el DT → 409 R4")
    void biologicoPorDtFalla() {
        Lote lote = loteEnRepositorio(biologico());
        comoDirectorTecnico();

        fallaCon("R4", () -> service().liberar(lote.getId()));
    }

    @Test
    @DisplayName("Biológico liberado por un inspector de la provincia del laboratorio → LIBERADO como INSPECTOR")
    @SuppressWarnings("unchecked")
    void biologicoPorInspector() {
        Lote lote = loteEnRepositorio(biologico());
        InspectorAnmat inspector = DatosDePrueba.inspector(Provincia.CORDOBA);
        comoInspector(inspector);

        service().liberar(lote.getId());

        assertEquals(EstadoLote.LIBERADO, lote.getEstado());
        ArgumentCaptor<Map<String, Object>> datos = ArgumentCaptor.forClass(Map.class);
        verify(registradorEventos).registrar(eq(TipoEvento.LOTE_LIBERADO), eq("Lote"), eq(lote.getId()),
                datos.capture(), any(UsuarioAutenticado.class));
        assertEquals("INSPECTOR", datos.getValue().get("liberadoComo"));
        assertEquals(inspector.getId(), datos.getValue().get("inspectorId"));
    }

    @Test
    @DisplayName("Inspector de otra provincia → 404; inspector de baja → 403; común liberado por inspector → R4")
    void inspectorInvalido() {
        Lote lote = loteEnRepositorio(biologico());
        comoInspector(DatosDePrueba.inspector(Provincia.MENDOZA));
        assertThrows(ResourceNotFoundException.class, () -> service().liberar(lote.getId()));

        InspectorAnmat deBaja = DatosDePrueba.inspector(Provincia.CORDOBA);
        deBaja.darDeBaja();
        comoInspector(deBaja);
        assertThrows(AccessDeniedException.class, () -> service().liberar(lote.getId()));

        Lote comun = loteEnRepositorio(medicamento);
        comoInspector(DatosDePrueba.inspector(Provincia.CORDOBA));
        fallaCon("R4", () -> service().liberar(comun.getId()));
    }

    @Test
    @DisplayName("Liberar dos veces → TRANSICION_INVALIDA")
    void liberarDosVeces() {
        Lote lote = loteEnRepositorio(medicamento);
        comoDirectorTecnico();
        LoteService service = service();
        service.liberar(lote.getId());

        fallaCon("TRANSICION_INVALIDA", () -> service.liberar(lote.getId()));
    }

    @Test
    @DisplayName("Lote vencido → 409 R10")
    void loteVencido() {
        Lote vencido = new Lote("L2020-0001", LocalDate.of(2020, 1, 1), LocalDate.of(2021, 1, 1), 1, medicamento);
        vencido.setId(UUID.randomUUID());
        when(repository.findById(vencido.getId())).thenReturn(Optional.of(vencido));
        comoDirectorTecnico();

        fallaCon("R10", () -> service().liberar(vencido.getId()));
    }

    @Test
    @DisplayName("Bandeja de liberación: consulta por la provincia del inspector leída de la base")
    void bandejaPorProvincia() {
        comoInspector(DatosDePrueba.inspector(Provincia.CORDOBA));
        when(repository.findBandejaLiberacion(Provincia.CORDOBA, pagina)).thenReturn(Page.empty(pagina));

        service().bandejaLiberacion(pagina);

        verify(repository).findBandejaLiberacion(Provincia.CORDOBA, pagina);
    }

    @Test
    @DisplayName("Generación de series para 10.000 cajas: todas distintas y de hasta 20 caracteres")
    @SuppressWarnings("unchecked")
    void diezMilCajas() {
        comoLaboratorio();
        LoteRequestDTO dto = dto("L2026-9999");
        dto.setCantidad(LoteRequestDTO.MAXIMO_CAJAS);

        service().registrar(dto);

        ArgumentCaptor<List<UnidadTrazable>> cajas = ArgumentCaptor.forClass(List.class);
        verify(unidadTrazableRepository).saveAllAndFlush(cajas.capture());
        List<String> series = new ArrayList<>(cajas.getValue().stream().map(UnidadTrazable::getSerie).toList());
        assertEquals(10_000, series.stream().distinct().count());
        assertEquals("L20269999S010000", series.get(series.size() - 1));
        // 10 tandas de consulta de existentes (IN de hasta 1000).
        verify(unidadTrazableRepository, org.mockito.Mockito.times(10))
                .findSeriesExistentes(eq(medicamento.getGtin()), anyCollection());
    }
}
