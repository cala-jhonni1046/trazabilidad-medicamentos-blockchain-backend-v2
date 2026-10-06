package com.medichain.modules.trazabilidad;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Utilidad HashUtil en MediChain.
 * SHA-256 en hexadecimal minúscula (64 caracteres, sin prefijo "0x": lo
 * agrega el anclaje en Sepolia). El texto siempre se convierte a bytes
 * con UTF-8 de forma explícita, para no depender del charset del sistema.
 */
public final class HashUtil {

    private HashUtil() {
    }

    /** Devuelve el SHA-256 del texto (codificado en UTF-8) en 64 caracteres hex minúscula. */
    public static String sha256Hex(String texto) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(texto.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 es estándar en toda JVM; si falta, es un error de entorno irrecuperable.
            throw new IllegalStateException("Algoritmo SHA-256 no disponible en esta JVM", e);
        }
    }

    /**
     * Calcula el seriesHash de un lote (regla A3, para LOTE_REGISTRADO en
     * el paso 7): SHA-256 de las series ordenadas lexicográficamente y
     * unidas por "\n". El evento no lleva la lista de series, solo
     * cantidadSeries y este hash, que permite verificar el conjunto exacto.
     */
    public static String seriesHash(Collection<String> series) {
        List<String> ordenadas = new ArrayList<>(series);
        ordenadas.sort(null);
        return sha256Hex(String.join("\n", ordenadas));
    }
}
