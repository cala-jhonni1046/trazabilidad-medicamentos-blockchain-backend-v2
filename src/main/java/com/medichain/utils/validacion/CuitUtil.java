package com.medichain.utils.validacion;

/**
 * Utilidad CuitUtil en MediChain.
 * Valida el dígito verificador de un CUIT argentino (algoritmo módulo 11
 * con pesos 5,4,3,2,7,6,5,4,3,2 sobre los primeros 10 dígitos).
 */
public final class CuitUtil {

    private static final int[] PESOS = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};

    private CuitUtil() {
    }

    /**
     * Indica si el CUIT es válido: acepta guiones (se ignoran), exige 11
     * dígitos y verifica el dígito verificador módulo 11. Un resto de 0
     * da dígito esperado 0 (11 - 0 = 11 se mapea a 0); un resto que da
     * dígito esperado 10 es inválido (no existe CUIT posible).
     */
    public static boolean esValido(String cuit) {
        if (cuit == null) {
            return false;
        }
        String limpio = cuit.replace("-", "").trim();
        if (!limpio.matches("\\d{11}")) {
            return false;
        }
        int suma = 0;
        for (int i = 0; i < 10; i++) {
            suma += (limpio.charAt(i) - '0') * PESOS[i];
        }
        int digitoVerificador = 11 - (suma % 11);
        if (digitoVerificador == 11) {
            digitoVerificador = 0;
        } else if (digitoVerificador == 10) {
            return false;
        }
        int ultimoDigito = limpio.charAt(10) - '0';
        return digitoVerificador == ultimoDigito;
    }

    /**
     * Normaliza un CUIT al formato canónico "XX-XXXXXXXX-X", sin importar
     * si llegó con o sin guiones, para que la restricción unique de la
     * columna detecte duplicados sin importar el formato de entrada. Si
     * el valor no tiene 11 dígitos (no debería pasar si ya se validó con
     * @Cuit), lo devuelve sin guiones tal como llegó, limpio de guiones.
     */
    public static String normalizar(String cuit) {
        if (cuit == null) {
            return null;
        }
        String limpio = cuit.replace("-", "").trim();
        if (!limpio.matches("\\d{11}")) {
            return limpio;
        }
        return limpio.substring(0, 2) + "-" + limpio.substring(2, 10) + "-" + limpio.substring(10);
    }
}
