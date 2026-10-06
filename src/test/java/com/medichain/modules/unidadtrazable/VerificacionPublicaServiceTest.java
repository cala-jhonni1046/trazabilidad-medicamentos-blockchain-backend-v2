package com.medichain.modules.unidadtrazable;

import com.medichain.modules.bulto.Bulto;
import com.medichain.modules.cuarentena.EvaluadorBloqueo;
import com.medichain.modules.despachologistico.DespachoLogistico;
import com.medichain.modules.despachologistico.DespachoLogisticoRepository;
import com.medichain.modules.despachologistico.TramoDespacho;
import com.medichain.modules.empresa.Empresa;
import com.medichain.modules.empresa.TipoEmpresa;
import com.medichain.modules.lote.Lote;
import com.medichain.modules.medicamento.MedicamentoRepository;
import com.medichain.modules.recepcion.RecepcionRepository;
import com.medichain.modules.registroblockchain.AnclajeProperties;
import com.medichain.modules.registroblockchain.EstadoAnclaje;
import com.medichain.modules.registroblockchain.RegistroBlockchain;
import com.medichain.modules.registroblockchain.RegistroBlockchainRepository;
import com.medichain.modules.trazabilidad.EventoTrazabilidadRepository;
import com.medichain.modules.trazabilidad.TipoEvento;
import com.medichain.testutil.DatosDePrueba;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test unitario VerificacionPublicaServiceTest en MediChain (R10, R13, R14).
 * Cada estado para el paciente, el registro de SERIE_INEXISTENTE /
 * SERIE_ROBADA, que la respuesta no tenga datos del paciente ni de la
 * dispensación, y el anclaje que cubre el recorrido (R15) sin mirar la
 * dispensación (R13).
 */
@ExtendWith(MockitoExtension.class)
class VerificacionPublicaServiceTest {

    @Mock
    private UnidadTrazableRepository unidadTrazableRepository;

    @Mock
    private MedicamentoRepository medicamentoRepository;

    @Mock
    private DespachoLogisticoRepository despachoLogisticoRepository;

    @Mock
    private RecepcionRepository recepcionRepository;

    @Mock
    private EvaluadorBloqueo evaluadorBloqueo;

    @Mock
    private RegistroIntentosVerificacion registroIntentos;

    @Mock
    private EventoTrazabilidadRepository eventoRepository;

    @Mock
    private RegistroBlockchainRepository registroBlockchainRepository;

    private AnclajeProperties propiedadesAnclaje;
    private Empresa farmacia;
    private Lote lote;

    @BeforeEach
    void setUp() {
        farmacia = DatosDePrueba.empresaHabilitada(TipoEmpresa.FARMACIA);
        lote = DatosDePrueba.loteDe(DatosDePrueba.empresaHabilitada(TipoEmpresa.LABORATORIO));
        lote.liberar(null);
        lenient().when(evaluadorBloqueo.bloqueoDeCaja(any())).thenReturn(Optional.empty());
        lenient().when(despachoLogisticoRepository.findSalidasDelBulto(any())).thenReturn(List.of());
        lenient().when(recepcionRepository.findByBultoIdOrderByFechaHoraAsc(any())).thenReturn(List.of());
        propiedadesAnclaje = new AnclajeProperties();
    }

    /** Construye el Service bajo prueba con los mocks. */
    private VerificacionPublicaService service() {
        return new VerificacionPublicaService(unidadTrazableRepository, medicamentoRepository,
                despachoLogisticoRepository, recepcionRepository, evaluadorBloqueo, registroIntentos,
                eventoRepository, registroBlockchainRepository, propiedadesAnclaje);
    }

    /** Registra la caja en el mock y la verifica. */
    private VerificacionPublicaResponseDTO verificar(UnidadTrazable caja) {
        when(unidadTrazableRepository.findByGtinAndSerie(caja.getGtin(), caja.getSerie())).thenReturn(Optional.of(caja));
        return service().verificar(caja.getGtin(), caja.getSerie());
    }

