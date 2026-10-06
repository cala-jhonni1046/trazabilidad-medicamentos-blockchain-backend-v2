package com.medichain.utils.validacion;

/**
 * Utilidad Gs1Util en MediChain.
 * Valida el dígito verificador GS1 (pesos 3 y 1 alternados, empezando
 * por 3 en el dígito inmediatamente a la izquierda del verificador y
 * yendo hacia la izquierda). Sirve para cualquier código GS1 numérico
 * (GTIN de 14 dígitos, GLN de 13 dígitos, etc.); la longitud exacta la
 * controla cada anotación específica (@Gtin, @Gln).
 */
public final class Gs1Util {

    private Gs1Util() {
    }

    /** Indica si el código (todo dígitos, con verificador incluido) cumple el algoritmo GS1. */
    public static boolean esValido(String codigo) {
        if (codigo == null || codigo.length() < 2 || !codigo.matches("\\d+")) {
            return false;
        }
        int digitoVerificador = codigo.charAt(codigo.length() - 1) - '0';
        String cuerpo = codigo.substring(0, codigo.length() - 1);
        int suma = 0;
        boolean pesoTres = true;
        for (int i = cuerpo.length() - 1; i >= 0; i--) {
            int digito = cuerpo.charAt(i) - '0';
            suma += digito * (pesoTres ? 3 : 1);
            pesoTres = !pesoTres;
        }
        int digitoEsperado = (10 - (suma % 10)) % 10;
        return digitoEsperado == digitoVerificador;
    }

    /**
     * Normaliza un código GS1 (GTIN o GLN) dejando solo los dígitos, sin
     * espacios ni ningún otro separador, para que se guarde siempre en
     * el mismo formato.
     */
    public static String normalizar(String codigo) {
        if (codigo == null) {
            return null;
        }
        return codigo.replaceAll("[^0-9]", "");
    }
}
