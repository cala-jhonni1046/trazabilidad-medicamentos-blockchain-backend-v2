package com.medichain.modules.registroblockchain;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Propiedades AnclajeProperties en MediChain (R15).
 * Configuración del anclaje en Ethereum Sepolia (prefijo medichain.anclaje).
 * Los tres datos sensibles llegan SOLO por variables de entorno
 * (application.properties): SEPOLIA_RPC_URL, WALLET_PRIVATE_KEY y
 * ANCHOR_CONTRACT_ADDRESS; el interruptor es ANCLAJE_HABILITADO (por
 * defecto false: la app y los tests funcionan sin blockchain).
 * Sin @Validated a propósito: al fallar una validación de propiedades,
 * Spring Boot imprime el valor ("Value: …"). Se valida a mano en
 * validarFormato(), con mensajes que nombran la variable pero NUNCA su valor.
 * Sin toString(): un objeto con secretos no se imprime.
 */
@ConfigurationProperties(prefix = "medichain.anclaje")
public class AnclajeProperties {

    private static final Pattern CLAVE_PRIVADA = Pattern.compile("(0x)?[0-9a-fA-F]{64}");
    private static final Pattern DIRECCION = Pattern.compile("0x[0-9a-fA-F]{40}");
    private static final BigInteger WEI_POR_GWEI = BigInteger.valueOf(1_000_000_000L);

    private boolean habilitado = false;
    private String rpcUrl = "";
    private String clavePrivada = "";
    private String contrato = "";
    private long chainId = 11_155_111L;
    private String red = "sepolia";
    private int confirmaciones = 3;
    private int maxIntentos = 5;
    private long topeGwei = 20L;
    private Duration esperaReemplazo = Duration.ofMinutes(3);
    // Límite de gas de cada anclaje: estimación + 30 % dentro de [gasMinimo; gasMaximo]. Con Glamsterdam
    // (Sepolia, 06/10/2026) el primer anclaje de un contrato cuesta ~353.000 y los siguientes ~154.000.
    private long gasMinimo = 100_000L;
    private long gasMaximo = 500_000L;
    // Freno por saldo de la tarea automática: no ancla si el saldo no alcanza para estos anclajes.
    private int saldoMinimoAnclajes = 10;

    /** Constructor vacío: los valores los completa Spring desde application.properties. */
    public AnclajeProperties() {
    }

    /**
     * Valida el formato de la configuración cuando el anclaje está habilitado.
     * Lanza IllegalStateException (la app no arranca) con un mensaje que nombra
     * las variables con problemas, sin mostrar sus valores.
     */
    public void validarFormato() {
        List<String> problemas = new ArrayList<>();
        if (vacio(rpcUrl)) {
            problemas.add("falta SEPOLIA_RPC_URL");
        } else if (!rpcUrl.trim().startsWith("https://")) {
            problemas.add("SEPOLIA_RPC_URL debe empezar con https://");
        }
        if (vacio(clavePrivada)) {
            problemas.add("falta WALLET_PRIVATE_KEY");
        } else if (!CLAVE_PRIVADA.matcher(clavePrivada.trim()).matches()) {
            problemas.add("WALLET_PRIVATE_KEY no tiene el formato de una clave privada (64 hexadecimales, con o sin 0x)");
        }
        if (vacio(contrato)) {
            problemas.add("falta ANCHOR_CONTRACT_ADDRESS (desplegá el contrato: contracts/GUIA-DESPLIEGUE.md)");
        } else if (!DIRECCION.matcher(contrato.trim()).matches()) {
            problemas.add("ANCHOR_CONTRACT_ADDRESS no es una dirección Ethereum (0x + 40 hexadecimales)");
        }
        if (confirmaciones < 1 || maxIntentos < 1 || topeGwei < 1 || saldoMinimoAnclajes < 1) {
            problemas.add("confirmaciones, max-intentos, tope-gwei y saldo-minimo-anclajes deben ser mayores que cero");
        }
        if (gasMinimo < 21_000 || gasMaximo < gasMinimo) {
            problemas.add("gas-minimo debe ser al menos 21000 y gas-maximo no puede ser menor que gas-minimo");
        }
        if (!problemas.isEmpty()) {
            throw new IllegalStateException("Anclaje habilitado (ANCLAJE_HABILITADO=true) pero la configuración no es válida: "
                    + String.join("; ", problemas) + ".");
        }
    }