    @Test
    @DisplayName("EN_STOCK en farmacia → APTA, con producto, lote, vencimiento; anclaje deshabilitado → NO_DISPONIBLE")
    void apta() {
        VerificacionPublicaResponseDTO respuesta = verificar(DatosDePrueba.cajaEnStock(lote, "S1", farmacia));

        assertEquals(EstadoVerificacion.APTA, respuesta.getEstado());
        assertEquals(lote.getMedicamento().getNombreComercial(), respuesta.getProducto());
        assertEquals(lote.getCodigo(), respuesta.getLote());
        assertEquals("NO_DISPONIBLE", respuesta.getAnclaje().getEstado());
        assertEquals("FABRICADO", respuesta.getRecorrido().get(0).getEtapa());
    }

    @Test
    @DisplayName("DISPENSADA → YA_DISPENSADA")
    void yaDispensada() {
        UnidadTrazable caja = DatosDePrueba.cajaEnStock(lote, "S1", farmacia);
        caja.dispensar();

        assertEquals(EstadoVerificacion.YA_DISPENSADA, verificar(caja).getEstado());
    }

    @Test
    @DisplayName("Lote en cuarentena (bloqueo), DEVUELTA o RECHAZADA → BLOQUEADA")
    void bloqueada() {
        UnidadTrazable conCuarentena = DatosDePrueba.cajaEnStock(lote, "S1", farmacia);
        when(evaluadorBloqueo.bloqueoDeCaja(conCuarentena)).thenReturn(Optional.of("el lote está en cuarentena"));
        assertEquals(EstadoVerificacion.BLOQUEADA, verificar(conCuarentena).getEstado());

        UnidadTrazable devuelta = DatosDePrueba.cajaEnStock(lote, "S2", farmacia);
        devuelta.devolver();
        assertEquals(EstadoVerificacion.BLOQUEADA, verificar(devuelta).getEstado());

        UnidadTrazable rechazada = DatosDePrueba.caja(lote, "S3");
        rechazada.salir();
        rechazada.quedarRechazadaEn(farmacia);
        assertEquals(EstadoVerificacion.BLOQUEADA, verificar(rechazada).getEstado());
    }

    @Test
    @DisplayName("ROBADA → ROBADA y SERIE_ROBADA (con anti-spam)")
    void robada() {
        UnidadTrazable caja = DatosDePrueba.caja(lote, "S1");
        caja.salir();
        caja.robar();

        assertEquals(EstadoVerificacion.ROBADA, verificar(caja).getEstado());
        verify(registroIntentos).registrarSiCorresponde(eq(TipoEvento.SERIE_ROBADA), eq(caja.getGtin()), eq("S1"),
                eq("UnidadTrazable"), eq(caja.getId()), anyMap());
    }

    @Test
    @DisplayName("En el laboratorio, en viaje o en depósito → EN_DISTRIBUCION")
    void enDistribucion() {
        assertEquals(EstadoVerificacion.EN_DISTRIBUCION, verificar(DatosDePrueba.caja(lote, "S1")).getEstado());
    }

    @Test
    @DisplayName("GTIN registrado pero serie inexistente → NO_EXISTE y SERIE_INEXISTENTE")
    void noExisteConGtinRegistrado() {
        String gtin = lote.getMedicamento().getGtin();
        when(unidadTrazableRepository.findByGtinAndSerie(gtin, "FALSA1")).thenReturn(Optional.empty());
        when(medicamentoRepository.findByGtin(gtin)).thenReturn(Optional.of(lote.getMedicamento()));

        VerificacionPublicaResponseDTO respuesta = service().verificar(gtin, "FALSA1");

        assertEquals(EstadoVerificacion.NO_EXISTE, respuesta.getEstado());
        verify(registroIntentos).registrarSiCorresponde(eq(TipoEvento.SERIE_INEXISTENTE), eq(gtin), eq("FALSA1"),
                eq("Medicamento"), eq(lote.getMedicamento().getId()), anyMap());
    }

    @Test
    @DisplayName("GTIN que no es de un medicamento registrado → NO_EXISTE sin evento")
    void noExisteSinGtinRegistrado() {
        when(unidadTrazableRepository.findByGtinAndSerie(anyString(), anyString())).thenReturn(Optional.empty());
        when(medicamentoRepository.findByGtin(anyString())).thenReturn(Optional.empty());

        assertEquals(EstadoVerificacion.NO_EXISTE, service().verificar("07791234567898", "X1").getEstado());
        verify(registroIntentos, never()).registrarSiCorresponde(any(), any(), any(), any(), any(), anyMap());
    }

