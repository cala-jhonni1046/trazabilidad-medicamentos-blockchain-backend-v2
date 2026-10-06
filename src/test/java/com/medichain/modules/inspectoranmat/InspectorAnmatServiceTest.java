package com.medichain.modules.inspectoranmat;

import com.medichain.exceptions.ReglaNegocioException;
import com.medichain.modules.auth.UsuarioAutenticado;
import com.medichain.modules.empresa.Empresa;
import com.medichain.modules.empresa.EmpresaRepository;
import com.medichain.modules.empresa.EstadoHabilitacion;
import com.medichain.modules.empresa.TipoEmpresa;
import com.medichain.modules.trazabilidad.RegistradorEventos;
import com.medichain.modules.trazabilidad.TipoEvento;
import com.medichain.modules.usuario.RolUsuario;
import com.medichain.modules.usuario.UsuarioRepository;
import com.medichain.testutil.DatosDePrueba;
import com.medichain.utils.enums.Provincia;
import com.medichain.utils.seguridad.UsuarioActual;
import com.medichain.utils.seguridad.VerificadorUsuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test unitario InspectorAnmatServiceTest en MediChain.
 * Baja y reactivación (R1): cuenta inactiva, solicitudes liberadas y
 * datos del evento; transición inválida → 409.
 */
@ExtendWith(MockitoExtension.class)
class InspectorAnmatServiceTest {

    @Mock
    private InspectorAnmatRepository repository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private EmpresaRepository empresaRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UsuarioActual usuarioActual;

    @Mock
    private VerificadorUsuario verificadorUsuario;

    @Mock
    private RegistradorEventos registradorEventos;

    /** Construye el Service bajo prueba con los mocks, autenticado como la Sede. */
    private InspectorAnmatService service() {
        lenient().when(usuarioActual.obtener()).thenReturn(DatosDePrueba.autenticado(RolUsuario.SEDE_CENTRAL, null));
        lenient().when(repository.save(any(InspectorAnmat.class))).thenAnswer(inv -> inv.getArgument(0));
        return new InspectorAnmatService(repository, usuarioRepository, passwordEncoder, usuarioActual,
                verificadorUsuario, registradorEventos, empresaRepository);
    }

    @Test
    @DisplayName("Baja: inspector BAJA, cuenta inactiva, sus solicitudes vuelven a la bandeja y el evento las lista")
    @SuppressWarnings("unchecked")
    void bajaLiberaSolicitudes() {
        InspectorAnmat inspector = DatosDePrueba.inspector(Provincia.CORDOBA);
        when(repository.findById(inspector.getId())).thenReturn(Optional.of(inspector));
        Empresa tomada = DatosDePrueba.empresa(TipoEmpresa.FARMACIA);
        tomada.tomar(inspector);
        when(empresaRepository.findByEstadoAndInspectorRevisorId(EstadoHabilitacion.PENDIENTE, inspector.getId()))
                .thenReturn(List.of(tomada));
        InspectorAnmatService service = service();

        service.darDeBaja(inspector.getId());

        assertEquals(EstadoInspector.BAJA, inspector.getEstado());
        assertFalse(inspector.getUsuario().getActivo());
        assertNull(tomada.getInspectorRevisor());
        ArgumentCaptor<Map<String, Object>> datos = ArgumentCaptor.forClass(Map.class);
        verify(registradorEventos).registrar(eq(TipoEvento.BAJA_INSPECTOR), eq("InspectorAnmat"),
                eq(inspector.getId()), datos.capture(), any(UsuarioAutenticado.class));
        Map<String, Object> liberadas = (Map<String, Object>) datos.getValue().get("solicitudesLiberadas");
        assertEquals(1, liberadas.get("cantidad"));
        assertEquals(List.of(tomada.getId()), liberadas.get("empresaIds"));
    }

    @Test
    @DisplayName("Baja de un inspector ya dado de baja → 409 TRANSICION_INVALIDA")
    void bajaDobleDevuelve409() {
        InspectorAnmat inspector = DatosDePrueba.inspector(Provincia.CORDOBA);
        inspector.darDeBaja();
        when(repository.findById(inspector.getId())).thenReturn(Optional.of(inspector));
        InspectorAnmatService service = service();

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class, () -> service.darDeBaja(inspector.getId()));
        assertEquals("TRANSICION_INVALIDA", ex.getCodigoRegla());
    }

    @Test
    @DisplayName("Reactivar: ACTIVO y cuenta activa de nuevo; evento REACTIVACION_INSPECTOR")
    void reactivarCasoFeliz() {
        InspectorAnmat inspector = DatosDePrueba.inspector(Provincia.CORDOBA);
        inspector.darDeBaja();
        inspector.getUsuario().setActivo(false);
        when(repository.findById(inspector.getId())).thenReturn(Optional.of(inspector));
        InspectorAnmatService service = service();

        service.reactivar(inspector.getId());

        assertEquals(EstadoInspector.ACTIVO, inspector.getEstado());
        assertTrue(inspector.getUsuario().getActivo());
        assertNull(inspector.getFechaBaja());
        verify(registradorEventos).registrar(eq(TipoEvento.REACTIVACION_INSPECTOR), eq("InspectorAnmat"),
                eq(inspector.getId()), anyMap(), any(UsuarioAutenticado.class));
    }

    @Test
    @DisplayName("Reactivar un inspector ACTIVO → 409 TRANSICION_INVALIDA")
    void reactivarActivoDevuelve409() {
        InspectorAnmat inspector = DatosDePrueba.inspector(Provincia.CORDOBA);
        when(repository.findById(inspector.getId())).thenReturn(Optional.of(inspector));
        InspectorAnmatService service = service();

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class, () -> service.reactivar(inspector.getId()));
        assertEquals("TRANSICION_INVALIDA", ex.getCodigoRegla());
    }
}
