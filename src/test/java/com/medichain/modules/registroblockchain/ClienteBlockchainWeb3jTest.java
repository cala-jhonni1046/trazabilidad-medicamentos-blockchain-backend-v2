package com.medichain.modules.registroblockchain;

import com.medichain.utils.seguridad.OcultadorSecretos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.SignedRawTransaction;
import org.web3j.crypto.TransactionDecoder;
import org.web3j.crypto.transaction.type.Transaction1559;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.Web3jService;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthEstimateGas;
import org.web3j.protocol.core.methods.response.EthGetCode;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Test unitario ClienteBlockchainWeb3jTest en MediChain (R15).
 * Con el transporte JSON-RPC simulado (Web3jService de Mockito, sin red):
 * la transacción firmada se decodifica con el chainId de Sepolia, el
 * contrato, el selector de anclar y el firmante correcto; un revert se
 * traduce; los errores salen saneados (sin URL ni clave).
 * La clave es la de ejemplo de la documentación de web3 (pública, sin fondos).
 */
@ExtendWith(MockitoExtension.class)
class ClienteBlockchainWeb3jTest {

    private static final String CLAVE_DE_PRUEBA = "4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318";
    private static final String URL = "https://eth-sepolia.g.alchemy.com/v2/ClaveDeApiDePrueba123";
    private static final String CONTRATO = "0x1111111111111111111111111111111111111111";
    private static final String HASH = "ab".repeat(32);

    @Mock
    private Web3jService servicio;

    private ClienteBlockchainWeb3j cliente;
    private Credentials credenciales;

    @BeforeEach
    void setUp() {
        credenciales = Credentials.create(CLAVE_DE_PRUEBA);
        cliente = new ClienteBlockchainWeb3j(Web3j.build(servicio), credenciales, CONTRATO, 11_155_111L,
                new OcultadorSecretos(List.of(URL, CLAVE_DE_PRUEBA)));
    }

    @Test
    @DisplayName("Firma EIP-1559: chainId Sepolia, contrato, nonce, datos de anclar(hash, n), firmante = billetera, hash local")
    void firmaLaTransaccion() throws Exception {
        TransaccionFirmada firmada = cliente.firmarAnclaje(HASH, 28, BigInteger.valueOf(7), BigInteger.valueOf(80_000),
                BigInteger.valueOf(2_100_000_000L), BigInteger.valueOf(100_000_000L));

        RawTransaction decodificada = TransactionDecoder.decode(firmada.getHexFirmado());
        assertInstanceOf(SignedRawTransaction.class, decodificada);
        assertEquals(11_155_111L, ((Transaction1559) decodificada.getTransaction()).getChainId());
        assertEquals(CONTRATO, decodificada.getTo());
        assertEquals(BigInteger.valueOf(7), decodificada.getNonce());
        assertEquals(BigInteger.valueOf(80_000), decodificada.getGasLimit());
        String datos = decodificada.getData().replace("0x", "");
        assertTrue(datos.startsWith(ContratoAnchor.selector(ContratoAnchor.FIRMA_ANCLAR).substring(2)));
        assertTrue(datos.contains(HASH), "el hash a anclar va en los datos");
        assertTrue(datos.endsWith("000000000000001c"), "hastaNumero 28 = 0x1c");
        assertEquals(credenciales.getAddress(), ((SignedRawTransaction) decodificada).getFrom());
        assertEquals(Hash.sha3(firmada.getHexFirmado()), firmada.getHash());
    }

    @Test
    @DisplayName("Firmar dos veces con los mismos parámetros da los mismos bytes (firma determinística)")
    void firmaDeterministica() {
        TransaccionFirmada una = cliente.firmarAnclaje(HASH, 28, BigInteger.ONE, BigInteger.TEN, BigInteger.TEN, BigInteger.ONE);
        TransaccionFirmada otra = cliente.firmarAnclaje(HASH, 28, BigInteger.ONE, BigInteger.TEN, BigInteger.TEN, BigInteger.ONE);
        assertEquals(una.getHash(), otra.getHash());
    }

