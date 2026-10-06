package com.medichain.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuración ProgramacionConfig en MediChain.
 * Habilita las tareas programadas (@Scheduled). Hoy la única es
 * TareaAnclaje, que solo existe con ANCLAJE_HABILITADO=true. Corren en el
 * hilo del scheduler, nunca en el de una petición de negocio.
 */
@Configuration
@EnableScheduling
public class ProgramacionConfig {
}
