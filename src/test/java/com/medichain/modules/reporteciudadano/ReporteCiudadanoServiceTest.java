package com.medichain.modules.reporteciudadano;

import com.medichain.exceptions.ReglaNegocioException;
import com.medichain.exceptions.ResourceNotFoundException;
import com.medichain.modules.auth.UsuarioAutenticado;
import com.medichain.modules.cuarentena.CuarentenaRepository;
import com.medichain.modules.empresa.TipoEmpresa;
import com.medichain.modules.inspectoranmat.InspectorAnmat;
import com.medichain.modules.inspectoranmat.InspectorAnmatRepository;
import com.medichain.modules.lote.Lote;
import com.medichain.modules.trazabilidad.HashUtil;
import com.medichain.modules.trazabilidad.JsonCanonico;
import com.medichain.modules.trazabilidad.RegistradorEventos;
import com.medichain.modules.trazabilidad.TipoEvento;
import com.medichain.modules.unidadtrazable.UnidadTrazable;
import com.medichain.modules.unidadtrazable.UnidadTrazableRepository;
import com.medichain.modules.usuario.RolUsuario;
import com.medichain.modules.usuario.Usuario;
import com.medichain.testutil.DatosDePrueba;
import com.medichain.utils.enums.Provincia;
import com.medichain.utils.seguridad.UsuarioActual;
import com.medichain.utils.seguridad.VerificadorUsuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test unitario ReporteCiudadanoServiceTest en MediChain (R12, R13).
 * El paciente reporta (con o sin caja existente) y ve solo lo suyo; el
 * inspector de la provincia toma y cierra; el evento no lleva paciente ni
 * descripción; el paciente no ve la conclusión.
 */
@ExtendWith(MockitoExtension.class)
class ReporteCiudadanoServiceTest {

    private static final String DESCRIPCION = "Me llamo Juan Pérez y vivo en Calle Falsa 123";

    @Mock
    private ReporteCiudadanoRepository repository;

    @Mock
    private UnidadTrazableRepository unidadTrazableRepository;

    @Mock
    private CuarentenaRepository cuarentenaRepository;

    @Mock
    private InspectorAnmatRepository inspectorAnmatRepository;

    @Mock
    private UsuarioActual usuarioActual;

    @Mock
    private VerificadorUsuario verificadorUsuario;

    @Mock
    private RegistradorEventos registradorEventos;

    private Usuario paciente;
    private UsuarioAutenticado comoPaciente;
    private Lote lote;

