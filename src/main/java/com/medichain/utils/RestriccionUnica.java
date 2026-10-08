package com.medichain.utils;

import org.springframework.dao.DataIntegrityViolationException;

/**
 * Utilidad RestriccionUnica en MediChain.
 * Reconoce qué restricción única (índice de PostgreSQL) violó una
 * operación. Sirve para traducir a su regla de negocio una CARRERA entre dos
 * altas simultáneas: las dos pasan la validación del Service, pero la base
 * deja entrar solo a una y la otra llega como DataIntegrityViolationException.
 * Ejemplos: ux_circuito_par_vigente → R5, ux_lote_laboratorio_codigo →
 * LOTE_DUPLICADO, ux_unidad_gtin_serie → R3.
 * Busca el nombre en la causa de Hibernate (getConstraintName) y, si no está,
 * en los mensajes de la cadena de causas (PostgreSQL lo incluye:
 * "duplicate key value violates unique constraint \"…\"").
 */
public final class RestriccionUnica {

    private RestriccionUnica() {
    }

    /** true si la excepción es una violación de la restricción única con ese nombre. */
    public static boolean es(DataIntegrityViolationException excepcion, String nombreRestriccion) {
        Throwable causa = excepcion;
        while (causa != null) {
            if (causa instanceof org.hibernate.exception.ConstraintViolationException violacion
                    && violacion.getConstraintName() != null
                    && violacion.getConstraintName().equalsIgnoreCase(nombreRestriccion)) {
                return true;
            }
            if (causa.getMessage() != null && causa.getMessage().contains(nombreRestriccion)) {
                return true;
            }
            causa = causa.getCause() == causa ? null : causa.getCause();
        }
        return false;
    }
}
