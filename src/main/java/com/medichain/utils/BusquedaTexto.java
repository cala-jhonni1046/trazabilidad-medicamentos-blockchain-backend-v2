package com.medichain.utils;

/**
 * Utilidad BusquedaTexto en MediChain.
 * Arma el patrón LIKE de las búsquedas por código o serie de los listados
 * (?codigo= en lotes y bultos, ?serie= en cajas): "contiene", sin distinguir
 * mayúsculas, con los comodines de LIKE escapados (lo que escribe la persona se
 * busca tal cual). Sin texto devuelve "%" (todo): así la consulta no necesita
 * un parámetro nulo, que PostgreSQL no sabe tipar dentro de lower(...).
 * La consulta lo usa como {@code lower(x.campo) like :patron escape '\'}.
 */
public final class BusquedaTexto {

    /** Largo máximo del texto buscado (el del campo más largo que se busca: 20 la serie, 12 el código de lote). */
    public static final int MAXIMO = 50;

    private BusquedaTexto() {
    }

    /** Patrón LIKE para "contiene texto" (minúsculas, comodines escapados); "%" si el texto es nulo o está en blanco. */
    public static String patron(String texto) {
        if (texto == null || texto.isBlank()) {
            return "%";
        }
        String limpio = texto.trim().toLowerCase(java.util.Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + limpio + "%";
    }
}