    @Test
    @DisplayName("Choque simultáneo en la restricción única del intento → se ignora, la verificación responde igual")
    void carreraEnElIntento() {
        String gtin = lote.getMedicamento().getGtin();
        when(unidadTrazableRepository.findByGtinAndSerie(gtin, "FALSA1")).thenReturn(Optional.empty());
        when(medicamentoRepository.findByGtin(gtin)).thenReturn(Optional.of(lote.getMedicamento()));
        when(registroIntentos.registrarSiCorresponde(any(), any(), any(), any(), any(), anyMap()))
                .thenThrow(new DataIntegrityViolationException("duplicado"));

        assertEquals(EstadoVerificacion.NO_EXISTE, service().verificar(gtin, "FALSA1").getEstado());
    }

    @Test
    @DisplayName("R13 OBLIGATORIO: la respuesta pública no tiene ningún campo de paciente ni de dispensación")
    void r13SinDatosDePacienteNiDispensacion() {
        Set<String> campos = Arrays.stream(VerificacionPublicaResponseDTO.class.getDeclaredFields())
                .map(Field::getName).map(String::toLowerCase).collect(Collectors.toSet());
        Set<String> camposEtapa = Arrays.stream(EtapaRecorridoDTO.class.getDeclaredFields())
                .map(Field::getName).map(String::toLowerCase).collect(Collectors.toSet());
        for (String prohibido : new String[]{"dni", "receta", "obrasocial", "afiliado", "paciente", "dispensacion",
                "farmaceutico", "fechadispensacion"}) {
            assertFalse(campos.stream().anyMatch(c -> c.contains(prohibido)), "campo prohibido: " + prohibido);
            assertFalse(camposEtapa.stream().anyMatch(c -> c.contains(prohibido)), "campo prohibido: " + prohibido);
        }
        // Y el recorrido de una caja dispensada termina en la recepción en farmacia: no hay etapa de dispensación.
        UnidadTrazable caja = DatosDePrueba.cajaEnStock(lote, "S1", farmacia);
        caja.dispensar();
        assertTrue(verificar(caja).getRecorrido().stream().noneMatch(e -> e.getEtapa().contains("DISPENS")));
    }

    @Test
    @DisplayName("Recorrido: fabricación, liberación, salida y recepción, en orden")
    void recorrido() {
        Empresa laboratorio = lote.getLaboratorio();
        Empresa distribuidora = DatosDePrueba.empresaHabilitada(TipoEmpresa.DISTRIBUIDOR);
        Bulto bulto = DatosDePrueba.bulto("BUL-0001", lote,
                DatosDePrueba.circuitoAprobado(laboratorio, distribuidora, farmacia));
        UnidadTrazable caja = DatosDePrueba.caja(lote, "S1");
        caja.asignarABulto(bulto);
        DespachoLogistico viaje = DatosDePrueba.viaje(TramoDespacho.LAB_A_DISTRIBUIDOR, laboratorio);
        viaje.agregarBulto(bulto);
        viaje.registrarSalida();
        when(despachoLogisticoRepository.findSalidasDelBulto(bulto.getId())).thenReturn(List.of(viaje));

        List<EtapaRecorridoDTO> etapas = verificar(caja).getRecorrido();

        assertEquals(List.of("FABRICADO", "LIBERADO", "DESPACHADO"),
                etapas.stream().map(EtapaRecorridoDTO::getEtapa).toList());
    }

    @Test
    @DisplayName("Lote en RECALL (aunque la caja ya se haya dispensado) → BLOQUEADA con mensaje de retiro del mercado")
    void recall() {
        UnidadTrazable caja = DatosDePrueba.cajaEnStock(lote, "S1", farmacia);
        caja.dispensar();
        when(evaluadorBloqueo.esRetiroDelMercado(caja)).thenReturn(true);

        VerificacionPublicaResponseDTO respuesta = verificar(caja);

        assertEquals(EstadoVerificacion.BLOQUEADA, respuesta.getEstado());
        assertTrue(respuesta.getMensaje().contains("Retiro del mercado"));
    }

    // ---------- Anclaje (R15) ----------

