package com.medichain.modules.telemetriagps;

import com.medichain.exceptions.ResourceNotFoundException;
import com.medichain.modules.empresa.Empresa;
import com.medichain.modules.empresa.TipoEmpresa;
import com.medichain.modules.usuario.RolUsuario;
import com.medichain.testutil.DatosDePrueba;
import com.medichain.utils.seguridad.UsuarioActual;
import com.medichain.modules.despachologistico.DespachoLogistico;
import com.medichain.modules.despachologistico.DespachoLogisticoRepository;
import com.medichain.modules.despachologistico.TramoDespacho;
import com.medichain.utils.seguridad.VerificadorEmpresa;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Test unitario TelemetriaGpsServiceTest en MediChain.
 * Prueba con Mockito el filtrado de datos por rol de TelemetriaGpsService: el usuario
 * ve lo suyo y lo ajeno responde 404 (no 403), para no revelar que existe.
 */
@ExtendWith(MockitoExtension.class)
class TelemetriaGpsServiceTest {

    @Mock
    private TelemetriaGpsRepository repository;

    @Mock
    private UsuarioActual usuarioActual;

    @Mock
    private DespachoLogisticoRepository despachoLogisticoRepository;

    @Mock
    private VerificadorEmpresa verificadorEmpresa;

    private final Pageable pagina = PageRequest.of(0, 20);

    /** Construye el Service bajo prueba con los mocks. */
    private TelemetriaGpsService service() {
        return new TelemetriaGpsService(repository, despachoLogisticoRepository, usuarioActual, verificadorEmpresa);
    }

    /** Lectura GPS de un despacho con origen en la empresa dada. */
    private TelemetriaGps lecturaDe(Empresa origen) {
        DespachoLogistico despacho = DatosDePrueba.viaje(TramoDespacho.DISTRIBUIDOR_A_FARMACIA, origen);
        TelemetriaGps lectura = new TelemetriaGps("G-1", -31.4, -64.2, null, Instant.now(), despacho);
        lectura.setId(UUID.randomUUID());
        return lectura;
    }

    @Test
    @DisplayName("Ve lo suyo: la distribuidora ve una lectura GPS de su despacho")
    void veLecturaDeSuDespacho() {
        Empresa distribuidora = DatosDePrueba.empresaHabilitada(TipoEmpresa.DISTRIBUIDOR);
        TelemetriaGps lectura = lecturaDe(distribuidora);
        when(usuarioActual.obtener()).thenReturn(DatosDePrueba.autenticado(RolUsuario.DISTRIBUIDOR, distribuidora));
        when(repository.findById(lectura.getId())).thenReturn(Optional.of(lectura));

        assertSame(lectura, service().getById(lectura.getId()));
    }

    @Test
    @DisplayName("No ve lo ajeno: una lectura GPS de un despacho ajeno responde 404")
    void noVeLecturaAjena() {
        Empresa distribuidora = DatosDePrueba.empresaHabilitada(TipoEmpresa.DISTRIBUIDOR);
        TelemetriaGps ajena = lecturaDe(DatosDePrueba.empresaHabilitada(TipoEmpresa.DISTRIBUIDOR));
        when(usuarioActual.obtener()).thenReturn(DatosDePrueba.autenticado(RolUsuario.DISTRIBUIDOR, distribuidora));
        when(repository.findById(ajena.getId())).thenReturn(Optional.of(ajena));

        assertThrows(ResourceNotFoundException.class, () -> service().getById(ajena.getId()));
    }
}
