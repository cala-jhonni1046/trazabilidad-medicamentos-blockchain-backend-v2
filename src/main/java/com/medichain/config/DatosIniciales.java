package com.medichain.config;

import com.medichain.modules.usuario.RolUsuario;
import com.medichain.modules.usuario.Usuario;
import com.medichain.modules.usuario.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * CommandLineRunner DatosIniciales en MediChain.
 * Al arrancar, si no existe ningún usuario SEDE_CENTRAL, crea uno con
 * ADMIN_EMAIL y ADMIN_PASSWORD (hasheada con BCrypt), para poder hacer
 * el primer login. Sin esas variables de entorno no crea nada y lo
 * avisa en el log. @Order(1): corre antes que DatosDemo, que necesita
 * ese usuario SEDE_CENTRAL para dar de alta al inspector de demo.
 */
@Component
@Order(1)
public class DatosIniciales implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DatosIniciales.class);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    @Autowired
    public DatosIniciales(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                          @Value("${medichain.admin.email}") String adminEmail,
                          @Value("${medichain.admin.password}") String adminPassword) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    /** Crea el usuario SEDE_CENTRAL inicial si todavía no hay ninguno. */
    @Override
    @Transactional
    public void run(String... args) {
        if (usuarioRepository.existsByRol(RolUsuario.SEDE_CENTRAL)) {
            logger.info("Ya existe un usuario SEDE_CENTRAL: no se crea el usuario inicial.");
            return;
        }
        if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            logger.warn("No hay usuario SEDE_CENTRAL y faltan ADMIN_EMAIL/ADMIN_PASSWORD: no se crea el usuario inicial.");
            return;
        }
        // nombre, apellido y dni son obligatorios en Usuario; para la cuenta
        // técnica inicial se usan valores fijos que la Sede puede corregir después.
        Usuario admin = new Usuario(adminEmail, passwordEncoder.encode(adminPassword),
                "Administrador", "Sede Central", "00000000", RolUsuario.SEDE_CENTRAL);
        usuarioRepository.save(admin);
        logger.info("Usuario inicial SEDE_CENTRAL creado: {}", adminEmail);
    }
}
