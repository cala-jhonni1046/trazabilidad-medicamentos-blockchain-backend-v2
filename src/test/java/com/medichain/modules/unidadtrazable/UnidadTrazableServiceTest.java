package com.medichain.modules.unidadtrazable;

import com.medichain.exceptions.ResourceNotFoundException;
import com.medichain.modules.empresa.Empresa;
import com.medichain.modules.empresa.TipoEmpresa;
import com.medichain.modules.usuario.RolUsuario;
import com.medichain.testutil.DatosDePrueba;
import com.medichain.utils.seguridad.UsuarioActual;
import com.medichain.utils.seguridad.VerificadorEmpresa;
import com.medichain.modules.trazabilidad.RegistradorEventos;
import com.medichain.exceptions.ReglaNegocioException;
import com.medichain.modules.trazabilidad.TipoEvento;
import com.medichain.modules.trazabilidad.HashUtil;
import com.medichain.modules.auth.UsuarioAutenticado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Test unitario UnidadTrazableServiceTest en MediChain.
 * Prueba con Mockito el filtrado de datos por rol de UnidadTrazableService: el usuario
 * ve lo suyo y lo ajeno responde 404 (no 403), para no revelar que existe.
 */
@ExtendWith(MockitoExtension.class)
class UnidadTrazableServiceTest {

    @Mock
    private UnidadTrazableRepository repository;

    @Mock
    private UsuarioActual usuarioActual;

    @Mock
    private VerificadorEmpresa verificadorEmpresa;

    @Mock
    private RegistradorEventos registradorEventos;

    private final Pageable pagina = PageRequest.of(0, 20);

    /** Construye el Service bajo prueba con los mocks. */
    private UnidadTrazableService service() {
        return new UnidadTrazableService(repository, usuarioActual, verificadorEmpresa, registradorEventos);
    }

    /** Unidad del lote de un laboratorio, ubicada en la empresa dada. */
    private UnidadTrazable unidadEn(Empresa ubicacion) {
        return DatosDePrueba.cajaEnStock(
                DatosDePrueba.loteDe(DatosDePrueba.empresaHabilitada(TipoEmpresa.LABORATORIO)), "L20260001S000123", ubicacion);
    }

    @Test
    @DisplayName("Ve lo suyo: la farmacia ve una unidad que está en su empresa")
    void farmaciaVeUnidadEnSuEmpresa() {
        Empresa farmacia = DatosDePrueba.empresaHabilitada(TipoEmpresa.FARMACIA);
        UnidadTrazable unidad = unidadEn(farmacia);
        when(usuarioActual.obtener()).thenReturn(DatosDePrueba.autenticado(RolUsuario.FARMACIA, farmacia));
        when(repository.findById(unidad.getId())).thenReturn(Optional.of(unidad));

        when(repository.esVisibleParaFarmacia(unidad.getId(), farmacia.getId())).thenReturn(true);

        assertSame(unidad, service().getById(unidad.getId()));
    }

    @Test
    @DisplayName("No ve lo ajeno: una unidad que está en otra farmacia responde 404")
    void farmaciaNoVeUnidadAjena() {
        Empresa farmacia = DatosDePrueba.empresaHabilitada(TipoEmpresa.FARMACIA);
        UnidadTrazable unidad = unidadEn(DatosDePrueba.empresaHabilitada(TipoEmpresa.FARMACIA));
        when(usuarioActual.obtener()).thenReturn(DatosDePrueba.autenticado(RolUsuario.FARMACIA, farmacia));
        when(repository.findById(unidad.getId())).thenReturn(Optional.of(unidad));

        assertThrows(ResourceNotFoundException.class, () -> service().getById(unidad.getId()));
    }

    // ---------- Devolución (R14) ----------

    /** DTO de devolución de la caja dada. */
    private DevolucionRequestDTO devolucion(UnidadTrazable caja) {
        DevolucionRequestDTO dto = new DevolucionRequestDTO();
        dto.setGtin(caja.getGtin());
        dto.setSerie(caja.getSerie());
        dto.setMotivo(MotivoDevolucion.DANADA);
        dto.setObservacion("Caja aplastada");
        return dto;
    }

    @Test
    @DisplayName("Devolución: caja EN_STOCK de mi farmacia → DEVUELTA, evento DEVOLUCION con motivo y hash")
    @SuppressWarnings("unchecked")
    void devolver() {
        Empresa farmacia = DatosDePrueba.empresaHabilitada(TipoEmpresa.FARMACIA);
        UnidadTrazable caja = unidadEn(farmacia);
        when(usuarioActual.obtener()).thenReturn(DatosDePrueba.autenticado(RolUsuario.FARMACIA, farmacia));
        when(verificadorEmpresa.exigirHabilitada(farmacia.getId())).thenReturn(farmacia);
        when(repository.findByGtinAndSerie(caja.getGtin(), caja.getSerie())).thenReturn(Optional.of(caja));
        when(repository.save(any(UnidadTrazable.class))).thenAnswer(inv -> inv.getArgument(0));

        service().devolver(devolucion(caja));

        assertEquals(EstadoUnidad.DEVUELTA, caja.getEstado());
        org.mockito.ArgumentCaptor<java.util.Map<String, Object>> datos = org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        verify(registradorEventos).registrar(eq(TipoEvento.DEVOLUCION), eq("UnidadTrazable"), eq(caja.getId()),
                datos.capture(), any(UsuarioAutenticado.class));
        assertEquals(MotivoDevolucion.DANADA, datos.getValue().get("motivo"));
        assertEquals(HashUtil.sha256Hex("Caja aplastada"), datos.getValue().get("observacionHash"));
    }

    @Test
    @DisplayName("Devolución de una caja de otra farmacia → 404; ya dispensada → TRANSICION_INVALIDA")
    void devolverInvalido() {
        Empresa farmacia = DatosDePrueba.empresaHabilitada(TipoEmpresa.FARMACIA);
        UnidadTrazable ajena = unidadEn(DatosDePrueba.empresaHabilitada(TipoEmpresa.FARMACIA));
        when(usuarioActual.obtener()).thenReturn(DatosDePrueba.autenticado(RolUsuario.FARMACIA, farmacia));
        when(verificadorEmpresa.exigirHabilitada(farmacia.getId())).thenReturn(farmacia);
        when(repository.findByGtinAndSerie(ajena.getGtin(), ajena.getSerie())).thenReturn(Optional.of(ajena));
        assertThrows(ResourceNotFoundException.class, () -> service().devolver(devolucion(ajena)));

        UnidadTrazable dispensada = unidadEn(farmacia);
        dispensada.dispensar();
        when(repository.findByGtinAndSerie(dispensada.getGtin(), dispensada.getSerie())).thenReturn(Optional.of(dispensada));
        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class, () -> service().devolver(devolucion(dispensada)));
        assertEquals("TRANSICION_INVALIDA", ex.getCodigoRegla());
    }
}
