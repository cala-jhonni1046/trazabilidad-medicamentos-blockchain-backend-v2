package com.medichain.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Anotación RespuestasError en MediChain.
 * Declara, en cada operación de un controller, los códigos HTTP de error que
 * puede devolver (400, 401, 403, 404, 409, 503). OpenApiConfig la convierte en
 * respuestas documentadas en el contrato, todas con el esquema ErrorResponseDTO
 * y una descripción fija por código. Así cada endpoint lista sus errores de
 * forma explícita sin repetir una @ApiResponse larga por código.
 * Criterios (los controla ContratoOpenApiIT): 400 si recibe datos (body, path,
 * query o paginación); 401 si no es pública; 403 si restringe por rol; 404 si
 * tiene {id} o referencia otro recurso; 409 en las escrituras; 503 solo si
 * consulta la blockchain en vivo.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RespuestasError {

    /** Códigos HTTP de error que puede devolver la operación. */
    int[] value();
}
