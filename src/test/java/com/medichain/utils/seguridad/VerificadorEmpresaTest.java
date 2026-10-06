package com.medichain.utils.seguridad;

import com.medichain.exceptions.ReglaNegocioException;
import com.medichain.modules.empresa.Empresa;
import com.medichain.modules.empresa.EmpresaRepository;
import com.medichain.modules.empresa.TipoEmpresa;
import com.medichain.testutil.DatosDePrueba;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Test unitario VerificadorEmpresaTest en MediChain.
 * Prueba la regla R2 verificada contra la base (repository simulado).
 */
@ExtendWith(MockitoExtension.class)
class VerificadorEmpresaTest {

    @Mock
    private EmpresaRepository empresaRepository;

    private VerificadorEmpresa verificador;

    @BeforeEach
    void setUp() {
        verificador = new VerificadorEmpresa(empresaRepository);
    }

    @Test
    @DisplayName("Devuelve la empresa si está HABILITADA")
    void aceptaEmpresaHabilitada() {
        Empresa empresa = DatosDePrueba.empresaHabilitada(TipoEmpresa.LABORATORIO);
        when(empresaRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));

        assertSame(empresa, verificador.exigirHabilitada(empresa.getId()));
    }

    @Test
    @DisplayName("R2: rechaza una empresa SUSPENDIDA aunque el token siga vigente")
    void rechazaEmpresaSuspendida() {
        Empresa empresa = DatosDePrueba.empresaHabilitada(TipoEmpresa.LABORATORIO);
        empresa.suspender("Inspección con hallazgos");
        when(empresaRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> verificador.exigirHabilitada(empresa.getId()));
        assertEquals("R2", ex.getCodigoRegla());
    }

    @Test
    @DisplayName("R2 / D6: un usuario sin empresa (null) se trata como no habilitado")
    void rechazaEmpresaNull() {
        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> verificador.exigirHabilitada(null));
        assertEquals("R2", ex.getCodigoRegla());
    }

    @Test
    @DisplayName("R2: rechaza una empresa inexistente")
    void rechazaEmpresaInexistente() {
        UUID id = UUID.randomUUID();
        when(empresaRepository.findById(id)).thenReturn(Optional.empty());

        assertEquals("R2", assertThrows(ReglaNegocioException.class,
                () -> verificador.exigirHabilitada(id)).getCodigoRegla());
    }
}
