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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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

    @Test
    @DisplayName("B12b: el listado pasa el estado a la consulta de cada rol (nunca findAll sin alcance)")
    void listadoConEstadoPorRol() {
        Empresa laboratorio = DatosDePrueba.empresaHabilitada(TipoEmpresa.LABORATORIO);
        Empresa distribuidora = DatosDePrueba.empresaHabilitada(TipoEmpresa.DISTRIBUIDOR);
        Empresa farmacia = DatosDePrueba.empresaHabilitada(TipoEmpresa.FARMACIA);
        Page<UnidadTrazable> vacia = Page.empty(pagina);
        lenient().when(repository.findPorEstado(EstadoUnidad.EN_STOCK, "%", pagina)).thenReturn(vacia);
        lenient().when(repository.findDelLaboratorioPorEstado(laboratorio.getId(), EstadoUnidad.EN_STOCK, "%", pagina)).thenReturn(vacia);
        lenient().when(repository.findVisiblesParaDistribuidor(distribuidora.getId(), EstadoUnidad.EN_STOCK, "%", pagina)).thenReturn(vacia);
        lenient().when(repository.findVisiblesParaFarmacia(farmacia.getId(), EstadoUnidad.EN_STOCK, "%", pagina)).thenReturn(vacia);

        when(usuarioActual.obtener()).thenReturn(DatosDePrueba.autenticado(RolUsuario.SEDE_CENTRAL, null));
        service().getAll(EstadoUnidad.EN_STOCK, null, pagina);
        verify(repository).findPorEstado(EstadoUnidad.EN_STOCK, "%", pagina);

        when(usuarioActual.obtener()).thenReturn(DatosDePrueba.autenticado(RolUsuario.LABORATORIO, laboratorio));
        service().getAll(EstadoUnidad.EN_STOCK, null, pagina);
        verify(repository).findDelLaboratorioPorEstado(laboratorio.getId(), EstadoUnidad.EN_STOCK, "%", pagina);

        when(usuarioActual.obtener()).thenReturn(DatosDePrueba.autenticado(RolUsuario.DISTRIBUIDOR, distribuidora));
        service().getAll(EstadoUnidad.EN_STOCK, null, pagina);
        verify(repository).findVisiblesParaDistribuidor(distribuidora.getId(), EstadoUnidad.EN_STOCK, "%", pagina);

        when(usuarioActual.obtener()).thenReturn(DatosDePrueba.autenticado(RolUsuario.FARMACIA, farmacia));
        service().getAll(EstadoUnidad.EN_STOCK, null, pagina);
        verify(repository).findVisiblesParaFarmacia(farmacia.getId(), EstadoUnidad.EN_STOCK, "%", pagina);
    }

    @Test
    @DisplayName("B12b: sin estado (null) se pide todo el alcance; el paciente no ve cajas")
    void listadoSinEstadoYPaciente() {
        Empresa farmacia = DatosDePrueba.empresaHabilitada(TipoEmpresa.FARMACIA);
        when(usuarioActual.obtener()).thenReturn(DatosDePrueba.autenticado(RolUsuario.FARMACIA, farmacia));
        when(repository.findVisiblesParaFarmacia(farmacia.getId(), null, "%", pagina)).thenReturn(Page.empty(pagina));
        service().getAll(null, null, pagina);
        verify(repository).findVisiblesParaFarmacia(farmacia.getId(), null, "%", pagina);

        when(usuarioActual.obtener()).thenReturn(DatosDePrueba.autenticado(RolUsuario.PACIENTE, null));
        assertTrue(service().getAll(EstadoUnidad.EN_STOCK, null, pagina).isEmpty());
        verify(repository, never()).findPorEstado(any(), any(), any());
    }

    @Test
    @DisplayName("B12e: ?serie= llega a la consulta del rol como patrón 'contiene', en minúsculas y con comodines escapados")
    void busquedaPorSerie() {
        Empresa farmacia = DatosDePrueba.empresaHabilitada(TipoEmpresa.FARMACIA);
        when(usuarioActual.obtener()).thenReturn(DatosDePrueba.autenticado(RolUsuario.FARMACIA, farmacia));
        when(repository.findVisiblesParaFarmacia(farmacia.getId(), EstadoUnidad.EN_STOCK, "%s0000\\_1%", pagina))
                .thenReturn(Page.empty(pagina));

        service().getAll(EstadoUnidad.EN_STOCK, "S0000_1", pagina);

        verify(repository).findVisiblesParaFarmacia(farmacia.getId(), EstadoUnidad.EN_STOCK, "%s0000\\_1%", pagina);
    }
}
