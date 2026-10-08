package com.medichain.config;

import com.medichain.modules.auth.JwtService;
import com.medichain.modules.auth.UsuarioAutenticado;
import com.medichain.modules.usuario.UsuarioRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

/**
 * Filtro JwtAuthenticationFilter en MediChain.
 * Se ejecuta una vez por request, antes del filtro de usuario/contraseña
 * de Spring Security. Si viene "Authorization: Bearer &lt;token&gt;", el
 * token es válido Y la cuenta sigue activa en la base, deja un
 * UsuarioAutenticado con la autoridad ROLE_&lt;rol&gt; en el SecurityContext.
 * Si falta, es inválido/vencido o la cuenta ya no está activa, no hace
 * nada: la request sigue sin autenticación y, si la ruta no es pública,
 * termina en 401.
 * La cuenta se verifica en la base en cada request (una consulta por clave
 * primaria): sin eso, un inspector dado de baja o un empleado desactivado
 * seguiría usando su token hasta que venza (8 h) en las rutas que solo
 * miran el rol.
 * No es @Component a propósito: si lo fuera, Spring Boot además lo
 * registraría como filtro de servlet global y correría dos veces. Lo
 * instancia SecurityConfig y lo agrega solo a la cadena de seguridad.
 * Todo token rechazado deja un log.warn con el motivo (nunca el token),
 * para que un 401 no quede silencioso. Usuarios sin empresa ni provincia
 * (PACIENTE, SEDE_CENTRAL) o sin empresa (INSPECTOR) se autentican igual:
 * esos claims son opcionales.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String PREFIJO_BEARER = "Bearer ";

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;

    /** Crea el filtro con el servicio que valida los tokens y el repositorio que confirma la cuenta activa. */
    public JwtAuthenticationFilter(JwtService jwtService, UsuarioRepository usuarioRepository) {
        this.jwtService = jwtService;
        this.usuarioRepository = usuarioRepository;
    }

    /** Valida el token del header y carga la autenticación, si corresponde. */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && !header.startsWith(PREFIJO_BEARER)) {
            logger.warn("Header Authorization sin prefijo 'Bearer ' en {} {}: se ignora",
                    request.getMethod(), request.getRequestURI());
        } else if (header != null) {
            String token = header.substring(PREFIJO_BEARER.length());
            try {
                Claims claims = jwtService.extraerClaims(token);
                UsuarioAutenticado usuario = jwtService.aUsuarioAutenticado(claims);
                if (usuarioRepository.existsByIdAndActivoTrue(usuario.getUsuarioId())) {
                    UsernamePasswordAuthenticationToken autenticacion = new UsernamePasswordAuthenticationToken(
                            usuario, null, List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRol().name())));
                    SecurityContextHolder.getContext().setAuthentication(autenticacion);
                } else {
                    // Token bien firmado y vigente, pero la cuenta se desactivó (o se borró) después de emitirlo.
                    logger.warn("Token JWT rechazado en {} {}: la cuenta está inactiva o ya no existe",
                            request.getMethod(), request.getRequestURI());
                    SecurityContextHolder.clearContext();
                }
            } catch (RuntimeException e) {
                // Token alterado, vencido, mal formado o con claims inválidos: se sigue
                // sin autenticación (→ 401 si la ruta es privada) y se registra el motivo.
                logger.warn("Token JWT rechazado en {} {}: {}", request.getMethod(), request.getRequestURI(),
                        motivo(e));
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Describe por qué se rechazó el token, sin incluir el token ni su
     * contenido (solo el tipo de falla).
     */
    private String motivo(RuntimeException e) {
        if (e instanceof ExpiredJwtException) {
            return "token vencido";
        }
        if (e instanceof SignatureException) {
            return "firma inválida (token alterado o firmado con otro secreto)";
        }
        if (e instanceof MalformedJwtException) {
            return "token mal formado (¿se pegó con comillas, incompleto o con 'Bearer' dos veces?)";
        }
        return "token inválido (" + e.getClass().getSimpleName() + ")";
    }
}
