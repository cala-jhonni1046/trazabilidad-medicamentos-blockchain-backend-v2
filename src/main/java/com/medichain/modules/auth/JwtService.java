package com.medichain.modules.auth;

import com.medichain.modules.usuario.RolUsuario;
import com.medichain.modules.usuario.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

/**
 * Servicio JwtService en MediChain.
 * Genera, valida y lee los JWT firmados con HMAC-SHA256. El token lleva
 * sub = usuarioId, email, rol, empresaId (si tiene) y provincia (si es
 * inspector). La firma se calcula con el secreto de la variable de
 * entorno JWT_SECRET, por eso no se puede falsificar sin conocerlo.
 */
@Service
public class JwtService {

    /** HMAC-SHA256 exige una clave de al menos 256 bits (32 bytes). */
    private static final int LONGITUD_MINIMA_CLAVE_BYTES = 32;

    private final SecretKey clave;
    private final long expiracionHoras;

    @Autowired
    public JwtService(@Value("${jwt.secret}") String secreto,
                      @Value("${jwt.expiracion-horas}") long expiracionHoras) {
        byte[] bytes = secreto.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < LONGITUD_MINIMA_CLAVE_BYTES) {
            throw new IllegalStateException("JWT_SECRET debe tener al menos 256 bits (32 bytes)");
        }
        this.clave = Keys.hmacShaKeyFor(bytes);
        this.expiracionHoras = expiracionHoras;
    }

    /** Calcula el instante de vencimiento de un token emitido ahora. */
    public Instant calcularVencimiento() {
        return Instant.now().plus(expiracionHoras, ChronoUnit.HOURS);
    }

    /**
     * Genera el JWT del usuario con vencimiento en el instante dado.
     * provincia solo se incluye si no es null (usuarios inspectores).
     */
    public String generarToken(Usuario usuario, String provincia, Instant vencimiento) {
        JwtBuilder builder = Jwts.builder()
                .subject(usuario.getId().toString())
                .claim("email", usuario.getEmail())
                .claim("rol", usuario.getRol().name())
                .issuedAt(new Date())
                .expiration(Date.from(vencimiento));
        if (usuario.getEmpresa() != null) {
            builder.claim("empresaId", usuario.getEmpresa().getId().toString());
        }
        if (provincia != null) {
            builder.claim("provincia", provincia);
        }
        return builder.signWith(clave).compact();
    }

    /**
     * Valida firma y vencimiento y devuelve los claims. Lanza
     * JwtException (o IllegalArgumentException si el texto está vacío)
     * si el token es inválido, está alterado o venció.
     */
    public Claims extraerClaims(String token) {
        return Jwts.parser()
                .verifyWith(clave)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** Indica si el token tiene firma válida y no está vencido. */
    public boolean esValido(String token) {
        try {
            extraerClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /** Arma el principal UsuarioAutenticado a partir de los claims de un token ya validado. */
    public UsuarioAutenticado aUsuarioAutenticado(Claims claims) {
        String empresaId = claims.get("empresaId", String.class);
        return new UsuarioAutenticado(
                UUID.fromString(claims.getSubject()),
                claims.get("email", String.class),
                RolUsuario.valueOf(claims.get("rol", String.class)),
                empresaId != null ? UUID.fromString(empresaId) : null,
                claims.get("provincia", String.class));
    }
}
