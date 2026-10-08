package com.medichain.utils;

import org.springframework.dao.DataIntegrityViolationException;
import java.sql.SQLException;

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

    /**
     * Nombre de la restricción violada, tal como lo informa Hibernate
     * (getConstraintName), o null si no lo informa. Nunca devuelve el valor
     * duplicado: sirve para loguear sin datos personales.
     */
    public static String nombre(DataIntegrityViolationException excepcion) {
        Throwable causa = excepcion;
        while (causa != null) {
            if (causa instanceof org.hibernate.exception.ConstraintViolationException violacion
                    && violacion.getConstraintName() != null) {
                return violacion.getConstraintName();
            }
            causa = causa.getCause() == causa ? null : causa.getCause();
        }
        return null;
    }

    /**
     * SQLState de la violación: 23505 única, 23503 clave foránea, 23514 CHECK,
     * 23502 NOT NULL. null si no viene de un SQLException.
     */
    public static String estadoSql(DataIntegrityViolationException excepcion) {
        Throwable causa = excepcion;
        while (causa != null) {
            if (causa instanceof SQLException sql && sql.getSQLState() != null) {
                SQLException siguiente = sql.getNextException();
                return siguiente != null && siguiente.getSQLState() != null ? siguiente.getSQLState() : sql.getSQLState();
            }
            causa = causa.getCause() == causa ? null : causa.getCause();
        }
        return null;
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
