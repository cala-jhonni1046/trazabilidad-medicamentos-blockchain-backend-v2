package com.medichain.herramientas;

import com.medichain.modules.registroblockchain.ContratoAnchor;
import com.medichain.utils.seguridad.OcultadorSecretos;
import okhttp3.OkHttpClient;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Keys;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Herramienta DesplegadorContrato en MediChain (solo para pruebas).
 * Despliega en Sepolia un contrato MediChainAnchor DESCARTABLE con el
 * bytecode compilado de contracts/MediChainAnchor.bin, para las pruebas
 * e2e --sepolia sobre una base descartable. El contrato PRINCIPAL de la
 * demo se despliega a mano con Remix (contracts/GUIA-DESPLIEGUE.md).
 * Vive en src/test: no forma parte de la aplicación.
 * Lee SEPOLIA_RPC_URL y WALLET_PRIVATE_KEY del entorno y NUNCA los imprime
 * (todo error pasa por OcultadorSecretos). Antes de enviar estima el costo y
 * se niega si supera --max-eth (por defecto 0.002) o si la comisión supera
 * --tope-gwei (por defecto 5). Informa saldo antes y después, gas real,
 * costo y los enlaces a Etherscan.
 * Uso: scripts/desplegar-contrato.sh [--saldo] [--max-eth 0.002] [--tope-gwei 5]
 */
public final class DesplegadorContrato {

    private static final long CHAIN_ID_SEPOLIA = 11_155_111L;
    private static final BigDecimal WEI_POR_ETH = new BigDecimal("1000000000000000000");
    private static final BigDecimal WEI_POR_GWEI = new BigDecimal("1000000000");
    private static final String ETHERSCAN = "https://sepolia.etherscan.io";

    private final Web3j web3j;
    private final Credentials credenciales;
    private final OcultadorSecretos ocultador;