    @Test
    @DisplayName("Estimar gas con revert NumeroNoCreciente → rechazo del contrato traducido, sin gastar nada")
    void estimacionConRevert() throws IOException {
        EthEstimateGas respuesta = new EthEstimateGas();
        Response.Error error = new Response.Error(3, "execution reverted");
        error.setData(ContratoAnchor.selector("NumeroNoCreciente(uint64,uint64)")
                + "0".repeat(62) + "1c" + "0".repeat(62) + "54");
        respuesta.setError(error);
        when(servicio.send(any(), eq(EthEstimateGas.class))).thenReturn(respuesta);

        ErrorBlockchainException e = assertThrows(ErrorBlockchainException.class,
                () -> cliente.estimarGasAnclaje(HASH, 28));

        assertEquals(CausaFallo.REVERT, e.getCausa());
        assertTrue(e.getMessage().contains("NumeroNoCreciente(recibido 28, último anclado 84)"), e.getMessage());
    }

    @Test
    @DisplayName("Un error de red con la URL y la clave en el mensaje sale saneado")
    void errorSaneado() throws IOException {
        when(servicio.send(any(), eq(EthSendTransaction.class)))
                .thenThrow(new IOException("Failed to connect to " + URL + " with key " + CLAVE_DE_PRUEBA));
        TransaccionFirmada firmada = cliente.firmarAnclaje(HASH, 28, BigInteger.ONE, BigInteger.TEN, BigInteger.TEN,
                BigInteger.ONE);

        ErrorBlockchainException e = assertThrows(ErrorBlockchainException.class, () -> cliente.transmitir(firmada));

        assertFalse(e.getMessage().contains("ClaveDeApiDePrueba123"), e.getMessage());
        assertFalse(e.getMessage().contains(CLAVE_DE_PRUEBA), e.getMessage());
        assertNull(e.getCausa(), "un error de red se reintenta: no tiene causa determinística");
    }

    @Test
    @DisplayName("Transmitir: 'already known' (mismos bytes) no es error; otro rechazo del nodo sí")
    void transmitir() throws IOException {
        TransaccionFirmada firmada = cliente.firmarAnclaje(HASH, 28, BigInteger.ONE, BigInteger.TEN, BigInteger.TEN,
                BigInteger.ONE);
        EthSendTransaction yaConocida = new EthSendTransaction();
        yaConocida.setError(new Response.Error(-32000, "already known"));
        EthSendTransaction sinFondos = new EthSendTransaction();
        sinFondos.setError(new Response.Error(-32000, "insufficient funds for gas * price + value"));
        when(servicio.send(any(), eq(EthSendTransaction.class))).thenReturn(yaConocida, sinFondos);

        assertDoesNotThrow(() -> cliente.transmitir(firmada));
        ErrorBlockchainException e = assertThrows(ErrorBlockchainException.class, () -> cliente.transmitir(firmada));
        assertTrue(e.getMessage().contains("insufficient funds"));
    }

    @Test
    @DisplayName("Recibo: bloque, status, gas usado y precio efectivo")
    void recibo() throws IOException {
        TransactionReceipt recibo = new TransactionReceipt();
        recibo.setBlockNumber("0x895440");
        recibo.setStatus("0x1");
        recibo.setGasUsed("0xef0e");
        recibo.setEffectiveGasPrice("0x47868c00");
        EthGetTransactionReceipt respuesta = new EthGetTransactionReceipt();
        respuesta.setResult(recibo);
        when(servicio.send(any(), eq(EthGetTransactionReceipt.class))).thenReturn(respuesta);

        ReciboTransaccion leido = cliente.recibo("0x" + "cd".repeat(32)).orElseThrow();

        assertEquals(9_000_000L, leido.getBloque());
        assertTrue(leido.isExitosa());
        assertEquals(61_198L, leido.getGasUsado());
        assertEquals(1_200_000_000L, leido.getPrecioEfectivoWei());
    }

