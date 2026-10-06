package com.medichain.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración OpenApiConfig en MediChain.
 * Declara el esquema de seguridad "bearerAuth" (JWT en el header
 * Authorization) y lo aplica a toda la API, para que Swagger UI muestre
 * el botón "Authorize" y envíe el token en cada request de prueba.
 */
@Configuration
public class OpenApiConfig {

    private static final String ESQUEMA = "bearerAuth";

    /** Definición OpenAPI con el esquema bearerAuth aplicado globalmente. */
    @Bean
    public OpenAPI medichainOpenApi() {
        return new OpenAPI()
                .info(new Info().title("MediChain API").version("v1")
                        .description("Trazabilidad de medicamentos (ANMAT) con anclaje en blockchain"))
                .components(new Components().addSecuritySchemes(ESQUEMA, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Pegá SOLO el token del login, sin la palabra Bearer y sin comillas: "
                                + "Swagger agrega \"Bearer \" por su cuenta.")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA));
    }
}
