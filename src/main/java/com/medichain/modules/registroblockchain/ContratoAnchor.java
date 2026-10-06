package com.medichain.modules.registroblockchain;

import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint64;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

/**
 * ABI ContratoAnchor en MediChain.
 * Las funciones del contrato contracts/MediChainAnchor.sol escritas a mano
 * con los tipos de web3j (sin clases generadas por un plugin): es explícito
 * y se puede revisar línea por línea. ContratoAnchorAbiTest comprueba que
 * estas definiciones coinciden con contracts/MediChainAnchor.abi.json.
 * También traduce los errores con nombre del contrato (revert) a texto.
 */
public final class ContratoAnchor {

    /** Firmas (nombre y tipos) de las funciones que usa MediChain. */
    public static final String FIRMA_ANCLAR = "anclar(bytes32,uint64)";
    public static final String FIRMA_ULTIMO_NUMERO = "ultimoNumero()";
    public static final String FIRMA_HASH_ANCLADO = "hashAnclado(uint64)";
    public static final String FIRMA_DUENIO = "duenio()";
    public static final String FIRMA_CANTIDAD_ANCLAJES = "cantidadAnclajes()";
    public static final String FIRMA_ANCLAJES = "anclajes(uint256,uint256)";

    /** Errores con nombre del contrato (firma completa, para calcular su selector). */
    public static final List<String> FIRMAS_ERRORES = List.of(
            "NoEsElDuenio(address)", "NumeroNoCreciente(uint64,uint64)", "HashVacio()");

    private ContratoAnchor() {
    }

    /** anclar(bytes32 hash, uint64 hastaNumero): la única función que escribe. */
    public static Function anclar(String hashHex, long hastaNumero) {
        return new Function("anclar",
                Arrays.<Type>asList(new Bytes32(bytes32(hashHex)), new Uint64(BigInteger.valueOf(hastaNumero))),
                List.of());
    }

    /** ultimoNumero() → uint64. */
    public static Function ultimoNumero() {
        return new Function("ultimoNumero", List.of(), List.of(new TypeReference<Uint64>() { }));
    }

    /** hashAnclado(uint64 numero) → bytes32. */
    public static Function hashAnclado(long numero) {
        return new Function("hashAnclado", Arrays.<Type>asList(new Uint64(BigInteger.valueOf(numero))),
                List.of(new TypeReference<Bytes32>() { }));
    }

    /** duenio() → address. */
    public static Function duenio() {
        return new Function("duenio", List.of(), List.of(new TypeReference<Address>() { }));
    }

    /** cantidadAnclajes() → uint256. */
    public static Function cantidadAnclajes() {
        return new Function("cantidadAnclajes", List.of(), List.of(new TypeReference<Uint256>() { }));
    }

    /** anclajes(uint256 desde, uint256 cantidad) → (uint64[] numeros, bytes32[] hashes). */
    public static Function anclajes(long desde, long cantidad) {
        return new Function("anclajes",
                Arrays.<Type>asList(new Uint256(BigInteger.valueOf(desde)), new Uint256(BigInteger.valueOf(cantidad))),
                List.of(new TypeReference<DynamicArray<Uint64>>() { }, new TypeReference<DynamicArray<Bytes32>>() { }));
    }

    /** Convierte un hash de 64 hexadecimales (con o sin 0x) en los 32 bytes de un bytes32. */
    public static byte[] bytes32(String hashHex) {
        byte[] bytes = Numeric.hexStringToByteArray(hashHex);
        if (bytes.length != 32) {
            throw new IllegalArgumentException("El hash a anclar debe tener 32 bytes (64 hexadecimales)");
        }
        return bytes;
    }

    /** Convierte un bytes32 en 64 hexadecimales en minúscula sin 0x, o null si son todos ceros. */
    public static String hexDeBytes32(byte[] valor) {
        boolean todoCeros = true;
        for (byte b : valor) {
            if (b != 0) {
                todoCeros = false;
                break;
            }
        }
        return todoCeros ? null : Numeric.toHexStringNoPrefix(valor);
    }

    /**
     * Traduce los datos de un revert (selector de 4 bytes + argumentos) a un
     * texto legible, por ejemplo "NumeroNoCreciente(recibido 85, último 90)".
     * Si no es un error conocido devuelve "rechazo del contrato".
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static String describirError(String datosRevert) {
        if (datosRevert == null || datosRevert.length() < 10) {
            return "rechazo del contrato";
        }
        String selector = datosRevert.substring(0, 10).toLowerCase();
        String argumentos = datosRevert.substring(10);
        if (selector.equals(selector("NumeroNoCreciente(uint64,uint64)"))) {
            try {
                List<Type> valores = FunctionReturnDecoder.decode(argumentos,
                        (List) List.of(new TypeReference<Uint64>() { }, new TypeReference<Uint64>() { }));
                return "NumeroNoCreciente(recibido " + valores.get(0).getValue()
                        + ", último anclado " + valores.get(1).getValue() + ")";
            } catch (RuntimeException e) {
                return "NumeroNoCreciente";
            }
        }
        if (selector.equals(selector("NoEsElDuenio(address)"))) {
            return "NoEsElDuenio (la billetera configurada no es la dueña del contrato)";
        }
        if (selector.equals(selector("HashVacio()"))) {
            return "HashVacio";
        }
        return "rechazo del contrato";
    }

    /** Selector de 4 bytes (0x + 8 hexadecimales) de una firma de función o error. */
    public static String selector(String firma) {
        return Hash.sha3String(firma).substring(0, 10);
    }
}
