package com.medichain.config;

import com.medichain.modules.auth.UsuarioAutenticado;
import com.medichain.modules.inspectoranmat.InspectorAnmatRepository;
import com.medichain.modules.usuario.RolUsuario;
import com.medichain.modules.usuario.Usuario;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.function.Supplier;

/**
 * Componente EjecutorComoUsuario en MediChain.
 * Ejecuta una acción REAL de un Service como si la hiciera el usuario
 * dado: pone en el SecurityContext el mismo UsuarioAutenticado que armaría
 * el filtro JWT (rol, empresa y provincia del inspector, leída de la base),
 * ejecuta y, en un finally, restaura el contexto anterior (aunque la acción
 * lance excepción). Lo usa DatosDemo para recorrer el mismo camino que la
 * API, incluidas las verificaciones D1 y R2. No usar en requests HTTP.
 */
@Component
public class EjecutorComoUsuario {

    private final InspectorAnmatRepository inspectorAnmatRepository;

    @Autowired
    public EjecutorComoUsuario(InspectorAnmatRepository inspectorAnmatRepository) {
        this.inspectorAnmatRepository = inspectorAnmatRepository;
    }

    /** Ejecuta la acción como el usuario dado. */
    public void ejecutarComo(Usuario usuario, Runnable accion) {
        ejecutarComo(usuario, () -> {
            accion.run();
            return null;
        });
    }

    /** Ejecuta la acción como el usuario dado y devuelve su resultado. */
    public <T> T ejecutarComo(Usuario usuario, Supplier<T> accion) {
        SecurityContext anterior = SecurityContextHolder.getContext();
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        UsuarioAutenticado principal = autenticadoDe(usuario);
        contexto.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRol().name()))));
        SecurityContextHolder.setContext(contexto);
        try {
            return accion.get();
        } finally {
            // Se restaura el contexto previo (vacío en DatosDemo): nunca queda el usuario "pegado" al hilo.
            if (anterior.getAuthentication() == null) {
                SecurityContextHolder.clearContext();
            } else {
                SecurityContextHolder.setContext(anterior);
            }
        }
    }

    /** Arma el principal igual que el login: provincia solo para INSPECTOR, leída de la base. */
    private UsuarioAutenticado autenticadoDe(Usuario usuario) {
        String provincia = null;
        if (usuario.getRol() == RolUsuario.INSPECTOR) {
            provincia = inspectorAnmatRepository.findByUsuarioId(usuario.getId())
                    .map(inspector -> inspector.getProvincia().name())
                    .orElse(null);
        }
        return new UsuarioAutenticado(usuario.getId(), usuario.getEmail(), usuario.getRol(),
                usuario.getEmpresa() != null ? usuario.getEmpresa().getId() : null, provincia);
    }
}
