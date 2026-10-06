package com.medichain.utils.validacion;

/**
 * Utilidad DniUtil en MediChain (R13, Ley 25.326).
 * El DNI del paciente es opcional en la dispensación y NUNCA se guarda,
 * se loguea ni se devuelve completo: solo enmascarado, con los últimos 3
 * dígitos visibles (30111006 → *****006). No se guarda un hash: con apenas
 * 10^8 combinaciones se revierte por fuerza bruta.
 */
public final class DniUtil {

    private static final int DIGITOS_VISIBLES = 3;

    private DniUtil() {
    }

    /** Indica si el texto es un DNI válido: 7 u 8 dígitos (los DNI antiguos tienen 7). */
    public static boolean esValido(String dni) {
        return dni != null && dni.matches("\\d{7,8}");
    }

    /** Devuelve el DNI enmascarado (solo los últimos 3 dígitos), o null si no se informó. */
    public static String enmascarar(String dni) {
        if (dni == null || dni.isBlank()) {
            return null;
        }
        if (!esValido(dni)) {
            throw new IllegalArgumentException("DNI inválido");
        }
        return "*".repeat(dni.length() - DIGITOS_VISIBLES) + dni.substring(dni.length() - DIGITOS_VISIBLES);
    }
}
