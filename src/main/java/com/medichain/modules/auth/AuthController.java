package com.medichain.modules.auth;

import com.medichain.config.RespuestasError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador AuthController en MediChain.
 * Expone el login público. Devuelve 200 con el JWT o 401 con
 * "Credenciales inválidas".
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticación", description = "Login y emisión de JWT")
public class AuthController {

    private final AuthService authService;

    @Autowired
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** Autentica con email y contraseña y devuelve el JWT. */
    @PostMapping("/login")
    @Operation(operationId = "iniciarSesion", summary = "Iniciar sesión", description = "Devuelve un JWT válido por 8 horas. Público: no necesita token.")
    @RespuestasError({400, 401})
    @SecurityRequirements
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.OK).body(authService.login(dto));
    }
}
