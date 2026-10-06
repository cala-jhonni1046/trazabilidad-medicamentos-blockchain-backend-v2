package com.medichain.modules.auth;

import com.medichain.modules.usuario.RolUsuario;
import com.medichain.modules.usuario.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test unitario JwtServiceTest en MediChain.
 * Prueba JwtService en aislamiento (sin Spring): generación, lectura de
 * claims y rechazo de tokens alterados, vencidos o mal formados.
 */
class JwtServiceTest {

    private static final String SECRETO = "secreto-de-prueba-de-al-menos-32-bytes-para-hs256";

    private JwtService jwtService;
    private Usuario usuario;

    /** Arrange común: servicio con secreto de prueba y un usuario con id fijo. */
    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRETO, 8);
        usuario = new Usuario("sede@medichain.local", "hash", "Ana", "Perez", "12345678",
                RolUsuario.SEDE_CENTRAL);
        usuario.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("Genera un token y extrae correctamente sus claims")
    void generaTokenYExtraeClaims() {
        // Act
        String token = jwtService.generarToken(usuario, null, jwtService.calcularVencimiento());
        Claims claims = jwtService.extraerClaims(token);
        // Assert
        assertEquals(usuario.getId().toString(), claims.getSubject());
        assertEquals("sede@medichain.local", claims.get("email", String.class));
        assertEquals("SEDE_CENTRAL", claims.get("rol", String.class));
        assertNull(claims.get("empresaId"), "sin empresa no debe llevar empresaId");
        assertNull(claims.get("provincia"), "si no es inspector no debe llevar provincia");
        assertTrue(jwtService.esValido(token));
    }

    @Test
    @DisplayName("PACIENTE / SEDE: token sin empresaId ni provincia se lee y arma el usuario autenticado")
    void tokenSinEmpresaNiProvinciaArmaUsuarioAutenticado() {
        usuario.setRol(RolUsuario.PACIENTE);
        String token = jwtService.generarToken(usuario, null, jwtService.calcularVencimiento());

        UsuarioAutenticado autenticado = jwtService.aUsuarioAutenticado(jwtService.extraerClaims(token));

        assertEquals(usuario.getId(), autenticado.getUsuarioId());
        assertEquals(RolUsuario.PACIENTE, autenticado.getRol());
        assertNull(autenticado.getEmpresaId());
        assertNull(autenticado.getProvincia());
    }

    @Test
    @DisplayName("INSPECTOR: token con provincia y sin empresaId arma el usuario autenticado")
    void tokenDeInspectorSinEmpresaArmaUsuarioAutenticado() {
        usuario.setRol(RolUsuario.INSPECTOR);
        String token = jwtService.generarToken(usuario, "MENDOZA", jwtService.calcularVencimiento());

        UsuarioAutenticado autenticado = jwtService.aUsuarioAutenticado(jwtService.extraerClaims(token));

        assertEquals(RolUsuario.INSPECTOR, autenticado.getRol());
        assertNull(autenticado.getEmpresaId());
        assertEquals("MENDOZA", autenticado.getProvincia());
    }

    @Test
    @DisplayName("Incluye la provincia cuando se informa (usuario inspector)")
    void incluyeProvinciaParaInspector() {
        String token = jwtService.generarToken(usuario, "CORDOBA", jwtService.calcularVencimiento());
        assertEquals("CORDOBA", jwtService.extraerClaims(token).get("provincia", String.class));
    }

    @Test
    @DisplayName("Rechaza un token con la firma alterada")
    void rechazaTokenConFirmaAlterada() {
        // Arrange: se cambia un carácter en el medio de la firma (tercera parte).
        String token = jwtService.generarToken(usuario, null, jwtService.calcularVencimiento());
        int posicion = token.lastIndexOf('.') + 10;
        char original = token.charAt(posicion);
        char reemplazo = original == 'A' ? 'B' : 'A';
        String alterado = token.substring(0, posicion) + reemplazo + token.substring(posicion + 1);
        // Act + Assert
        assertThrows(JwtException.class, () -> jwtService.extraerClaims(alterado));
        assertFalse(jwtService.esValido(alterado));
    }

    @Test
    @DisplayName("Rechaza un token vencido")
    void rechazaTokenVencido() {
        String token = jwtService.generarToken(usuario, null, Instant.now().minus(1, ChronoUnit.MINUTES));
        assertThrows(ExpiredJwtException.class, () -> jwtService.extraerClaims(token));
        assertFalse(jwtService.esValido(token));
    }

    @Test
    @DisplayName("Rechaza un texto que no es un JWT")
    void rechazaTextoQueNoEsJwt() {
        assertFalse(jwtService.esValido("esto-no-es-un-jwt"));
        assertFalse(jwtService.esValido(""));
    }

    @Test
    @DisplayName("No arranca con un secreto de menos de 256 bits")
    void rechazaSecretoCorto() {
        assertThrows(IllegalStateException.class, () -> new JwtService("corto", 8));
    }
}
