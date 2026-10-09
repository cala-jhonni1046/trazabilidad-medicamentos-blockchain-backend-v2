package com.medichain.modules.dispensacion;

import com.medichain.exceptions.ReglaNegocioException;
import com.medichain.exceptions.ResourceNotFoundException;
import com.medichain.modules.auth.UsuarioAutenticado;
import com.medichain.modules.cuarentena.Bloqueo;
import com.medichain.modules.cuarentena.CausaBloqueo;
import com.medichain.modules.cuarentena.EvaluadorBloqueo;
import com.medichain.modules.empresa.Empresa;
import com.medichain.modules.empresa.TipoEmpresa;
import com.medichain.modules.lote.Lote;
import com.medichain.modules.trazabilidad.HashUtil;
import com.medichain.modules.trazabilidad.JsonCanonico;
import com.medichain.modules.trazabilidad.RegistradorEventos;
import com.medichain.modules.trazabilidad.RegistradorEventosAparte;
import com.medichain.modules.trazabilidad.TipoEvento;
import com.medichain.modules.unidadtrazable.EstadoUnidad;
import com.medichain.modules.unidadtrazable.UnidadTrazable;
import com.medichain.modules.unidadtrazable.UnidadTrazableRepository;
import com.medichain.modules.usuario.RolUsuario;
import com.medichain.modules.usuario.Usuario;
import com.medichain.testutil.DatosDePrueba;
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
import org.springframework.test.util.ReflectionTestUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test unitario DispensacionServiceTest en MediChain (R10, R11, R13, R14).
 * Dispensación por GTIN + serie, anulación dentro de las 2 h, intentos
 * registrados aparte (INTENTO_DUPLICADO, SERIE_ROBADA) y R13: el DNI
 * completo no está en la entidad, ni en la respuesta, ni en el evento.
 */
@ExtendWith(MockitoExtension.class)
class DispensacionServiceTest {

    private static final String DNI = "30111006";
    private static final String RECETA = "REC-PRIVADA-777";
    private static final String OBRA_SOCIAL = "OSEP-PRIVADA";
    private static final String AFILIADO = "AF-PRIVADO-999";

    @Mock
    private DispensacionRepository repository;

    @Mock
    private UnidadTrazableRepository unidadTrazableRepository;

    @Mock
    private EvaluadorBloqueo evaluadorBloqueo;

    @Mock
    private UsuarioActual usuarioActual;

    @Mock
    private VerificadorEmpresa verificadorEmpresa;

    @Mock
    private VerificadorUsuario verificadorUsuario;

    @Mock
    private RegistradorEventos registradorEventos;

    @Mock
    private RegistradorEventosAparte registradorEventosAparte;

    private Empresa farmacia;
    private Lote lote;
    private UnidadTrazable caja;
    private UsuarioAutenticado actual;

    @BeforeEach
    void setUp() {
        farmacia = DatosDePrueba.empresaHabilitada(TipoEmpresa.FARMACIA);
        lote = DatosDePrueba.loteDe(DatosDePrueba.empresaHabilitada(TipoEmpresa.LABORATORIO));
        caja = DatosDePrueba.cajaEnStock(lote, "L20260001S000011", farmacia);
        actual = DatosDePrueba.autenticado(RolUsuario.FARMACIA, farmacia);
        lenient().when(usuarioActual.obtener()).thenReturn(actual);
        lenient().when(verificadorEmpresa.exigirHabilitada(farmacia.getId())).thenReturn(farmacia);
        lenient().when(verificadorUsuario.obtener(actual.getUsuarioId())).thenReturn(
                new Usuario("f@demo.com", "hash", "Ana", "Perez", "12345678", RolUsuario.FARMACIA));
        lenient().when(unidadTrazableRepository.findByGtinAndSerie(caja.getGtin(), caja.getSerie()))
                .thenReturn(Optional.of(caja));
        lenient().when(evaluadorBloqueo.bloqueoDeCaja(any())).thenReturn(Optional.empty());
        lenient().when(repository.save(any(Dispensacion.class))).thenAnswer(inv -> {
            Dispensacion d = inv.getArgument(0);
            if (d.getId() == null) {
                d.setId(UUID.randomUUID());
            }
            return d;
        });
    }

    /** Construye el Service bajo prueba con los mocks. */
    private DispensacionService service() {
        return new DispensacionService(repository, unidadTrazableRepository, evaluadorBloqueo, usuarioActual,
                verificadorEmpresa, verificadorUsuario, registradorEventos, registradorEventosAparte);
    }

