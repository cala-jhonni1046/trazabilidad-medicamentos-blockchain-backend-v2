package com.medichain.utils.seguridad;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test unitario OcultadorSecretosTest en MediChain.
 * La URL del RPC (con su API key) y la clave privada no sobreviven en
 * ningún texto, en ninguna de sus variantes.
 */
class OcultadorSecretosTest {

    private static final String URL = "https://eth-sepolia.g.alchemy.com/v2/ClaveDeApiDePrueba123";
    private static final String CLAVE = "0x4C0883A69102937D6231471B5DBB6204FE5129617082792AE468D01A3F362318";

    private final OcultadorSecretos ocultador = new OcultadorSecretos(List.of(URL, CLAVE));

    @Test
    @DisplayName("La URL completa, sin esquema, su ruta o solo la API key → ***")
    void ocultaLaUrlYSusVariantes() {
        String texto = "Fallo al conectar con " + URL + " (host eth-sepolia.g.alchemy.com/v2/ClaveDeApiDePrueba123)"
                + " ruta /v2/ClaveDeApiDePrueba123 y key ClaveDeApiDePrueba123";

        String oculto = ocultador.ocultar(texto);

        assertFalse(oculto.contains("ClaveDeApiDePrueba123"), oculto);
        assertTrue(oculto.contains(OcultadorSecretos.OCULTO));
        assertTrue(oculto.startsWith("Fallo al conectar con "), "el resto del mensaje se conserva");
    }

    @Test
    @DisplayName("La clave privada con 0x, sin 0x o en minúscula → ***")
    void ocultaLaClave() {
        String sinPrefijo = CLAVE.substring(2);
        String texto = "clave " + CLAVE + " / " + sinPrefijo + " / " + sinPrefijo.toLowerCase();

        String oculto = ocultador.ocultar(texto);

        assertFalse(oculto.toLowerCase().contains(sinPrefijo.toLowerCase()), oculto);
    }

    @Test
    @DisplayName("Valores vacíos o null se ignoran; un texto null queda null")
    void valoresVacios() {
        OcultadorSecretos vacio = new OcultadorSecretos(Arrays.asList(null, "", "  "));
        assertEquals("sin cambios", vacio.ocultar("sin cambios"));
        assertNull(ocultador.ocultar(null));
    }
}