    @BeforeEach
    void setUp() {
        paciente = new Usuario("p@demo.com", "hash", "Juan", "Pérez", "30111006", RolUsuario.PACIENTE);
        paciente.setId(UUID.randomUUID());
        comoPaciente = new UsuarioAutenticado(paciente.getId(), "p@demo.com", RolUsuario.PACIENTE, null, null);
        lote = DatosDePrueba.loteDe(DatosDePrueba.empresaHabilitada(TipoEmpresa.LABORATORIO));
        lenient().when(verificadorUsuario.obtener(paciente.getId())).thenReturn(paciente);
        lenient().when(repository.siguienteNumeroCodigo()).thenReturn(1L);
        lenient().when(repository.save(any(ReporteCiudadano.class))).thenAnswer(inv -> {
            ReporteCiudadano r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(UUID.randomUUID());
            }
            return r;
        });
        lenient().when(cuarentenaRepository.findByReporteOrigenId(any())).thenReturn(List.of());
    }

    /** Construye el Service bajo prueba con los mocks. */
    private ReporteCiudadanoService service() {
        return new ReporteCiudadanoService(repository, unidadTrazableRepository, cuarentenaRepository,
                inspectorAnmatRepository, usuarioActual, verificadorUsuario, registradorEventos);
    }

    /** DTO de reporte. */
    private ReporteCiudadanoRequestDTO dto(String serie) {
        ReporteCiudadanoRequestDTO dto = new ReporteCiudadanoRequestDTO();
        dto.setGtin(lote.getMedicamento().getGtin());
        dto.setSerie(serie);
        dto.setMotivo(MotivoReporte.SOSPECHA_FALSIFICACION);
        dto.setProvincia(Provincia.CORDOBA);
        dto.setDescripcion(DESCRIPCION);
        return dto;
    }

    /** Reporte ABIERTO de Córdoba registrado en el mock. */
    private ReporteCiudadano reporte() {
        ReporteCiudadano reporte = new ReporteCiudadano("REP-0001", paciente, lote.getMedicamento().getGtin(), "FALSA1",
                MotivoReporte.SOSPECHA_FALSIFICACION, Provincia.CORDOBA, DESCRIPCION, null);
        reporte.setId(UUID.randomUUID());
        lenient().when(repository.findById(reporte.getId())).thenReturn(Optional.of(reporte));
        return reporte;
    }

    /** Autentica como el inspector dado. */
    private UsuarioAutenticado comoInspector(InspectorAnmat inspector) {
        UsuarioAutenticado actual = DatosDePrueba.autenticadoInspector(inspector);
        when(usuarioActual.obtener()).thenReturn(actual);
        when(inspectorAnmatRepository.findByUsuarioId(actual.getUsuarioId())).thenReturn(Optional.of(inspector));
        return actual;
    }

    // ---------- Reportar ----------

    @Test
    @DisplayName("Reportar una serie inexistente: REP-0001 ABIERTO, sin caja; R13: el evento no lleva paciente ni descripción")
    @SuppressWarnings("unchecked")
    void reportarSinCaja() {
        when(usuarioActual.obtener()).thenReturn(comoPaciente);
        when(unidadTrazableRepository.findByGtinAndSerie(anyString(), eq("FALSA1"))).thenReturn(Optional.empty());

        ReporteCiudadano reporte = service().reportar(dto("FALSA1"));

        assertEquals("REP-0001", reporte.getCodigo());
        assertEquals(EstadoAuditoria.ABIERTO, reporte.getEstado());
        assertNull(reporte.getUnidadTrazable());
        ArgumentCaptor<Map<String, Object>> datos = ArgumentCaptor.forClass(Map.class);
        verify(registradorEventos).registrar(eq(TipoEvento.REPORTE_CIUDADANO), eq("ReporteCiudadano"),
                eq(reporte.getId()), datos.capture(), eq(comoPaciente));
        String json = JsonCanonico.escribir(datos.getValue());
        assertFalse(json.contains("Juan"), "el evento no lleva datos de la descripción");
        assertFalse(json.contains(HashUtil.sha256Hex(DESCRIPCION)), "ni siquiera el hash de la descripción");
        assertFalse(json.contains(paciente.getId().toString()), "el evento no lleva el paciente");
        assertEquals(false, datos.getValue().get("cajaExiste"));
    }

    @Test
    @DisplayName("Reportar una caja existente: queda vinculada")
    void reportarConCaja() {
        when(usuarioActual.obtener()).thenReturn(comoPaciente);
        UnidadTrazable caja = DatosDePrueba.caja(lote, "S1");
        when(unidadTrazableRepository.findByGtinAndSerie(caja.getGtin(), "S1")).thenReturn(Optional.of(caja));

        assertSame(caja, service().reportar(dto("S1")).getUnidadTrazable());
    }

    // ---------- Visibilidad ----------

    @Test
    @DisplayName("El paciente ve su reporte; un reporte ajeno → 404")
    void pacienteSoloVeLoSuyo() {
        ReporteCiudadano reporte = reporte();
        when(usuarioActual.obtener()).thenReturn(comoPaciente);
        assertSame(reporte, service().getById(reporte.getId()));

        when(usuarioActual.obtener()).thenReturn(new UsuarioAutenticado(UUID.randomUUID(), "otro@demo.com",
                RolUsuario.PACIENTE, null, null));
        assertThrows(ResourceNotFoundException.class, () -> service().getById(reporte.getId()));
    }

    @Test
    @DisplayName("El paciente no ve la conclusión; el inspector sí")
    void pacienteNoVeLaConclusion() {
        ReporteCiudadano reporte = reporte();
        InspectorAnmat inspector = DatosDePrueba.inspector(Provincia.CORDOBA);
        reporte.tomar(inspector);
        reporte.cerrar(inspector, "Conclusión interna");
        ReporteCiudadanoMapper mapper = new ReporteCiudadanoMapper();

        assertNull(mapper.toResponseDTO(reporte, RolUsuario.PACIENTE).getConclusion());
        assertNull(mapper.toResponseDTO(reporte, RolUsuario.PACIENTE).getInvestigaId());
        assertEquals("Conclusión interna", mapper.toResponseDTO(reporte, RolUsuario.INSPECTOR).getConclusion());
    }

    // ---------- Inspector ----------

    @Test
    @DisplayName("Tomar (REPORTE_TOMADO) y cerrar (REPORTE_CERRADO con el hash de la conclusión)")
    @SuppressWarnings("unchecked")
    void tomarYCerrar() {
        ReporteCiudadano reporte = reporte();
        InspectorAnmat inspector = DatosDePrueba.inspector(Provincia.CORDOBA);
        UsuarioAutenticado actual = comoInspector(inspector);
        ReporteCiudadanoService service = service();

        service.tomar(reporte.getId());
        assertEquals(EstadoAuditoria.EN_INVESTIGACION, reporte.getEstado());
        verify(registradorEventos).registrar(eq(TipoEvento.REPORTE_TOMADO), eq("ReporteCiudadano"), eq(reporte.getId()),
                anyMap(), eq(actual));

        service.cerrar(reporte.getId(), "Serie inexistente: se informa a la fiscalía");
        assertEquals(EstadoAuditoria.CERRADO, reporte.getEstado());
        assertTrue(reporte.getFechaCierre() != null);
        ArgumentCaptor<Map<String, Object>> datos = ArgumentCaptor.forClass(Map.class);
        verify(registradorEventos).registrar(eq(TipoEvento.REPORTE_CERRADO), eq("ReporteCiudadano"), eq(reporte.getId()),
                datos.capture(), eq(actual));
        assertEquals(HashUtil.sha256Hex("Serie inexistente: se informa a la fiscalía"), datos.getValue().get("conclusionHash"));
        assertFalse(JsonCanonico.escribir(datos.getValue()).contains("fiscalía"));
    }

    @Test
    @DisplayName("Cerrar sin tomar → TRANSICION_INVALIDA; inspector de otra provincia → 404")
    void cerrarSinTomarYOtraProvincia() {
        ReporteCiudadano reporte = reporte();
        comoInspector(DatosDePrueba.inspector(Provincia.CORDOBA));
        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class, () -> service().cerrar(reporte.getId(), "x"));
        assertEquals("TRANSICION_INVALIDA", ex.getCodigoRegla());

        comoInspector(DatosDePrueba.inspector(Provincia.MENDOZA));
        assertThrows(ResourceNotFoundException.class, () -> service().tomar(reporte.getId()));
    }
}
