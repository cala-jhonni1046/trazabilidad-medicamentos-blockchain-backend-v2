package com.medichain.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Componente Calendario en MediChain.
 * Las fechas (sin hora) del negocio se leen en el calendario de Argentina
 * (medichain.zona-horaria, por defecto America/Argentina/Buenos_Aires: el
 * sistema es nacional): "hoy" para decidir si un lote venció (R10) y la fecha
 * de cada etapa del recorrido en la verificación pública. Las horas se
 * guardan y se devuelven siempre en UTC; solo el día depende de la zona.
 * Sin esto, un lote que vence hoy figuraba vencido desde las 21:00 de
 * Argentina (ya es mañana en UTC).
 */
@Component
public class Calendario {

    private final Clock reloj;
    private final ZoneId zona;

    @Autowired
    public Calendario(Clock reloj, ZoneId zonaHoraria) {
        this.reloj = reloj;
        this.zona = zonaHoraria;
    }

    /** La fecha de hoy en Argentina. */
    public LocalDate hoy() {
        return LocalDate.now(reloj.withZone(zona));
    }

    /** La fecha (en Argentina) de un instante; null si el instante es null. */
    public LocalDate fecha(Instant instante) {
        return instante != null ? LocalDate.ofInstant(instante, zona) : null;
    }

    /** La zona horaria del calendario. */
    public ZoneId getZona() {
        return zona;
    }
}