    @Test
    @DisplayName("Lecturas del contrato: ultimoNumero y hashAnclado (0x00…00 → null)")
    void lecturasDelContrato() throws IOException {
        EthCall ultimo = new EthCall();
        ultimo.setResult("0x" + "0".repeat(62) + "54");
        EthCall vacio = new EthCall();
        vacio.setResult("0x" + "0".repeat(64));
        when(servicio.send(any(), eq(EthCall.class))).thenReturn(ultimo, vacio);

        assertEquals(84L, cliente.ultimoNumeroAnclado());
        assertEquals(null, cliente.hashAnclado(85));
    }

    // ---------- Simulación antes de enviar (corrección del out of gas del 06/10/2026) ----------

    @Test
    @DisplayName("Simular con gas-maximo de límite: 'out of gas' (como 0xB126… con 300.000) → GAS_SOBRE_EL_MAXIMO, sin enviar")
    void simulacionSinGas() throws IOException {
        EthCall respuesta = new EthCall();
        respuesta.setError(new Response.Error(-32003, "out of gas: gas required exceeds: 300000"));
        when(servicio.send(any(), eq(EthCall.class))).thenReturn(respuesta);

        ErrorBlockchainException e = assertThrows(ErrorBlockchainException.class,
                () -> cliente.simularAnclaje(HASH, 28, BigInteger.valueOf(300_000)));

        assertEquals(CausaFallo.GAS_SOBRE_EL_MAXIMO, e.getCausa());
        assertTrue(e.getMessage().contains("más de 300000 de gas"), e.getMessage());
    }

    @Test
    @DisplayName("Simular: revert con NumeroNoCreciente → REVERT con el error decodificado")
    void simulacionConRevert() throws IOException {
        EthCall respuesta = new EthCall();
        Response.Error error = new Response.Error(3, "execution reverted");
        error.setData(ContratoAnchor.selector("NumeroNoCreciente(uint64,uint64)")
                + "0".repeat(62) + "1c" + "0".repeat(62) + "54");
        respuesta.setError(error);
        when(servicio.send(any(), eq(EthCall.class))).thenReturn(respuesta);

        ErrorBlockchainException e = assertThrows(ErrorBlockchainException.class,
                () -> cliente.simularAnclaje(HASH, 28, BigInteger.valueOf(500_000)));

        assertEquals(CausaFallo.REVERT, e.getCausa());
        assertTrue(e.getMessage().contains("NumeroNoCreciente(recibido 28, último anclado 84)"), e.getMessage());
    }

    @Test
    @DisplayName("Simular: la llamada pasa → no lanza; un error de red → causa null (se reintenta)")
    void simulacionCorrectaYErrorDeRed() throws IOException {
        EthCall correcta = new EthCall();
        correcta.setResult("0x");
        EthCall caida = new EthCall();
        caida.setError(new Response.Error(-32000, "header not found"));
        when(servicio.send(any(), eq(EthCall.class))).thenReturn(correcta, caida);

        assertDoesNotThrow(() -> cliente.simularAnclaje(HASH, 28, BigInteger.valueOf(500_000)));
        ErrorBlockchainException e = assertThrows(ErrorBlockchainException.class,
                () -> cliente.simularAnclaje(HASH, 28, BigInteger.valueOf(500_000)));
        assertNull(e.getCausa());
    }

    @Test
    @DisplayName("Código del contrato: el que devuelve eth_getCode, o \"0x\" si no hay contrato")
    void codigoDelContrato() throws IOException {
        EthGetCode conCodigo = new EthGetCode();
        conCodigo.setResult("0x6080");
        EthGetCode sinCodigo = new EthGetCode();
        sinCodigo.setResult("0x");
        when(servicio.send(any(), eq(EthGetCode.class))).thenReturn(conCodigo, sinCodigo);

        assertEquals("0x6080", cliente.codigoDelContrato());
        assertEquals("0x", cliente.codigoDelContrato());
    }
}
