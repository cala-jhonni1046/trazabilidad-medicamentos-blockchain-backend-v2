package com.medichain.modules.registroblockchain;

import com.medichain.utils.seguridad.OcultadorSecretos;
import okhttp3.OkHttpClient;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint64;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Keys;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthEstimateGas;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;
import java.io.IOException;
import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementación ClienteBlockchainWeb3j en MediChain (R15).
 * Habla con Sepolia por JSON-RPC usando web3j: lee el contrato
 * MediChainAnchor (eth_call), firma la transacción anclar(...) con la
 * clave de la billetera (EIP-1559, chainId incluido en la firma) y la
 * transmite (eth_sendRawTransaction).
 * Seguridad:
 * <ul>
 *   <li>Cliente HTTP propio SIN interceptor de log: el cliente por defecto
 *       de web3j loguea URL y cuerpo en DEBUG, y la URL lleva la API key.</li>
 *   <li>Todo error pasa por OcultadorSecretos antes de salir de esta clase.</li>
 *   <li>La clave vive solo dentro de Credentials; nunca se imprime.</li>
 * </ul>
 */
public class ClienteBlockchainWeb3j implements ClienteBlockchain {

    /** Datos de un revert dentro del error del nodo: 0x + selector de 4 bytes + argumentos. */
    private static final Pattern DATOS_HEX = Pattern.compile("0x[0-9a-fA-F]{8,}");

    private final Web3j web3j;
    private final Credentials credenciales;
    private final String contrato;
    private final long chainId;
    private final OcultadorSecretos ocultador;

    /** Crea el cliente real a partir de la configuración (ya validada en formato). */
    public ClienteBlockchainWeb3j(AnclajeProperties propiedades) {
        this(Web3j.build(new HttpService(propiedades.getRpcUrl().trim(), clienteHttp())),
                Credentials.create(propiedades.getClavePrivada().trim()),
                propiedades.getContrato(), propiedades.getChainId(),
                new OcultadorSecretos(List.of(propiedades.getRpcUrl(), propiedades.getClavePrivada())));
    }

    /** Crea el cliente con sus partes (lo usan los tests con un Web3jService simulado). */
    public ClienteBlockchainWeb3j(Web3j web3j, Credentials credenciales, String contrato, long chainId,
                                  OcultadorSecretos ocultador) {
        this.web3j = web3j;
        this.credenciales = credenciales;
        this.contrato = contrato;
        this.chainId = chainId;
        this.ocultador = ocultador;
    }

