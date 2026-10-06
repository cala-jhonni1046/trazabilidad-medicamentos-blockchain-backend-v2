package com.medichain.modules.registroblockchain;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.web3j.utils.Numeric;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Componente CodigoEsperadoContrato en MediChain (R15).
 * Reconoce si el código desplegado en ANCHOR_CONTRACT_ADDRESS es
 * contracts/MediChainAnchor.sol, en cualquiera de sus dos variantes válidas:
 * compilado con optimizador (el .bin del repo) o sin optimizador (lo que
 * produce Remix si no se tilda Optimization). Funcionan igual; la variante
 * sin optimizador solo cuesta más desplegar.
 * Las variantes vienen del recurso contrato/MediChainAnchor-codigo.json
 * (generado con solc). Para comparar se ignoran:
 * <ul>
 *   <li>los metadatos CBOR del final (cambian con la ruta del archivo y los ajustes de Remix);</li>
 *   <li>los immutables (la dirección del dueño, que el constructor graba en el código).</li>
 * </ul>
 */
@Component
public class CodigoEsperadoContrato {

    /** Recurso con las variantes del código esperado. */
    public static final String RECURSO = "contrato/MediChainAnchor-codigo.json";

    private final List<Variante> variantes;

    /** Carga las variantes del recurso del classpath. */
    public CodigoEsperadoContrato() {
        try (InputStream entrada = new ClassPathResource(RECURSO).getInputStream()) {
            JsonNode raiz = JsonMapper.builder().build().readTree(entrada);
            List<Variante> leidas = new ArrayList<>();
            for (JsonNode nodo : raiz.get("variantes")) {
                List<int[]> inmutables = new ArrayList<>();
                for (JsonNode inmutable : nodo.get("inmutables")) {
                    inmutables.add(new int[] {inmutable.get("inicio").asInt(), inmutable.get("largo").asInt()});
                }
                leidas.add(new Variante(nodo.get("nombre").asString(),
                        Numeric.hexStringToByteArray(nodo.get("runtime").asString()), inmutables));
            }
            this.variantes = List.copyOf(leidas);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + RECURSO, e);
        }
    }

    /**
     * Nombre de la variante que coincide con el código desplegado
     * ("con optimizador (200 runs)" o "sin optimizador"), o vacío si no es
     * MediChainAnchor.sol (otro contrato, otra versión del fuente u otro compilador).
     */
    public Optional<String> varianteDe(String codigoHex) {
        byte[] codigo = sinMetadatos(Numeric.hexStringToByteArray(codigoHex));
        for (Variante variante : variantes) {
            if (variante.coincide(codigo)) {
                return Optional.of(variante.getNombre());
            }
        }
        return Optional.empty();
    }

    /**
     * Quita los metadatos CBOR que solc agrega al final del código: los dos
     * últimos bytes dicen cuánto miden. Si no tienen ese formato, lo deja igual.
     */
    static byte[] sinMetadatos(byte[] codigo) {
        if (codigo.length < 2) {
            return codigo;
        }
        int largo = ((codigo[codigo.length - 2] & 0xff) << 8) | (codigo[codigo.length - 1] & 0xff);
        if (largo + 2 > codigo.length) {
            return codigo;
        }
        return Arrays.copyOf(codigo, codigo.length - 2 - largo);
    }

    /** Una variante compilada: su nombre, el runtime sin metadatos y dónde están los immutables. */
    private static final class Variante {

        private final String nombre;
        private final byte[] runtime;
        private final List<int[]> inmutables;

        Variante(String nombre, byte[] runtime, List<int[]> inmutables) {
            this.nombre = nombre;
            this.runtime = runtime;
            this.inmutables = inmutables;
        }

        String getNombre() {
            return nombre;
        }

        /** true si el código (ya sin metadatos) es este runtime, con cualquier valor en los immutables. */
        boolean coincide(byte[] codigo) {
            if (codigo.length != runtime.length) {
                return false;
            }
            byte[] normalizado = codigo.clone();
            for (int[] inmutable : inmutables) {
                Arrays.fill(normalizado, inmutable[0], inmutable[0] + inmutable[1], (byte) 0);
            }
            return Arrays.equals(normalizado, runtime);
        }
    }
}
