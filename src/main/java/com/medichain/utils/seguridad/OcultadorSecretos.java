package com.medichain.utils.seguridad;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Utilidad OcultadorSecretos en MediChain.
 * Reemplaza por "***" toda aparición de un valor secreto en un texto que va
 * al log, a la base (por ejemplo RegistroBlockchain.ultimoError) o a una
 * respuesta. Se usa con la URL del RPC (lleva la API key de Alchemy en la
 * ruta) y la clave privada de la billetera: cualquier mensaje de error que
 * venga de la red o de web3j pasa por acá antes de salir.
 * Además del valor completo oculta sus variantes: la clave con y sin "0x"
 * (y en minúscula), la URL sin el esquema, su ruta y su último segmento.
 */
public final class OcultadorSecretos {

    /** Texto que reemplaza a cada secreto. */
    public static final String OCULTO = "***";

    /** Largo mínimo de una variante: un pedazo muy corto podría tapar texto común. */
    private static final int LARGO_MINIMO = 8;

    private final List<String> secretos;

    /** Crea el ocultador para los valores dados (los null o vacíos se ignoran). */
    public OcultadorSecretos(Collection<String> valores) {
        List<String> variantes = new ArrayList<>();
        for (String valor : valores) {
            agregarVariantes(valor, variantes);
        }
        // Primero las más largas: así una variante corta no deja restos de una larga.
        variantes.sort(Comparator.comparingInt(String::length).reversed());
        this.secretos = List.copyOf(variantes);
    }

    /** Devuelve el texto con cada secreto reemplazado por "***" (null si el texto es null). */
    public String ocultar(String texto) {
        if (texto == null) {
            return null;
        }
        String resultado = texto;
        for (String secreto : secretos) {
            resultado = resultado.replace(secreto, OCULTO);
        }
        return resultado;
    }

    /** Agrega el valor y sus variantes (clave sin 0x, URL sin esquema, ruta y último segmento). */
    private static void agregarVariantes(String valor, List<String> variantes) {
        if (valor == null || valor.isBlank()) {
            return;
        }
        String limpio = valor.trim();
        agregar(limpio, variantes);
        agregar(limpio.toLowerCase(), variantes);
        if (limpio.startsWith("0x") || limpio.startsWith("0X")) {
            agregar(limpio.substring(2), variantes);
            agregar(limpio.substring(2).toLowerCase(), variantes);
        }
        int esquema = limpio.indexOf("://");
        if (esquema > 0) {
            String sinEsquema = limpio.substring(esquema + 3);
            agregar(sinEsquema, variantes);
            int barra = sinEsquema.indexOf('/');
            if (barra >= 0) {
                String ruta = sinEsquema.substring(barra);
                agregar(ruta, variantes);
                agregar(ruta.substring(ruta.lastIndexOf('/') + 1), variantes);
            }
        }
    }

    /** Agrega una variante si es suficientemente larga y no está repetida. */
    private static void agregar(String variante, List<String> variantes) {
        if (variante.length() >= LARGO_MINIMO && !variantes.contains(variante)) {
            variantes.add(variante);
        }
    }
}