    /** Cliente HTTP con tiempos máximos (el hilo del anclaje nunca queda colgado) y sin log. */
    private static OkHttpClient clienteHttp() {
        return new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(20))
                .writeTimeout(Duration.ofSeconds(20))
                .build();
    }

    @Override
    public long chainId() {
        return enviar(web3j.ethChainId(), "consultar el chainId").getChainId().longValueExact();
    }

    @Override
    public String direccionBilletera() {
        return Keys.toChecksumAddress(credenciales.getAddress());
    }

    @Override
    public BigInteger saldoWei() {
        return enviar(web3j.ethGetBalance(credenciales.getAddress(), DefaultBlockParameterName.LATEST),
                "consultar el saldo").getBalance();
    }

    @Override
    public String codigoDelContrato() {
        String codigo = enviar(web3j.ethGetCode(contrato, DefaultBlockParameterName.LATEST),
                "consultar el código del contrato").getCode();
        return codigo == null || codigo.isBlank() ? "0x" : codigo;
    }

    @Override
    public String duenioDelContrato() {
        return ((Address) llamar(ContratoAnchor.duenio()).get(0)).getValue().toLowerCase();
    }

    @Override
    public long ultimoNumeroAnclado() {
        return ((Uint64) llamar(ContratoAnchor.ultimoNumero()).get(0)).getValue().longValueExact();
    }

    @Override
    public String hashAnclado(long numero) {
        return ContratoAnchor.hexDeBytes32(((Bytes32) llamar(ContratoAnchor.hashAnclado(numero)).get(0)).getValue());
    }

    @Override
    public long cantidadAnclajes() {
        return ((Uint256) llamar(ContratoAnchor.cantidadAnclajes()).get(0)).getValue().longValueExact();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<AnclajeEnContrato> anclajes(long desde, int cantidad) {
        List<Type> salida = llamar(ContratoAnchor.anclajes(desde, cantidad));
        List<Uint64> numeros = ((DynamicArray<Uint64>) salida.get(0)).getValue();
        List<Bytes32> hashes = ((DynamicArray<Bytes32>) salida.get(1)).getValue();
        List<AnclajeEnContrato> anclajes = new ArrayList<>();
        for (int i = 0; i < numeros.size(); i++) {
            anclajes.add(new AnclajeEnContrato(numeros.get(i).getValue().longValueExact(),
                    ContratoAnchor.hexDeBytes32(hashes.get(i).getValue())));
        }
        return anclajes;
    }

    @Override
    public long ultimoBloque() {
        return enviar(web3j.ethBlockNumber(), "consultar el último bloque").getBlockNumber().longValueExact();
    }

    @Override
    public ComisionesRed comisiones() {
        BigInteger base = enviar(web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false),
                "consultar la comisión base").getBlock().getBaseFeePerGas();
        BigInteger propina = enviar(web3j.ethMaxPriorityFeePerGas(), "consultar la propina sugerida")
                .getMaxPriorityFeePerGas();
        return new ComisionesRed(base, propina);
    }

    @Override
    public BigInteger nonce() {
        return enviar(web3j.ethGetTransactionCount(credenciales.getAddress(), DefaultBlockParameterName.LATEST),
                "consultar el nonce").getTransactionCount();
    }

    @Override
    public void simularAnclaje(String hash, long hastaNumero, BigInteger gasMaximo) {
        Transaction llamada = Transaction.createFunctionCallTransaction(credenciales.getAddress(), null, null,
                gasMaximo, contrato, FunctionEncoder.encode(ContratoAnchor.anclar(hash, hastaNumero)));
        EthCall respuesta = ejecutar(web3j.ethCall(llamada, DefaultBlockParameterName.LATEST), "simular el anclaje");
        if (respuesta.hasError()) {
            String mensaje = String.valueOf(respuesta.getError().getMessage());
            Matcher revert = DATOS_HEX.matcher(String.valueOf(respuesta.getError().getData()));
            if (revert.find()) {
                throw new ErrorBlockchainException("El contrato rechazaría el anclaje: "
                        + ContratoAnchor.describirError(revert.group()), CausaFallo.REVERT);
            }
            String minuscula = mensaje.toLowerCase();
            if (minuscula.contains("out of gas") || minuscula.contains("gas required exceeds")) {
                // Con gasMaximo como límite la simulación se quedó sin gas: enviarla fallaría y cobraría todo.
                throw new ErrorBlockchainException("El anclaje necesita más de " + gasMaximo + " de gas (máximo "
                        + "configurado, medichain.anclaje.gas-maximo): no se envió.", CausaFallo.GAS_SOBRE_EL_MAXIMO);
            }
            if (minuscula.contains("revert")) {
                throw new ErrorBlockchainException("El contrato rechazaría el anclaje: " + ocultador.ocultar(mensaje),
                        CausaFallo.REVERT);
            }
            throw new ErrorBlockchainException("No se pudo simular el anclaje: " + ocultador.ocultar(mensaje));
        }
        if (respuesta.isReverted()) {
            throw new ErrorBlockchainException("El contrato rechazaría el anclaje: "
                    + ocultador.ocultar(String.valueOf(respuesta.getRevertReason())), CausaFallo.REVERT);
        }
    }

    @Override
    public BigInteger estimarGasAnclaje(String hash, long hastaNumero) {
        Transaction llamada = Transaction.createEthCallTransaction(credenciales.getAddress(), contrato,
                FunctionEncoder.encode(ContratoAnchor.anclar(hash, hastaNumero)));
        EthEstimateGas respuesta = ejecutar(web3j.ethEstimateGas(llamada), "estimar el gas del anclaje");
        if (respuesta.hasError()) {
            Matcher revert = DATOS_HEX.matcher(String.valueOf(respuesta.getError().getData()));
            if (revert.find()) {
                // El nodo simuló la transacción y el contrato la rechazó (revert con un error con nombre).
                throw new ErrorBlockchainException("El contrato rechazaría el anclaje: "
                        + ContratoAnchor.describirError(revert.group()), CausaFallo.REVERT);
            }
            throw new ErrorBlockchainException("No se pudo estimar el gas del anclaje: "
                    + ocultador.ocultar(respuesta.getError().getMessage()));
        }
        return respuesta.getAmountUsed();
    }

    @Override
    public TransaccionFirmada firmarAnclaje(String hash, long hastaNumero, BigInteger nonce, BigInteger limiteGas,
                                            BigInteger comisionMaximaWei, BigInteger propinaWei) {
        String datos = FunctionEncoder.encode(ContratoAnchor.anclar(hash, hastaNumero));
        RawTransaction transaccion = RawTransaction.createTransaction(chainId, nonce, limiteGas, contrato,
                BigInteger.ZERO, datos, propinaWei, comisionMaximaWei);
        String hexFirmado = Numeric.toHexString(TransactionEncoder.signMessage(transaccion, credenciales));
        // El hash de la transacción es keccak256 de los bytes firmados: se conoce ANTES de transmitirla.
        return new TransaccionFirmada(Hash.sha3(hexFirmado), hexFirmado, nonce, limiteGas, comisionMaximaWei,
                propinaWei);
    }

    @Override
    public void transmitir(TransaccionFirmada transaccion) {
        EthSendTransaction respuesta = ejecutar(web3j.ethSendRawTransaction(transaccion.getHexFirmado()),
                "transmitir la transacción");
        if (respuesta.hasError()) {
            String mensaje = respuesta.getError().getMessage() == null ? "" : respuesta.getError().getMessage();
            String minuscula = mensaje.toLowerCase();
            // Mismos bytes ya transmitidos (reintento o reinicio a mitad de camino): no es un error.
            if (minuscula.contains("already known") || minuscula.contains("known transaction")) {
                return;
            }
            throw new ErrorBlockchainException("El nodo rechazó la transacción: " + ocultador.ocultar(mensaje));
        }
    }

    @Override
    public Optional<ReciboTransaccion> recibo(String hashTransaccion) {
        Optional<TransactionReceipt> recibo = enviar(web3j.ethGetTransactionReceipt(hashTransaccion),
                "consultar el recibo").getTransactionReceipt();
        return recibo.map(r -> new ReciboTransaccion(r.getBlockNumber().longValueExact(), r.isStatusOK(),
                r.getGasUsed().longValueExact(),
                r.getEffectiveGasPrice() == null ? 0L : Numeric.decodeQuantity(r.getEffectiveGasPrice()).longValueExact()));
    }

    @Override
    public boolean transaccionConocida(String hashTransaccion) {
        return enviar(web3j.ethGetTransactionByHash(hashTransaccion), "consultar la transacción")
                .getTransaction().isPresent();
    }

    @Override
    public void cerrar() {
        web3j.shutdown();
    }

    /** eth_call a una función de lectura del contrato; decodifica la salida. */
    private List<Type> llamar(Function funcion) {
        Transaction llamada = Transaction.createEthCallTransaction(credenciales.getAddress(), contrato,
                FunctionEncoder.encode(funcion));
        EthCall respuesta = enviar(web3j.ethCall(llamada, DefaultBlockParameterName.LATEST),
                "leer " + funcion.getName() + "() del contrato");
        if (respuesta.isReverted() || respuesta.getValue() == null || respuesta.getValue().equals("0x")) {
            throw new ErrorBlockchainException("El contrato no respondió " + funcion.getName()
                    + "(): ¿la dirección es la de MediChainAnchor?");
        }
        return FunctionReturnDecoder.decode(respuesta.getValue(), funcion.getOutputParameters());
    }

    /** Ejecuta un pedido y exige que no traiga error JSON-RPC. */
    private <T extends Response<?>> T enviar(Request<?, T> pedido, String operacion) {
        T respuesta = ejecutar(pedido, operacion);
        if (respuesta.hasError()) {
            throw new ErrorBlockchainException("No se pudo " + operacion + ": "
                    + ocultador.ocultar(respuesta.getError().getMessage()));
        }
        return respuesta;
    }

    /** Ejecuta un pedido; los errores de red salen como ErrorBlockchainException saneada. */
    private <T extends Response<?>> T ejecutar(Request<?, T> pedido, String operacion) {
        try {
            return pedido.send();
        } catch (IOException | RuntimeException e) {
            throw new ErrorBlockchainException("No se pudo " + operacion + " (" + e.getClass().getSimpleName() + "): "
                    + ocultador.ocultar(String.valueOf(e.getMessage())));
        }
    }
}
