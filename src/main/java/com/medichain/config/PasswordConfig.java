package com.medichain.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Configuración PasswordConfig en MediChain.
 * Expone el PasswordEncoder que usa toda la aplicación para hashear
 * contraseñas (BCrypt): nunca se guarda ni se compara una contraseña en
 * texto plano. BCrypt genera un salt aleatorio distinto por cada
 * contraseña y lo embebe en el propio hash de salida, así que no hace
 * falta guardarlo aparte.
 */
@Configuration
public class PasswordConfig {

    /** Bean del PasswordEncoder (BCrypt) usado por UsuarioService al crear cuentas. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
