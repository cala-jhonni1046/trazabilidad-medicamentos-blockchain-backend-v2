package com.medichain.modules.auth;

import com.medichain.exceptions.CredencialesInvalidasException;
import com.medichain.modules.inspectoranmat.InspectorAnmat;
import com.medichain.modules.inspectoranmat.InspectorAnmatRepository;
import com.medichain.modules.usuario.RolUsuario;
import com.medichain.modules.usuario.Usuario;
import com.medichain.modules.usuario.UsuarioRepository;
import com.medichain.testutil.DatosDePrueba;
import com.medichain.utils.enums.Provincia;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test unitario AuthServiceTest en MediChain.
 * Prueba AuthService.login() con Mockito (sin Spring ni base): login
 * correcto y los tres casos de falla, que deben dar el mismo mensaje.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String EMAIL = "ana@demo.com";
    private static final String HASH = "$2a$10$hashDeEjemplo";

    @Mock
    private UsuarioDetailsService usuarioDetailsService;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private InspectorAnmatRepository inspectorAnmatRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private AuthService authService;

    /** Arrange común: el Service se construye explícitamente con los mocks. */
    @BeforeEach
    void setUp() {
        authService = new AuthService(usuarioDetailsService, usuarioRepository, inspectorAnmatRepository,
                passwordEncoder, jwtService);
    }

    @Test
    @DisplayName("Login correcto devuelve el token y actualiza el último login")
    void loginCorrectoDevuelveToken() {
        // Arrange
        Usuario usuario = new Usuario(EMAIL, HASH, "Ana", "Perez", "12345678", RolUsuario.PACIENTE);
        usuario.setId(UUID.randomUUID());
        when(usuarioDetailsService.loadUserByUsername(EMAIL)).thenReturn(new User(EMAIL, HASH, List.of()));
        when(passwordEncoder.matches("claveCorrecta", HASH)).thenReturn(true);
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
        Instant vencimiento = Instant.now().plusSeconds(3600);
        when(jwtService.calcularVencimiento()).thenReturn(vencimiento);
        when(jwtService.generarToken(usuario, null, vencimiento)).thenReturn("token.de.prueba");

        // Act
        LoginResponseDTO respuesta = authService.login(new LoginRequestDTO(EMAIL, "claveCorrecta"));

        // Assert
        assertEquals("token.de.prueba", respuesta.getToken());
        assertEquals("Bearer", respuesta.getTipo());
        assertEquals(usuario.getId(), respuesta.getUsuarioId());
        assertEquals(vencimiento, respuesta.getExpiraEn(), "el vencimiento es el Instant del token (UTC)");
        assertFalse(respuesta.isEsAdminEmpresa());
        assertFalse(respuesta.isEsDirectorTecnico());
        assertNull(respuesta.getProvincia(), "la provincia solo va para el inspector");
        assertNotNull(usuario.getUltimoLogin(), "debe registrar el último login");
        verify(usuarioRepository).save(usuario);
    }

    @Test
    @DisplayName("Login de un admin que es director técnico: las dos marcas en true, sin provincia")
    void loginDevuelveLasMarcasDelUsuario() {
        Usuario usuario = new Usuario(EMAIL, HASH, "Elena", "Sosa", "12345678", RolUsuario.LABORATORIO);
        usuario.setId(UUID.randomUUID());
        usuario.setEsAdminEmpresa(true);
        usuario.setEsDirectorTecnico(true);
        prepararLogin(usuario, null);

        LoginResponseDTO respuesta = authService.login(new LoginRequestDTO(EMAIL, "claveCorrecta"));

        assertTrue(respuesta.isEsAdminEmpresa());
        assertTrue(respuesta.isEsDirectorTecnico());
        assertNull(respuesta.getProvincia());
    }

    @Test
    @DisplayName("Login de un inspector: la provincia de su jurisdicción, la misma que va en el JWT")
    void loginDeInspectorDevuelveSuProvincia() {
        InspectorAnmat inspector = DatosDePrueba.inspector(Provincia.SALTA);
        Usuario usuario = inspector.getUsuario();
        prepararLogin(usuario, "SALTA");
        when(inspectorAnmatRepository.findByUsuarioId(usuario.getId())).thenReturn(Optional.of(inspector));

        LoginResponseDTO respuesta = authService.login(new LoginRequestDTO(EMAIL, "claveCorrecta"));

        assertEquals(Provincia.SALTA, respuesta.getProvincia());
        assertEquals("token.de.prueba", respuesta.getToken(), "el JWT se generó con la misma provincia");
    }

    /** Login correcto para el usuario dado; el token se genera con la provincia indicada. */
    private void prepararLogin(Usuario usuario, String provinciaEnElToken) {
        when(usuarioDetailsService.loadUserByUsername(EMAIL)).thenReturn(new User(EMAIL, HASH, List.of()));
        when(passwordEncoder.matches("claveCorrecta", HASH)).thenReturn(true);
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
        Instant vencimiento = Instant.now().plusSeconds(3600);
        when(jwtService.calcularVencimiento()).thenReturn(vencimiento);
        when(jwtService.generarToken(usuario, provinciaEnElToken, vencimiento)).thenReturn("token.de.prueba");
    }

    @Test
    @DisplayName("Contraseña incorrecta → \"Credenciales inválidas\"")
    void contrasenaIncorrectaDaCredencialesInvalidas() {
        when(usuarioDetailsService.loadUserByUsername(EMAIL)).thenReturn(new User(EMAIL, HASH, List.of()));
        when(passwordEncoder.matches("claveIncorrecta", HASH)).thenReturn(false);

        CredencialesInvalidasException ex = assertThrows(CredencialesInvalidasException.class,
                () -> authService.login(new LoginRequestDTO(EMAIL, "claveIncorrecta")));
        assertEquals("Credenciales inválidas", ex.getMessage());
        verify(jwtService, never()).generarToken(any(), any(), any());
    }

    @Test
    @DisplayName("Email inexistente → el mismo mensaje \"Credenciales inválidas\"")
    void emailInexistenteDaElMismoMensaje() {
        when(usuarioDetailsService.loadUserByUsername("nadie@demo.com"))
                .thenThrow(new UsernameNotFoundException("Usuario no encontrado o inactivo"));

        CredencialesInvalidasException ex = assertThrows(CredencialesInvalidasException.class,
                () -> authService.login(new LoginRequestDTO("nadie@demo.com", "cualquiera")));
        assertEquals("Credenciales inválidas", ex.getMessage());
    }

    @Test
    @DisplayName("Usuario inactivo → no autentica")
    void usuarioInactivoNoAutentica() {
        // UsuarioDetailsService rechaza a los inactivos igual que a los inexistentes.
        when(usuarioDetailsService.loadUserByUsername(EMAIL))
                .thenThrow(new UsernameNotFoundException("Usuario no encontrado o inactivo"));

        CredencialesInvalidasException ex = assertThrows(CredencialesInvalidasException.class,
                () -> authService.login(new LoginRequestDTO(EMAIL, "claveCorrecta")));
        assertEquals("Credenciales inválidas", ex.getMessage());
        verify(passwordEncoder, never()).matches(any(), any());
        verify(usuarioRepository, never()).save(any());
    }
}
