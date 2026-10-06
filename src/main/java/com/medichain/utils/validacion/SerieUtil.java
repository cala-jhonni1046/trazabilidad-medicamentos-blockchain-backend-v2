package com.medichain.utils.validacion;

/**
 * Utilidad SerieUtil en MediChain.
 * Valida el formato de una serie de unidad trazable según la regla R3:
 * alfanumérica, de 1 a 20 caracteres, que no empiece con "779" (rango
 * reservado que se usa para probar series inválidas a propósito).
 */
public final class SerieUtil {

    private SerieUtil() {
    }

    /** Indica si la serie cumple el formato de R3. */
    public static boolean esValida(String serie) {
        if (serie == null) {
            return false;
        }
        if (serie.isEmpty() || serie.length() > 20) {
            return false;
        }
        if (!serie.matches("[A-Za-z0-9]+")) {
            return false;
        }
        return !serie.startsWith("779");
    }
}
