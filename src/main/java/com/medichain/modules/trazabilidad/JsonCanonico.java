package com.medichain.modules.trazabilidad;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Serializador JsonCanonico en MediChain.
 * Escribe JSON canónico a mano (sin ObjectMapper), para que el mismo dato
 * produzca SIEMPRE el mismo texto y por lo tanto el mismo hash, aunque
 * cambie la versión o la configuración de Jackson. Reglas:
 * <ul>
 *   <li>Claves ordenadas lexicográficamente (también en los mapas anidados), sin espacios.</li>
 *   <li>Los valores null se OMITEN (la clave no aparece).</li>
 *   <li>Instant: UTC, truncado a microsegundos, "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'".</li>
 *   <li>LocalDateTime: truncado a microsegundos, "yyyy-MM-dd'T'HH:mm:ss.SSSSSS" (sin zona).</li>
 *   <li>LocalDate: "yyyy-MM-dd".</li>
 *   <li>BigDecimal: string con stripTrailingZeros().toPlainString() (5.50 → "5.5").</li>
 *   <li>Integer y Long: número JSON. Boolean: true/false. UUID: string en minúscula.</li>
 *   <li>Enum: su name() como string. Colecciones: arreglo JSON en el orden recibido.</li>
 *   <li>Strings: se escapan solo comillas, barra invertida y caracteres de control.</li>
 *   <li>JsonCrudo: se inserta tal cual (para reutilizar un JSON ya canónico).</li>
 * </ul>
 * Cualquier otro tipo se rechaza: los datos de un evento deben ser
 * explícitos (lista blanca), nunca objetos arbitrarios.
 */
public final class JsonCanonico {

    private static final DateTimeFormatter FORMATO_INSTANT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter FORMATO_LOCAL_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS");
    private static final DateTimeFormatter FORMATO_LOCAL_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private JsonCanonico() {
    }

    /**
     * Valor que ya es JSON canónico y se inserta sin volver a procesar
     * (por ejemplo, los "datos" guardados de un evento).
     */
    public static final class JsonCrudo {

        private final String json;

        /** Envuelve un texto que ya es JSON canónico. */
        public JsonCrudo(String json) {
            this.json = json;
        }

        /** Devuelve el JSON envuelto. */
        public String getJson() {
            return json;
        }
    }

    /** Escribe el mapa como JSON canónico (claves ordenadas, sin nulls, sin espacios). */
    public static String escribir(Map<String, ?> mapa) {
        StringBuilder salida = new StringBuilder();
        escribirMapa(mapa, salida);
        return salida.toString();
    }

    /** Escribe un mapa con sus claves ordenadas y omitiendo los valores null. */
    private static void escribirMapa(Map<String, ?> mapa, StringBuilder salida) {
        Map<String, Object> ordenado = new TreeMap<>(mapa);
        salida.append('{');
        boolean primero = true;
        for (Map.Entry<String, Object> entrada : ordenado.entrySet()) {
            if (entrada.getValue() == null) {
                continue;
            }
            if (!primero) {
                salida.append(',');
            }
            escribirTexto(entrada.getKey(), salida);
            salida.append(':');
            escribirValor(entrada.getValue(), salida);
            primero = false;
        }
        salida.append('}');
    }

    /** Escribe un valor según su tipo; los tipos no previstos se rechazan. */
    @SuppressWarnings("unchecked")
    private static void escribirValor(Object valor, StringBuilder salida) {
        if (valor instanceof JsonCrudo crudo) {
            salida.append(crudo.getJson());
        } else if (valor instanceof String texto) {
            escribirTexto(texto, salida);
        } else if (valor instanceof Integer || valor instanceof Long) {
            salida.append(valor);
        } else if (valor instanceof BigDecimal decimal) {
            escribirTexto(decimal.stripTrailingZeros().toPlainString(), salida);
        } else if (valor instanceof Boolean booleano) {
            salida.append(booleano ? "true" : "false");
        } else if (valor instanceof UUID uuid) {
            escribirTexto(uuid.toString().toLowerCase(), salida);
        } else if (valor instanceof Instant instante) {
            escribirTexto(FORMATO_INSTANT.format(instante.truncatedTo(ChronoUnit.MICROS)), salida);
        } else if (valor instanceof LocalDateTime fechaHora) {
            escribirTexto(FORMATO_LOCAL_DATE_TIME.format(fechaHora.truncatedTo(ChronoUnit.MICROS)), salida);
        } else if (valor instanceof LocalDate fecha) {
            escribirTexto(FORMATO_LOCAL_DATE.format(fecha), salida);
        } else if (valor instanceof Enum<?> enumerado) {
            escribirTexto(enumerado.name(), salida);
        } else if (valor instanceof Map<?, ?> mapa) {
            escribirMapa((Map<String, ?>) mapa, salida);
        } else if (valor instanceof Collection<?> coleccion) {
            salida.append('[');
            boolean primero = true;
            for (Object elemento : coleccion) {
                if (!primero) {
                    salida.append(',');
                }
                escribirValor(elemento, salida);
                primero = false;
            }
            salida.append(']');
        } else {
            throw new IllegalArgumentException("Tipo no admitido en JSON canónico: " + valor.getClass().getName());
        }
    }

    /** Escribe un string escapando solo comillas, barra invertida y caracteres de control. */
    private static void escribirTexto(String texto, StringBuilder salida) {
        salida.append('"');
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            switch (c) {
                case '"' -> salida.append("\\\"");
                case '\\' -> salida.append("\\\\");
                case '\n' -> salida.append("\\n");
                case '\r' -> salida.append("\\r");
                case '\t' -> salida.append("\\t");
                case '\b' -> salida.append("\\b");
                case '\f' -> salida.append("\\f");
                default -> {
                    if (c < 0x20) {
                        salida.append(String.format("\\u%04x", (int) c));
                    } else {
                        salida.append(c);
                    }
                }
            }
        }
        salida.append('"');
    }
}
