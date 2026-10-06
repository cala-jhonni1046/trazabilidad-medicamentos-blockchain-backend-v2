package com.medichain.config;

import com.medichain.modules.auth.UsuarioAutenticado;
import com.medichain.modules.inspectoranmat.InspectorAnmatRepository;
import com.medichain.modules.usuario.RolUsuario;
import com.medichain.modules.usuario.Usuario;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test unitario EjecutorComoUsuarioTest en MediChain.
 * Durante la acción el contexto tiene al usuario (rol y authority); al
 * terminar, aunque la acción lance excepción, el contexto queda limpio.
 */
@ExtendWith(MockitoExtension.class)
class EjecutorComoUsuarioTest {

    @Mock
    private InspectorAnmatRepository inspectorAnmatRepository;

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    /** Usuario SEDE con id. */
    private Usuario sede() {
        Usuario usuario = new Usuario("sede@demo.com", "hash", "Ana", "Perez", "12345678", RolUsuario.SEDE_CENTRAL);
        usuario.setId(UUID.randomUUID());
        return usuario;
    }

    @Test
    @DisplayName("Durante la acción el usuario está en el contexto; después se limpia")
    void poneYLimpiaElContexto() {
        Usuario usuario = sede();
        EjecutorComoUsuario ejecutor = new EjecutorComoUsuario(inspectorAnmatRepository);

        UUID visto = ejecutor.ejecutarComo(usuario, () -> {
            Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
            assertTrue(autenticacion.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_SEDE_CENTRAL")));
            return ((UsuarioAutenticado) autenticacion.getPrincipal()).getUsuarioId();
        });

        assertEquals(usuario.getId(), visto);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Si la acción lanza excepción, el contexto igual queda limpio")
    void limpiaAunqueFalle() {
        EjecutorComoUsuario ejecutor = new EjecutorComoUsuario(inspectorAnmatRepository);

        assertThrows(IllegalStateException.class, () -> ejecutor.ejecutarComo(sede(), (Runnable) () -> {
            throw new IllegalStateException("falla de la acción");
        }));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
