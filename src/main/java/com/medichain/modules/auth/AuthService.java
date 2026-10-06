package com.medichain.modules.auth;

import com.medichain.exceptions.CredencialesInvalidasException;
import com.medichain.modules.inspectoranmat.InspectorAnmatRepository;
import com.medichain.modules.usuario.RolUsuario;
import com.medichain.modules.usuario.Usuario;
import com.medichain.modules.usuario.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Servicio AuthService en MediChain.
 * Resuelve el login: valida las credenciales contra el hash BCrypt,
 * registra el último login y emite el JWT. Cualquier falla (email
 * inexistente, usuario inactivo o contraseña incorrecta) termina en el
 * mismo "Credenciales inválidas", para no revelar cuál falló.
 */
@Service
public class AuthService {

    private final UsuarioDetailsService usuarioDetailsService;
    private final UsuarioRepository usuarioRepository;
    private final InspectorAnmatRepository inspectorAnmatRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Autowired
    public AuthService(UsuarioDetailsService usuarioDetailsService, UsuarioRepository usuarioRepository,
                       InspectorAnmatRepository inspectorAnmatRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.usuarioDetailsService = usuarioDetailsService;
        this.usuarioRepository = usuarioRepository;
        this.inspectorAnmatRepository = inspectorAnmatRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * Autentica al usuario y devuelve el token. Lanza
     * CredencialesInvalidasException (→ 401) si algo no coincide.
     */
    @Transactional
    public LoginResponseDTO login(LoginRequestDTO dto) {
        UserDetails detalles;
        try {
            detalles = usuarioDetailsService.loadUserByUsername(dto.getEmail());
        } catch (UsernameNotFoundException e) {
            throw new CredencialesInvalidasException();
        }
        if (!passwordEncoder.matches(dto.getPassword(), detalles.getPassword())) {
            throw new CredencialesInvalidasException();
        }
        Usuario usuario = usuarioRepository.findByEmail(dto.getEmail())
                .orElseThrow(CredencialesInvalidasException::new);

        usuario.setUltimoLogin(LocalDateTime.now());
        usuarioRepository.save(usuario);

        String provincia = null;
        if (usuario.getRol() == RolUsuario.INSPECTOR) {
            provincia = inspectorAnmatRepository.findByUsuarioId(usuario.getId())
                    .map(inspector -> inspector.getProvincia().name())
                    .orElse(null);
        }
        Instant vencimiento = jwtService.calcularVencimiento();
        String token = jwtService.generarToken(usuario, provincia, vencimiento);
        return new LoginResponseDTO(token, LocalDateTime.ofInstant(vencimiento, ZoneOffset.UTC),
                usuario.getId(), usuario.getNombre(), usuario.getRol(),
                usuario.getEmpresa() != null ? usuario.getEmpresa().getId() : null);
    }
}
