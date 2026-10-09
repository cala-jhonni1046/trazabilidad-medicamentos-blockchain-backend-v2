package com.medichain.utils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Utilidad Tiempo en MediChain.
 * El instante actual en UTC, truncado a microsegundos (la precisión de
 * timestamptz en PostgreSQL): el valor en memoria es el mismo que se relee
 * de la base. Lo usan las entidades para sus fechas y horas de negocio
 * (salida de un viaje, recepción, dispensación…). Para "hoy" en el
 * calendario de Argentina, ver Calendario.
 */
public final class Tiempo {

    private Tiempo() {
    }

    /** Instante actual en UTC, en microsegundos. */
    public static Instant ahora() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
