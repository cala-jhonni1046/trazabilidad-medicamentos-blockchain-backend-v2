package com.medichain.modules.registroblockchain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.AbiTypes;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test unitario ContratoAnchorAbiTest en MediChain.
 * Las funciones que ContratoAnchor arma a mano coinciden con el ABI que
 * genera el compilador (contracts/MediChainAnchor.abi.json): nombre, tipos
 * de entrada y de salida, y selector. Si alguien cambia el contrato y no
 * el Java (o al revés), este test falla.
 */
class ContratoAnchorAbiTest {

    /** Firma → tipos de salida, leídos del ABI del compilador. */
    private Map<String, List<String>> funcionesDelAbi() {
        JsonNode abi = JsonMapper.builder().build().readTree(Path.of("contracts/MediChainAnchor.abi.json").toFile());
        Map<String, List<String>> funciones = new HashMap<>();
        for (JsonNode elemento : abi) {
            if (!"function".equals(elemento.get("type").asString())) {
                continue;
            }
            funciones.put(firma(elemento), tipos(elemento.get("outputs")));
        }
        return funciones;
    }

    /** Firmas de los errores del ABI. */
    private List<String> erroresDelAbi() {
        JsonNode abi = JsonMapper.builder().build().readTree(Path.of("contracts/MediChainAnchor.abi.json").toFile());
        List<String> errores = new ArrayList<>();
        for (JsonNode elemento : abi) {
            if ("error".equals(elemento.get("type").asString())) {
                errores.add(firma(elemento));
            }
        }
        return errores;
    }

    /** nombre(tipo1,tipo2) de un elemento del ABI. */
    private String firma(JsonNode elemento) {
        return elemento.get("name").asString() + "(" + String.join(",", tipos(elemento.get("inputs"))) + ")";
    }

    /** Tipos Solidity de una lista de parámetros del ABI. */
    private List<String> tipos(JsonNode parametros) {
        List<String> tipos = new ArrayList<>();
        for (JsonNode parametro : parametros) {
            tipos.add(parametro.get("type").asString());
        }
        return tipos;
    }

    /** Firma de una Function de web3j: nombre(tipos de entrada). */
    private String firma(Function funcion) {
        List<String> tipos = new ArrayList<>();
        for (Type<?> parametro : funcion.getInputParameters()) {
            tipos.add(parametro.getTypeAsString());
        }
        return funcion.getName() + "(" + String.join(",", tipos) + ")";
    }

    /** Tipos Solidity de las salidas de una Function de web3j. */
    private List<String> salidas(Function funcion) {
        List<String> tipos = new ArrayList<>();
        for (TypeReference<Type> salida : funcion.getOutputParameters()) {
            java.lang.reflect.Type tipo = salida.getType();
            if (tipo instanceof ParameterizedType arreglo) {
                // DynamicArray<X> → "x[]"
                tipos.add(AbiTypes.getTypeAString(claseAbi(arreglo.getActualTypeArguments()[0])) + "[]");
            } else {
                tipos.add(AbiTypes.getTypeAString(claseAbi(tipo)));
            }
        }
        return tipos;
    }

    /** Convierte un tipo reflejado en la clase de web3j que representa un tipo del ABI. */
    @SuppressWarnings("unchecked")
    private Class<? extends Type> claseAbi(java.lang.reflect.Type tipo) {
        return (Class<? extends Type>) tipo;
    }

    @Test
    @DisplayName("Cada función de ContratoAnchor existe en el ABI con los mismos tipos de entrada y de salida")
    void funcionesCoincidenConElAbi() {
        Map<String, List<String>> abi = funcionesDelAbi();
        List<Function> funciones = List.of(ContratoAnchor.anclar("ab".repeat(32), 1), ContratoAnchor.ultimoNumero(),
                ContratoAnchor.hashAnclado(1), ContratoAnchor.duenio(), ContratoAnchor.cantidadAnclajes(),
                ContratoAnchor.anclajes(0, 500));
        List<String> firmasEsperadas = List.of(ContratoAnchor.FIRMA_ANCLAR, ContratoAnchor.FIRMA_ULTIMO_NUMERO,
                ContratoAnchor.FIRMA_HASH_ANCLADO, ContratoAnchor.FIRMA_DUENIO, ContratoAnchor.FIRMA_CANTIDAD_ANCLAJES,
                ContratoAnchor.FIRMA_ANCLAJES);

        for (int i = 0; i < funciones.size(); i++) {
            Function funcion = funciones.get(i);
            String firma = firma(funcion);
            assertEquals(firmasEsperadas.get(i), firma);
            assertTrue(abi.containsKey(firma), "el ABI no tiene " + firma);
            assertEquals(abi.get(firma), salidas(funcion), "salidas de " + firma);
            assertTrue(FunctionEncoder.encode(funcion).startsWith(ContratoAnchor.selector(firma)));
        }
    }

    @Test
    @DisplayName("Los errores con nombre que traduce ContratoAnchor existen en el ABI")
    void erroresCoincidenConElAbi() {
        assertTrue(erroresDelAbi().containsAll(ContratoAnchor.FIRMAS_ERRORES), erroresDelAbi().toString());
    }

    @Test
    @DisplayName("El bytecode compilado (contracts/MediChainAnchor.bin) es hexadecimal y contiene el selector de anclar")
    void bytecodeCompilado() throws IOException {
        String bytecode = Files.readString(Path.of("contracts/MediChainAnchor.bin")).trim();
        assertTrue(bytecode.matches("[0-9a-f]+"));
        assertTrue(bytecode.contains(ContratoAnchor.selector(ContratoAnchor.FIRMA_ANCLAR).substring(2)));
    }

    @Test
    @DisplayName("describirError traduce NumeroNoCreciente con sus valores")
    void describeErrores() {
        String datos = ContratoAnchor.selector("NumeroNoCreciente(uint64,uint64)")
                + "0".repeat(63) + "5" + "0".repeat(63) + "9";
        assertEquals("NumeroNoCreciente(recibido 5, último anclado 9)", ContratoAnchor.describirError(datos));
        assertEquals("rechazo del contrato", ContratoAnchor.describirError("0x12345678"));
    }
}
