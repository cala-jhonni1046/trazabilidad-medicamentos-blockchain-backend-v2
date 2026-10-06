package com.medichain.modules.auth;

import com.medichain.modules.usuario.RolUsuario;
import com.medichain.modules.usuario.Usuario;
import com.medichain.modules.usuario.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Test unitario UsuarioDetailsServiceTest en MediChain.
 * Prueba que UsuarioDetailsService carga a los usuarios activos y
 * rechaza a los inexistentes y a los inactivos.
 */
@ExtendWith(MockitoExtension.class)
class UsuarioDetailsServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Test
    @DisplayName("Carga un usuario activo con la autoridad ROLE_<rol>")
    void cargaUsuarioActivo() {
        Usuario usuario = new Usuario("ana@demo.com", "hash", "Ana", "Perez", "12345678", RolUsuario.FARMACIA);
        when(usuarioRepository.findByEmail("ana@demo.com")).thenReturn(Optional.of(usuario));

        UserDetails detalles = new UsuarioDetailsService(usuarioRepository).loadUserByUsername("ana@demo.com");

        assertEquals("hash", detalles.getPassword());
        assertEquals("ROLE_FARMACIA", detalles.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    @DisplayName("Rechaza un usuario inactivo")
    void rechazaUsuarioInactivo() {
        Usuario usuario = new Usuario("ana@demo.com", "hash", "Ana", "Perez", "12345678", RolUsuario.FARMACIA);
        usuario.setActivo(false);
        when(usuarioRepository.findByEmail("ana@demo.com")).thenReturn(Optional.of(usuario));

        assertThrows(UsernameNotFoundException.class,
                () -> new UsuarioDetailsService(usuarioRepository).loadUserByUsername("ana@demo.com"));
    }

    @Test
    @DisplayName("Rechaza un email inexistente")
    void rechazaEmailInexistente() {
        when(usuarioRepository.findByEmail("nadie@demo.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> new UsuarioDetailsService(usuarioRepository).loadUserByUsername("nadie@demo.com"));
    }
}