    /** Tope de comisión por unidad de gas, en wei. */
    public BigInteger topeWei() {
        return BigInteger.valueOf(topeGwei).multiply(WEI_POR_GWEI);
    }

    /** true si el texto es null o está en blanco. */
    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }

    /** Indica si el anclaje está habilitado (ANCLAJE_HABILITADO). */
    public boolean isHabilitado() {
        return habilitado;
    }

    /** Establece si el anclaje está habilitado. */
    public void setHabilitado(boolean habilitado) {
        this.habilitado = habilitado;
    }

    /** Devuelve la URL del nodo RPC (secreta: lleva la API key). */
    public String getRpcUrl() {
        return rpcUrl;
    }

    /** Establece la URL del nodo RPC. */
    public void setRpcUrl(String rpcUrl) {
        this.rpcUrl = rpcUrl;
    }

    /** Devuelve la clave privada de la billetera (secreta). */
    public String getClavePrivada() {
        return clavePrivada;
    }

    /** Establece la clave privada de la billetera. */
    public void setClavePrivada(String clavePrivada) {
        this.clavePrivada = clavePrivada;
    }

    /** Devuelve la dirección del contrato MediChainAnchor (sin espacios). */
    public String getContrato() {
        return contrato == null ? "" : contrato.trim();
    }

    /** Establece la dirección del contrato MediChainAnchor. */
    public void setContrato(String contrato) {
        this.contrato = contrato;
    }

    /** Devuelve el chainId esperado (Sepolia: 11155111). */
    public long getChainId() {
        return chainId;
    }

    /** Establece el chainId esperado. */
    public void setChainId(long chainId) {
        this.chainId = chainId;
    }

    /** Devuelve el nombre de la red (sepolia), usado en los enlaces a Etherscan. */
    public String getRed() {
        return red;
    }

    /** Establece el nombre de la red. */
    public void setRed(String red) {
        this.red = red;
    }

    /** Devuelve las confirmaciones (bloques encima) necesarias para CONFIRMADO. */
    public int getConfirmaciones() {
        return confirmaciones;
    }

    /** Establece las confirmaciones necesarias. */
    public void setConfirmaciones(int confirmaciones) {
        this.confirmaciones = confirmaciones;
    }

    /** Devuelve la cantidad de intentos de envío antes de FALLIDO. */
    public int getMaxIntentos() {
        return maxIntentos;
    }

    /** Establece la cantidad de intentos de envío antes de FALLIDO. */
    public void setMaxIntentos(int maxIntentos) {
        this.maxIntentos = maxIntentos;
    }

    /** Devuelve el tope de comisión por unidad de gas, en gwei. */
    public long getTopeGwei() {
        return topeGwei;
    }

    /** Establece el tope de comisión por unidad de gas, en gwei. */
    public void setTopeGwei(long topeGwei) {
        this.topeGwei = topeGwei;
    }

    /** Devuelve el piso del límite de gas de un anclaje. */
    public long getGasMinimo() {
        return gasMinimo;
    }

    /** Establece el piso del límite de gas de un anclaje. */
    public void setGasMinimo(long gasMinimo) {
        this.gasMinimo = gasMinimo;
    }

    /** Devuelve el techo del límite de gas: por encima, el anclaje no se envía. */
    public long getGasMaximo() {
        return gasMaximo;
    }

    /** Establece el techo del límite de gas. */
    public void setGasMaximo(long gasMaximo) {
        this.gasMaximo = gasMaximo;
    }

    /** Devuelve para cuántos anclajes tiene que alcanzar el saldo para que la tarea automática ancle. */
    public int getSaldoMinimoAnclajes() {
        return saldoMinimoAnclajes;
    }

    /** Establece para cuántos anclajes tiene que alcanzar el saldo. */
    public void setSaldoMinimoAnclajes(int saldoMinimoAnclajes) {
        this.saldoMinimoAnclajes = saldoMinimoAnclajes;
    }

    /** Devuelve cuánto se espera una transacción sin incluir antes de reemplazarla. */
    public Duration getEsperaReemplazo() {
        return esperaReemplazo;
    }

    /** Establece cuánto se espera una transacción sin incluir antes de reemplazarla. */
    public void setEsperaReemplazo(Duration esperaReemplazo) {
        this.esperaReemplazo = esperaReemplazo;
    }
}
