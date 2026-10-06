package com.medichain.testutil;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Utilidad de prueba RelojMovible en MediChain.
 * Reloj en UTC que solo avanza cuando el test lo pide: permite probar
 * esperas (reintentos, reemplazo de una transacción trabada) sin dormir.
 */
public class RelojMovible extends Clock {

    private Instant ahora;

    /** Crea el reloj detenido en el instante dado. */
    public RelojMovible(Instant inicio) {
        this.ahora = inicio;
    }

    /** Avanza el reloj. */
    public void avanzar(Duration duracion) {
        this.ahora = this.ahora.plus(duracion);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zona) {
        return this;
    }

    @Override
    public Instant instant() {
        return ahora;
    }
}