    @Test
    @DisplayName("Anclaje: el primero que cubre el último hito del recorrido → CONFIRMADO con transacción, bloque y enlace")
    void anclajeQueCubreElRecorrido() {
        propiedadesAnclaje.setHabilitado(true);
        RegistroBlockchain anclaje = DatosDePrueba.anclajeConfirmado(1, 45, "cd".repeat(32), "0x" + "1".repeat(40));
        when(eventoRepository.ultimoNumeroDe(anyCollection(), anyCollection())).thenReturn(40L);
        when(registroBlockchainRepository.findFirstByHastaNumeroGreaterThanEqualAndEstadoInOrderByHastaNumeroAsc(
                eq(40L), eq(List.of(EstadoAnclaje.ENVIADO, EstadoAnclaje.CONFIRMADO)))).thenReturn(Optional.of(anclaje));

        AnclajeDTO dto = verificar(DatosDePrueba.cajaEnStock(lote, "S1", farmacia)).getAnclaje();

        assertEquals("CONFIRMADO", dto.getEstado());
        assertEquals("sepolia", dto.getRed());
        assertEquals(anclaje.getTransactionHash(), dto.getTransactionHash());
        assertEquals(9_000_000L, dto.getBloque());
        assertEquals("https://sepolia.etherscan.io/tx/" + anclaje.getTransactionHash(), dto.getEnlace());
    }

    @Test
    @DisplayName("Anclaje: se buscan solo los hitos del recorrido (lote, bulto, salidas), nunca la dispensación (R13)")
    @SuppressWarnings("unchecked")
    void anclajeSinDispensacion() {
        propiedadesAnclaje.setHabilitado(true);
        Empresa laboratorio = lote.getLaboratorio();
        Bulto bulto = DatosDePrueba.bulto("BUL-0001", lote, DatosDePrueba.circuitoAprobado(laboratorio,
                DatosDePrueba.empresaHabilitada(TipoEmpresa.DISTRIBUIDOR), farmacia));
        UnidadTrazable caja = DatosDePrueba.caja(lote, "S1");
        caja.asignarABulto(bulto);
        DespachoLogistico viaje = DatosDePrueba.viaje(TramoDespacho.LAB_A_DISTRIBUIDOR, laboratorio);
        viaje.agregarBulto(bulto);
        viaje.registrarSalida();
        when(despachoLogisticoRepository.findSalidasDelBulto(bulto.getId())).thenReturn(List.of(viaje));
        caja.salir();
        caja.recibirEnFarmacia(farmacia);
        caja.dispensar();
        ArgumentCaptor<Collection<UUID>> entidades = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<Collection<TipoEvento>> tipos = ArgumentCaptor.forClass(Collection.class);
        when(eventoRepository.ultimoNumeroDe(entidades.capture(), tipos.capture())).thenReturn(null);

        verificar(caja);

        assertEquals(Set.of(lote.getId(), bulto.getId(), viaje.getId()), Set.copyOf(entidades.getValue()));
        assertFalse(entidades.getValue().contains(caja.getId()), "la caja (dispensación, devolución) no cuenta");
        assertFalse(tipos.getValue().contains(TipoEvento.DISPENSACION));
        assertFalse(tipos.getValue().contains(TipoEvento.ANULACION_DISPENSA));
        assertTrue(tipos.getValue().containsAll(List.of(TipoEvento.LOTE_REGISTRADO, TipoEvento.BULTO_ARMADO,
                TipoEvento.VIAJE_SALIDA, TipoEvento.BULTO_RECIBIDO)));
    }

    @Test
    @DisplayName("Anclaje habilitado pero el recorrido todavía no está anclado → PENDIENTE, sin transacción")
    void anclajePendiente() {
        propiedadesAnclaje.setHabilitado(true);
        when(eventoRepository.ultimoNumeroDe(anyCollection(), anyCollection())).thenReturn(50L);
        when(registroBlockchainRepository.findFirstByHastaNumeroGreaterThanEqualAndEstadoInOrderByHastaNumeroAsc(
                anyLong(), anyCollection())).thenReturn(Optional.empty());

        AnclajeDTO dto = verificar(DatosDePrueba.cajaEnStock(lote, "S1", farmacia)).getAnclaje();

        assertEquals("PENDIENTE", dto.getEstado());
        assertEquals(null, dto.getTransactionHash());
    }
}
