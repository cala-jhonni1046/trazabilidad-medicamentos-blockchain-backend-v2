package com.medichain.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.validation.autoconfigure.ValidationConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;
import java.time.ZoneId;

/**
 * Configuración ZonaHorariaConfig en MediChain.
 * Zona del calendario del negocio (medichain.zona-horaria): define qué día es
 * "hoy" (lote vencido, R10; fechas del recorrido). La misma zona la usa Bean
 * Validation: @Future y @PastOrPresent de las fechas sin hora (fabricación y
 * vencimiento de un lote) se evalúan con el calendario de Argentina.
 * Las fechas y horas se guardan y se devuelven en UTC.
 */
@Configuration
public class ZonaHorariaConfig {

    /** Zona horaria del calendario del negocio. */
    @Bean
    public ZoneId zonaHoraria(@Value("${medichain.zona-horaria}") String zona) {
        return ZoneId.of(zona);
    }

    /** Bean Validation usa el reloj de la app con la zona del negocio para @Future / @PastOrPresent. */
    @Bean
    public ValidationConfigurationCustomizer relojDeValidacion(Clock reloj, ZoneId zonaHoraria) {
        return configuracion -> configuracion.clockProvider(() -> reloj.withZone(zonaHoraria));
    }
}