    /** DTO con obra social, afiliado, receta y DNI. */
    private DispensacionRequestDTO dto() {
        DispensacionRequestDTO dto = new DispensacionRequestDTO();
        dto.setGtin(caja.getGtin());
        dto.setSerie(caja.getSerie());
        dto.setParticular(false);
        dto.setObraSocial(OBRA_SOCIAL);
        dto.setNumeroAfiliado(AFILIADO);
        dto.setNumeroReceta(RECETA);
        dto.setDni(DNI);
        return dto;
    }

    /** Verifica que la acción falle con el código dado. */
    private void fallaCon(String codigo, Runnable accion) {
        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class, accion::run);
        assertEquals(codigo, ex.getCodigoRegla());
    }

    /** Valores de todos los campos del objeto (y de su superclase), como texto. */
    private String valoresDeCampos(Object objeto) throws IllegalAccessException {
        StringBuilder texto = new StringBuilder();
        for (Class<?> clase = objeto.getClass(); clase != null; clase = clase.getSuperclass()) {
            for (Field campo : clase.getDeclaredFields()) {
                campo.setAccessible(true);
                texto.append(campo.getName()).append('=').append(campo.get(objeto)).append(';');
            }
        }
        return texto.toString();
    }

    // ---------- Dispensar ----------

    @Test
    @DisplayName("Dispensar: caja DISPENSADA y evento DISPENSACION")
    void dispensar() {
        Dispensacion dispensacion = service().dispensar(dto());

        assertEquals(EstadoUnidad.DISPENSADA, caja.getEstado());
        assertEquals("*****006", dispensacion.getDniEnmascarado());
        verify(registradorEventos).registrar(eq(TipoEvento.DISPENSACION), eq("Dispensacion"), eq(dispensacion.getId()),
                anyMap(), eq(actual));
    }

    @Test
    @DisplayName("R13 OBLIGATORIO: el DNI completo NO está en la entidad ni en la respuesta")
    void r13DniNoSePersisteNiSeDevuelve() throws Exception {
        Dispensacion dispensacion = service().dispensar(dto());

        assertFalse(valoresDeCampos(dispensacion).contains(DNI), "la entidad no guarda el DNI completo");
        DispensacionResponseDTO respuesta = new DispensacionMapper().toResponseDTO(dispensacion);
        StringBuilder getters = new StringBuilder();
        for (Method metodo : DispensacionResponseDTO.class.getMethods()) {
            if (metodo.getName().startsWith("get") && metodo.getParameterCount() == 0) {
                getters.append(metodo.invoke(respuesta)).append(';');
            }
        }
        assertFalse(getters.toString().contains(DNI), "la respuesta no devuelve el DNI completo");
        assertEquals("*****006", respuesta.getDniEnmascarado());
    }

    @Test
    @DisplayName("R13 OBLIGATORIO: el evento DISPENSACION no lleva DNI, receta, obra social ni afiliado")
    @SuppressWarnings("unchecked")
    void r13EventoSinDatosDelPaciente() {
        service().dispensar(dto());

        ArgumentCaptor<Map<String, Object>> datos = ArgumentCaptor.forClass(Map.class);
        verify(registradorEventos).registrar(eq(TipoEvento.DISPENSACION), any(), any(), datos.capture(), eq(actual));
        assertEquals(java.util.Set.of("unidadId", "gtin", "serie", "farmaciaId"), datos.getValue().keySet());
        String json = JsonCanonico.escribir(datos.getValue());
        for (String privado : new String[]{DNI, RECETA, OBRA_SOCIAL, AFILIADO}) {
            assertFalse(json.contains(privado), "el evento no debe contener " + privado);
        }
    }

    @Test
    @DisplayName("Particular: sin obra social ni afiliado; DNI opcional ausente → null")
    void particular() {
        DispensacionRequestDTO dto = dto();
        dto.setParticular(true);
        dto.setObraSocial(null);
        dto.setNumeroAfiliado(null);
        dto.setDni(null);

        Dispensacion dispensacion = service().dispensar(dto);

        assertNull(dispensacion.getObraSocial());
        assertNull(dispensacion.getDniEnmascarado());
    }

    @Test
    @DisplayName("Caja que no está en mi farmacia → 404; caja inexistente → 404")
    void cajaAjenaOInexistente() {
        UnidadTrazable ajena = DatosDePrueba.cajaEnStock(lote, "OTRA1", DatosDePrueba.empresaHabilitada(TipoEmpresa.FARMACIA));
        when(unidadTrazableRepository.findByGtinAndSerie(ajena.getGtin(), "OTRA1")).thenReturn(Optional.of(ajena));
        DispensacionRequestDTO dto = dto();
        dto.setSerie("OTRA1");
        assertThrows(ResourceNotFoundException.class, () -> service().dispensar(dto));

        dto.setSerie("NOEXISTE");
        assertThrows(ResourceNotFoundException.class, () -> service().dispensar(dto));
    }

    @Test
    @DisplayName("R10: caja bloqueada → 409 R10")
    void cajaBloqueada() {
        when(evaluadorBloqueo.bloqueoDeCaja(caja)).thenReturn(Optional.of(new Bloqueo(CausaBloqueo.LOTE_EN_CUARENTENA, "el lote está en cuarentena")));

        fallaCon("R10", () -> service().dispensar(dto()));
        assertEquals(EstadoUnidad.EN_STOCK, caja.getEstado());
    }

    @Test
    @DisplayName("R11: caja ya dispensada → 409 R11 e INTENTO_DUPLICADO en transacción aparte")
    void yaDispensada() {
        caja.dispensar();

        fallaCon("R11", () -> service().dispensar(dto()));
        verify(registradorEventosAparte).registrar(eq(TipoEvento.INTENTO_DUPLICADO), eq("UnidadTrazable"),
                eq(caja.getId()), anyMap(), any(), eq(farmacia.getId()));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("R14: caja robada → 409 R14 y SERIE_ROBADA en transacción aparte")
    void robada() {
        UnidadTrazable robada = DatosDePrueba.caja(lote, "ROBADA1");
        robada.salir();
        robada.robar();
        when(unidadTrazableRepository.findByGtinAndSerie(robada.getGtin(), "ROBADA1")).thenReturn(Optional.of(robada));
        DispensacionRequestDTO dto = dto();
        dto.setSerie("ROBADA1");

        fallaCon("R14", () -> service().dispensar(dto));
        verify(registradorEventosAparte).registrar(eq(TipoEvento.SERIE_ROBADA), eq("UnidadTrazable"),
                eq(robada.getId()), anyMap(), any(), eq(farmacia.getId()));
    }

    @Test
    @DisplayName("Caja DEVUELTA en mi farmacia → TRANSICION_INVALIDA (no vuelve a dispensarse)")
    void devuelta() {
        caja.devolver();

        fallaCon("TRANSICION_INVALIDA", () -> service().dispensar(dto()));
    }

    // ---------- Anular ----------

    /** Dispensación vigente de la caja, registrada en el mock. */
    private Dispensacion dispensada() {
        Dispensacion dispensacion = service().dispensar(dto());
        when(repository.findById(dispensacion.getId())).thenReturn(Optional.of(dispensacion));
        return dispensacion;
    }

    @Test
    @DisplayName("Anular dentro de las 2 h: caja EN_STOCK y ANULACION_DISPENSA con el hash del motivo")
    @SuppressWarnings("unchecked")
    void anular() {
        Dispensacion dispensacion = dispensada();

        service().anular(dispensacion.getId(), "Error de carga");

        assertEquals(EstadoUnidad.EN_STOCK, caja.getEstado());
        ArgumentCaptor<Map<String, Object>> datos = ArgumentCaptor.forClass(Map.class);
        verify(registradorEventos).registrar(eq(TipoEvento.ANULACION_DISPENSA), eq("Dispensacion"),
                eq(dispensacion.getId()), datos.capture(), eq(actual));
        assertEquals(HashUtil.sha256Hex("Error de carga"), datos.getValue().get("motivoHash"));
    }

    @Test
    @DisplayName("R11: anular pasadas las 2 h → 409 R11")
    void anularFueraDePlazo() {
        Dispensacion dispensacion = dispensada();
        ReflectionTestUtils.setField(dispensacion, "fechaHora", Instant.now().minus(java.time.Duration.ofHours(3)));

        fallaCon("R11", () -> service().anular(dispensacion.getId(), "tarde"));
        assertEquals(EstadoUnidad.DISPENSADA, caja.getEstado());
    }

    @Test
    @DisplayName("Anular dos veces → TRANSICION_INVALIDA; otra farmacia → 404")
    void anularInvalido() {
        Dispensacion dispensacion = dispensada();
        DispensacionService service = service();
        service.anular(dispensacion.getId(), "x");
        fallaCon("TRANSICION_INVALIDA", () -> service.anular(dispensacion.getId(), "x"));

        when(usuarioActual.obtener()).thenReturn(DatosDePrueba.autenticado(RolUsuario.FARMACIA,
                DatosDePrueba.empresaHabilitada(TipoEmpresa.FARMACIA)));
        assertThrows(ResourceNotFoundException.class, () -> service.anular(dispensacion.getId(), "x"));
    }

    @Test
    @DisplayName("No ve lo ajeno: una dispensación de otra farmacia responde 404")
    void noVeDispensacionAjena() {
        Dispensacion dispensacion = dispensada();
        when(usuarioActual.obtener()).thenReturn(DatosDePrueba.autenticado(RolUsuario.FARMACIA,
                DatosDePrueba.empresaHabilitada(TipoEmpresa.FARMACIA)));

        assertThrows(ResourceNotFoundException.class, () -> service().getById(dispensacion.getId()));
    }
}
