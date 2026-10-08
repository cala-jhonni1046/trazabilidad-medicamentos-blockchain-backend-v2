package com.medichain.modules.medicamento;

import com.medichain.exceptions.ReglaNegocioException;
import com.medichain.modules.auth.UsuarioAutenticado;
import com.medichain.modules.empresa.Empresa;
import com.medichain.modules.empresa.TipoEmpresa;
import com.medichain.modules.trazabilidad.RegistradorEventos;
import com.medichain.modules.trazabilidad.TipoEvento;
import com.medichain.modules.usuario.RolUsuario;
import com.medichain.testutil.DatosDePrueba;
import com.medichain.utils.seguridad.UsuarioActual;
import com.medichain.utils.seguridad.VerificadorEmpresa;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test unitario MedicamentoServiceTest en MediChain.
 * Alta de un medicamento: el GTIN se normaliza, queda a nombre del
 * laboratorio del usuario y deja su evento; un GTIN ya registrado responde
 * 409 MEDICAMENTO_DUPLICADO sin guardar nada.
 */
@ExtendWith(MockitoExtension.class)
class MedicamentoServiceTest {

    private static final String GTIN = "07799000001010";

    @Mock
    private MedicamentoRepository repository;

    @Mock
    private UsuarioActual usuarioActual;

    @Mock
    private VerificadorEmpresa verificadorEmpresa;

    @Mock
    private RegistradorEventos registradorEventos;

    private final Empresa laboratorio = DatosDePrueba.empresaHabilitada(TipoEmpresa.LABORATORIO);

    /** Service autenticado como el laboratorio habilitado. */
    private MedicamentoService service() {
        UsuarioAutenticado actual = DatosDePrueba.autenticado(RolUsuario.LABORATORIO, laboratorio);
        when(usuarioActual.obtener()).thenReturn(actual);
        when(verificadorEmpresa.exigirHabilitada(laboratorio.getId())).thenReturn(laboratorio);
        return new MedicamentoService(repository, usuarioActual, verificadorEmpresa, registradorEventos);
    }

    private static Medicamento nuevo() {
        return new Medicamento(GTIN, "Cuyafen", "Ibuprofeno", "400 mg", "Comprimido", "Caja x 20");
    }

    @Test
    @DisplayName("Alta: queda a nombre del laboratorio del usuario y deja MEDICAMENTO_REGISTRADO")
    void altaCasoFeliz() {
        MedicamentoService servicio = service();
        when(repository.save(any(Medicamento.class))).thenAnswer(inv -> inv.getArgument(0));

        Medicamento guardado = servicio.create(nuevo(), new MedicamentoRequestDTO());

        assertSame(laboratorio, guardado.getLaboratorio());
        verify(registradorEventos).registrar(eq(TipoEvento.MEDICAMENTO_REGISTRADO), eq("Medicamento"), any(),
                anyMap(), any(UsuarioAutenticado.class));
    }

    @Test
    @DisplayName("GTIN ya registrado → 409 MEDICAMENTO_DUPLICADO, sin guardar ni registrar evento")
    void gtinRepetido() {
        MedicamentoService servicio = service();
        when(repository.existsByGtin(GTIN)).thenReturn(true);

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> servicio.create(nuevo(), new MedicamentoRequestDTO()));

        assertEquals("MEDICAMENTO_DUPLICADO", ex.getCodigoRegla());
        verify(repository, never()).save(any());
        verify(registradorEventos, never()).registrar(any(), any(), any(), anyMap(), any(UsuarioAutenticado.class));
    }
}
