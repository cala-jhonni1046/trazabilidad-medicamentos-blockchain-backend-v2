package com.medichain.config;

import com.medichain.modules.auth.JwtService;
import com.medichain.modules.dispensacion.DispensacionController;
import com.medichain.modules.dispensacion.DispensacionMapper;
import com.medichain.modules.dispensacion.DispensacionService;
import com.medichain.modules.empresa.Empresa;
import com.medichain.modules.empresa.EmpresaController;
import com.medichain.modules.empresa.EmpresaMapper;
import com.medichain.modules.empresa.EmpresaService;
import com.medichain.modules.empresa.TipoEmpresa;
import com.medichain.modules.usuario.RolUsuario;
import com.medichain.modules.usuario.Usuario;
import com.medichain.modules.usuario.UsuarioRepository;
import com.medichain.testutil.DatosDePrueba;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test de capa web FiltroJwtRealTest en MediChain.
 * A diferencia de SeguridadEndpointsTest, acá el JwtService es REAL: se
 * generan tokens firmados de verdad y cada request pasa por el
 * JwtAuthenticationFilter real. Cubre usuarios sin empresa ni provincia
 * (PACIENTE) y con provincia pero sin empresa (INSPECTOR), y el token
 * vigente de una cuenta que ya no está activa (→ 401).
 */
@WebMvcTest(controllers = {EmpresaController.class, DispensacionController.class})
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, JwtService.class})
@TestPropertySource(properties = {
        "jwt.secret=secreto-de-prueba-de-al-menos-32-bytes-para-hs256",
        "jwt.expiracion-horas=8"
})
class FiltroJwtRealTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private EmpresaService empresaService;

    @MockitoBean
    private EmpresaMapper empresaMapper;

    @MockitoBean
    private DispensacionService dispensacionService;

    @MockitoBean
    private DispensacionMapper dispensacionMapper;

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    /** Por defecto toda cuenta está activa; el test de cuenta inactiva lo cambia. */
    @BeforeEach
    void cuentasActivas() {
        when(usuarioRepository.existsByIdAndActivoTrue(any(UUID.class))).thenReturn(true);
    }

    /** Genera un token real para un usuario del rol dado, con la empresa y provincia indicadas. */
    private String tokenDe(RolUsuario rol, Empresa empresa, String provincia) {
        Usuario usuario = new Usuario(rol.name().toLowerCase() + "@demo.com", "hash", "Ana", "Perez", "12345678", rol);
        usuario.setId(UUID.randomUUID());
        usuario.setEmpresa(empresa);
        return jwtService.generarToken(usuario, provincia, jwtService.calcularVencimiento());
    }

    @Test
    @DisplayName("PACIENTE (sin empresa ni provincia) con token real en GET /api/dispensaciones → 403, no 401")
    void pacienteConTokenRealRecibe403() throws Exception {
        String token = tokenDe(RolUsuario.PACIENTE, null, null);

        mockMvc.perform(get("/api/dispensaciones").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("INSPECTOR (con provincia, sin empresa) con token real en GET /api/empresas → 200")
    void inspectorConTokenRealRecibe200() throws Exception {
        when(empresaService.getAll(any(), any(Pageable.class))).thenReturn(Page.empty());
        String token = tokenDe(RolUsuario.INSPECTOR, null, "MENDOZA");

        mockMvc.perform(get("/api/empresas").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("FARMACIA (con empresa) con token real en GET /api/dispensaciones → 200")
    void farmaciaConTokenRealRecibe200() throws Exception {
        when(dispensacionService.getAll(any(Pageable.class))).thenReturn(Page.empty());
        String token = tokenDe(RolUsuario.FARMACIA, DatosDePrueba.empresaHabilitada(TipoEmpresa.FARMACIA), null);

        mockMvc.perform(get("/api/dispensaciones").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Token real y vigente de una cuenta desactivada (p. ej. inspector dado de baja) → 401")
    void cuentaInactivaConTokenVigenteRecibe401() throws Exception {
        when(empresaService.getAll(any(), any(Pageable.class))).thenReturn(Page.empty());
        String token = tokenDe(RolUsuario.INSPECTOR, null, "MENDOZA");
        when(usuarioRepository.existsByIdAndActivoTrue(any(UUID.class))).thenReturn(false);

        mockMvc.perform(get("/api/empresas").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Token pegado con 'Bearer' dos veces (error típico en Swagger) → 401")
    void bearerDuplicadoRecibe401() throws Exception {
        String token = tokenDe(RolUsuario.PACIENTE, null, null);

        mockMvc.perform(get("/api/dispensaciones").header("Authorization", "Bearer Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}
