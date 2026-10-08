package com.medichain.config;

import com.medichain.modules.auth.JwtService;
import com.medichain.modules.usuario.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configuración SecurityConfig en MediChain.
 * API stateless con JWT: sin sesiones, sin CSRF (no hay cookies), sin
 * httpBasic ni formLogin. JwtAuthenticationFilter autentica cada request
 * a partir del header Authorization (y confirma que la cuenta siga activa). Solo el login, Swagger y la
 * verificación pública de cajas son rutas abiertas; todo lo demás exige
 * autenticación (401 si falta). @EnableMethodSecurity queda habilitado
 * para los permisos por rol (@PreAuthorize) del paso siguiente.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    @Autowired
    public SecurityConfig(JwtService jwtService, UsuarioRepository usuarioRepository,
                          RestAuthenticationEntryPoint authenticationEntryPoint,
                          RestAccessDeniedHandler accessDeniedHandler) {
        this.jwtService = jwtService;
        this.usuarioRepository = usuarioRepository;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    /** Define la cadena de filtros de seguridad y las rutas públicas. */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/registro/empresas", "/api/registro/pacientes").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/verificacion", "/api/verificacion/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, usuarioRepository), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