    private DesplegadorContrato(String rpcUrl, String clave) {
        OkHttpClient http = new OkHttpClient.Builder().connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(30)).build();
        this.web3j = Web3j.build(new HttpService(rpcUrl, http));
        this.credenciales = Credentials.create(clave);
        this.ocultador = new OcultadorSecretos(List.of(rpcUrl, clave));
    }

    /** Punto de entrada: valida el entorno, informa el saldo y (si no es --saldo) despliega. */
    public static void main(String[] args) {
        String rpcUrl = System.getenv("SEPOLIA_RPC_URL");
        String clave = System.getenv("WALLET_PRIVATE_KEY");
        if (rpcUrl == null || rpcUrl.isBlank() || clave == null || clave.isBlank()) {
            salir("Faltan SEPOLIA_RPC_URL o WALLET_PRIVATE_KEY en el entorno (set -a; source .env; set +a).");
        }
        if (!clave.trim().matches("(0x)?[0-9a-fA-F]{64}")) {
            salir("WALLET_PRIVATE_KEY no tiene el formato de una clave privada (64 hexadecimales).");
        }
        boolean soloSaldo = List.of(args).contains("--saldo");
        BigDecimal maxEth = new BigDecimal(argumento(args, "--max-eth", "0.002"));
        long topeGwei = Long.parseLong(argumento(args, "--tope-gwei", "5"));
        DesplegadorContrato desplegador = new DesplegadorContrato(rpcUrl.trim(), clave.trim());
        try {
            desplegador.ejecutar(soloSaldo, maxEth, topeGwei);
        } catch (IOException | RuntimeException e) {
            salir("Error: " + desplegador.ocultador.ocultar(e.getClass().getSimpleName() + ": " + e.getMessage()));
        } finally {
            desplegador.web3j.shutdown();
        }
    }

    /** Informa la billetera y, salvo --saldo, despliega el contrato. */
    private void ejecutar(boolean soloSaldo, BigDecimal maxEth, long topeGwei) throws IOException {
        long chainId = exigir(web3j.ethChainId().send()).getChainId().longValueExact();
        if (chainId != CHAIN_ID_SEPOLIA) {
            salir("El RPC apunta a la red con chainId " + chainId + ", no a Sepolia (11155111). No se despliega.");
        }
        String billetera = Keys.toChecksumAddress(credenciales.getAddress());
        BigInteger saldoAntes = saldo();
        System.out.println("Billetera: " + billetera + "  (" + ETHERSCAN + "/address/" + billetera + ")");
        System.out.println("Saldo: " + eth(saldoAntes) + " SepoliaETH");
        if (soloSaldo) {
            return;
        }

        String bytecode = "0x" + Files.readString(Path.of("contracts/MediChainAnchor.bin")).trim();
        BigInteger gasEstimado = exigir(web3j.ethEstimateGas(Transaction.createContractTransaction(
                credenciales.getAddress(), null, null, bytecode)).send()).getAmountUsed();
        BigInteger base = exigir(web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send())
                .getBlock().getBaseFeePerGas();
        BigInteger propina = exigir(web3j.ethMaxPriorityFeePerGas().send()).getMaxPriorityFeePerGas();
        BigInteger tope = BigInteger.valueOf(topeGwei).multiply(BigInteger.valueOf(1_000_000_000L));
        if (base.add(propina).compareTo(tope) > 0) {
            salir("La comisión de la red (" + gwei(base.add(propina)) + " gwei) supera el tope de " + topeGwei
                    + " gwei. No se despliega.");
        }
        BigInteger maxima = base.shiftLeft(1).add(propina).min(tope);
        BigInteger limiteGas = gasEstimado.multiply(BigInteger.valueOf(120)).divide(BigInteger.valueOf(100));
        BigDecimal costoEsperado = new BigDecimal(gasEstimado.multiply(base.add(propina))).divide(WEI_POR_ETH);
        BigDecimal costoMaximo = new BigDecimal(limiteGas.multiply(maxima)).divide(WEI_POR_ETH);
        System.out.println("Gas estimado: " + gasEstimado + "  comisión actual: " + gwei(base.add(propina))
                + " gwei  costo esperado: " + costoEsperado.setScale(8, RoundingMode.UP).toPlainString()
                + " ETH (máximo posible " + costoMaximo.setScale(8, RoundingMode.UP).toPlainString() + ")");
        if (costoMaximo.compareTo(maxEth) > 0) {
            salir("El costo máximo posible supera --max-eth " + maxEth.toPlainString() + ". No se despliega.");
        }

        BigInteger nonce = exigir(web3j.ethGetTransactionCount(credenciales.getAddress(),
                DefaultBlockParameterName.LATEST).send()).getTransactionCount();
        RawTransaction creacion = RawTransaction.createTransaction(CHAIN_ID_SEPOLIA, nonce, limiteGas, "",
                BigInteger.ZERO, bytecode, propina, maxima);
        String firmada = Numeric.toHexString(TransactionEncoder.signMessage(creacion, credenciales));
        EthSendTransaction envio = exigir(web3j.ethSendRawTransaction(firmada).send());
        String hashTransaccion = envio.getTransactionHash();
        System.out.println("Transacción enviada: " + ETHERSCAN + "/tx/" + hashTransaccion);

        TransactionReceipt recibo = esperarRecibo(hashTransaccion);
        if (!recibo.isStatusOK()) {
            salir("El despliegue falló (status " + recibo.getStatus() + ").");
        }
        String contrato = Keys.toChecksumAddress(recibo.getContractAddress());
        BigInteger precio = Numeric.decodeQuantity(recibo.getEffectiveGasPrice());
        BigInteger costo = recibo.getGasUsed().multiply(precio);
        System.out.println("Contrato desplegado: " + contrato);
        System.out.println("  " + ETHERSCAN + "/address/" + contrato);
        System.out.println("  bloque " + recibo.getBlockNumber() + ", gas usado " + recibo.getGasUsed() + ", precio "
                + gwei(precio) + " gwei, costo " + eth(costo) + " ETH");
        String duenio = leer(contrato, ContratoAnchor.duenio()).get(0).getValue().toString();
        String ultimo = leer(contrato, ContratoAnchor.ultimoNumero()).get(0).getValue().toString();
        System.out.println("  duenio() = " + Keys.toChecksumAddress(duenio)
                + (duenio.equalsIgnoreCase(credenciales.getAddress()) ? " (la billetera)" : " (¡NO es la billetera!)")
                + ", ultimoNumero() = " + ultimo);
        System.out.println("Saldo después: " + eth(saldo()) + " SepoliaETH (antes " + eth(saldoAntes) + ")");
        System.out.println("ANCHOR_CONTRACT_ADDRESS=" + contrato);
    }

    /** Espera el recibo (cada 3 s, hasta 4 minutos). */
    private TransactionReceipt esperarRecibo(String hashTransaccion) throws IOException {
        for (int i = 0; i < 80; i++) {
            Optional<TransactionReceipt> recibo = exigir(web3j.ethGetTransactionReceipt(hashTransaccion).send())
                    .getTransactionReceipt();
            if (recibo.isPresent()) {
                return recibo.get();
            }
            try {
                Thread.sleep(3_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                salir("Interrumpido esperando el recibo de " + hashTransaccion);
            }
        }
        salir("La transacción no se incluyó en 4 minutos: " + ETHERSCAN + "/tx/" + hashTransaccion);
        return null;
    }

    /** eth_call a una función de lectura del contrato recién desplegado. */
    private List<Type> leer(String contrato, Function funcion) throws IOException {
        String valor = exigir(web3j.ethCall(Transaction.createEthCallTransaction(credenciales.getAddress(), contrato,
                FunctionEncoder.encode(funcion)), DefaultBlockParameterName.LATEST).send()).getValue();
        return FunctionReturnDecoder.decode(valor, funcion.getOutputParameters());
    }

    /** Saldo de la billetera en wei. */
    private BigInteger saldo() throws IOException {
        return exigir(web3j.ethGetBalance(credenciales.getAddress(), DefaultBlockParameterName.LATEST).send())
                .getBalance();
    }

    /** Exige que la respuesta JSON-RPC no traiga error (mensaje saneado). */
    private <T extends Response<?>> T exigir(T respuesta) {
        if (respuesta.hasError()) {
            salir("El nodo respondió con un error: " + ocultador.ocultar(respuesta.getError().getMessage()));
        }
        return respuesta;
    }

    /** Valor de un argumento --nombre valor, o el valor por defecto. */
    private static String argumento(String[] args, String nombre, String defecto) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(nombre)) {
                return args[i + 1];
            }
        }
        return defecto;
    }

    /** Wei → ETH con 8 decimales. */
    private static String eth(BigInteger wei) {
        return new BigDecimal(wei).divide(WEI_POR_ETH, 8, RoundingMode.DOWN).toPlainString();
    }

    /** Wei → gwei con 3 decimales. */
    private static String gwei(BigInteger wei) {
        return new BigDecimal(wei).divide(WEI_POR_GWEI, 3, RoundingMode.HALF_UP).toPlainString();
    }

    /** Termina con un mensaje (ya saneado) y código 1. */
    private static void salir(String mensaje) {
        System.err.println(mensaje);
        System.exit(1);
    }
}
