package com.medichain.herramientas;

import com.medichain.modules.registroblockchain.AnclajeProperties;
import com.medichain.modules.registroblockchain.ClienteBlockchainWeb3j;
import com.medichain.modules.registroblockchain.CodigoEsperadoContrato;
import com.medichain.modules.registroblockchain.ComisionesRed;
import com.medichain.modules.registroblockchain.ErrorBlockchainException;
import com.medichain.modules.registroblockchain.PoliticaGas;
import com.medichain.modules.registroblockchain.ReciboTransaccion;
import com.medichain.modules.registroblockchain.TransaccionFirmada;
import com.medichain.modules.trazabilidad.HashUtil;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Herramienta AnclajeDePrueba en MediChain (solo para pruebas).
 * Hace UN anclaje de prueba en un contrato MediChainAnchor DESCARTABLE,
 * pasando por el mismo código que usa el backend antes de enviar:
 * ClienteBlockchainWeb3j.simularAnclaje (eth_call con gas-maximo),
 * estimarGasAnclaje, PoliticaGas (estimación + 30 % dentro de
 * [gas-minimo; gas-maximo]) y CodigoEsperadoContrato. Ancla el número
 * siguiente al último del contrato con un hash de prueba.
 * Se niega si el código no es MediChainAnchor.sol, si la billetera no es la
 * dueña, o si el costo máximo posible supera --max-eth (por defecto 0.002).
 * Lee SEPOLIA_RPC_URL y WALLET_PRIVATE_KEY del entorno y nunca los imprime.
 * Uso: scripts/anclar-prueba.sh --contrato 0x… [--max-eth 0.002]
 */
public final class AnclajeDePrueba {

    private static final long CHAIN_ID_SEPOLIA = 11_155_111L;
    private static final BigDecimal WEI_POR_ETH = new BigDecimal("1000000000000000000");
    private static final String ETHERSCAN = "https://sepolia.etherscan.io";

    private AnclajeDePrueba() {
    }

    /** Punto de entrada. */
    public static void main(String[] args) throws InterruptedException {
        String contrato = argumento(args, "--contrato", null);
        BigDecimal maxEth = new BigDecimal(argumento(args, "--max-eth", "0.002"));
        if (contrato == null) {
            salir("Falta --contrato 0x… (un contrato DESCARTABLE de pruebas).");
        }
        AnclajeProperties propiedades = new AnclajeProperties();
        propiedades.setHabilitado(true);
        propiedades.setRpcUrl(System.getenv("SEPOLIA_RPC_URL"));
        propiedades.setClavePrivada(System.getenv("WALLET_PRIVATE_KEY"));
        propiedades.setContrato(contrato);
        try {
            propiedades.validarFormato();
        } catch (IllegalStateException e) {
            salir(e.getMessage());
        }
        ClienteBlockchainWeb3j cliente = new ClienteBlockchainWeb3j(propiedades);
        try {
            anclar(cliente, propiedades, maxEth);
        } catch (ErrorBlockchainException e) {
            salir("No se ancló (" + (e.getCausa() == null ? "error de red" : e.getCausa()) + "): " + e.getMessage());
        } finally {
            cliente.cerrar();
        }
    }

    /** Controles, simulación, estimación, firma, envío y espera del recibo. */
    private static void anclar(ClienteBlockchainWeb3j cliente, AnclajeProperties propiedades, BigDecimal maxEth)
            throws InterruptedException {
        if (cliente.chainId() != CHAIN_ID_SEPOLIA) {
            salir("El RPC no apunta a Sepolia. No se ancla.");
        }
        Optional<String> variante = new CodigoEsperadoContrato().varianteDe(cliente.codigoDelContrato());
        if (variante.isEmpty()) {
            salir("El código de " + propiedades.getContrato() + " no es MediChainAnchor.sol. No se ancla.");
        }
        String billetera = cliente.direccionBilletera();
        if (!cliente.duenioDelContrato().equalsIgnoreCase(billetera)) {
            salir("La billetera no es la dueña del contrato. No se ancla.");
        }
        BigInteger saldoAntes = cliente.saldoWei();
        long ultimo = cliente.ultimoNumeroAnclado();
        long numero = ultimo + 1;
        String hash = HashUtil.sha256Hex("MediChain prueba de anclaje #" + numero + " " + Instant.now());
        System.out.println("Contrato " + propiedades.getContrato() + ": MediChainAnchor.sol " + variante.get()
                + ", " + cliente.cantidadAnclajes() + " anclajes, último #" + ultimo);
        System.out.println("Billetera " + billetera + ", saldo " + eth(saldoAntes) + " ETH");

        PoliticaGas politica = new PoliticaGas(propiedades);
        cliente.simularAnclaje(hash, numero, politica.getGasMaximo());
        System.out.println("Simulación anclar(…, " + numero + ") con gas-maximo " + politica.getGasMaximo()
                + ": no revierte y entra en el máximo");
        BigInteger estimado = cliente.estimarGasAnclaje(hash, numero);
        BigInteger limite = politica.limiteParaEnviar(estimado);
        ComisionesRed red = cliente.comisiones();
        BigInteger maxima = red.getBaseWei().shiftLeft(1).add(red.getPropinaWei()).min(propiedades.topeWei());
        BigInteger costoMaximo = limite.multiply(maxima);
        System.out.println("Estimación " + estimado + " de gas → límite " + limite + " (+30 %, dentro de ["
                + politica.getGasMinimo() + "; " + politica.getGasMaximo() + "]); costo máximo posible "
                + eth(costoMaximo) + " ETH");
        if (new BigDecimal(costoMaximo).divide(WEI_POR_ETH).compareTo(maxEth) > 0) {
            salir("El costo máximo posible supera --max-eth " + maxEth.toPlainString() + ". No se ancla.");
        }
        if (saldoAntes.compareTo(costoMaximo) < 0) {
            salir("Saldo insuficiente para el costo máximo posible. No se ancla.");
        }

        TransaccionFirmada transaccion = cliente.firmarAnclaje(hash, numero, cliente.nonce(), limite, maxima,
                red.getPropinaWei().min(maxima));
        cliente.transmitir(transaccion);
        System.out.println("Transacción enviada: " + ETHERSCAN + "/tx/" + transaccion.getHash());
        Optional<ReciboTransaccion> recibo = Optional.empty();
        for (int i = 0; i < 80 && recibo.isEmpty(); i++) {
            Thread.sleep(3_000);
            recibo = cliente.recibo(transaccion.getHash());
        }
        if (recibo.isEmpty()) {
            salir("No se incluyó en 4 minutos: " + ETHERSCAN + "/tx/" + transaccion.getHash());
        }
        ReciboTransaccion incluido = recibo.get();
        BigInteger costo = BigInteger.valueOf(incluido.getGasUsado()).multiply(BigInteger.valueOf(incluido.getPrecioEfectivoWei()));
        System.out.println((incluido.isExitosa() ? "OK" : "FALLÓ") + " en el bloque " + incluido.getBloque()
                + ": gas usado " + incluido.getGasUsado() + " de un límite de " + limite + " (estimación " + estimado
                + "), costo " + eth(costo) + " ETH");
        System.out.println("Contrato: ultimoNumero() = " + cliente.ultimoNumeroAnclado() + ", hashAnclado(" + numero
                + ") " + (hash.equals(cliente.hashAnclado(numero)) ? "= el hash enviado" : "≠ el hash enviado"));
        System.out.println("Saldo después: " + eth(cliente.saldoWei()) + " ETH (antes " + eth(saldoAntes) + ")");
    }

    /** Valor de un argumento --nombre valor, o el valor por defecto. */
    private static String argumento(String[] args, String nombre, String defecto) {
        List<String> lista = List.of(args);
        int i = lista.indexOf(nombre);
        return i >= 0 && i + 1 < lista.size() ? lista.get(i + 1) : defecto;
    }

    /** Wei → ETH con 8 decimales. */
    private static String eth(BigInteger wei) {
        return new BigDecimal(wei).divide(WEI_POR_ETH, 8, RoundingMode.DOWN).toPlainString();
    }

    /** Termina con un mensaje (ya saneado) y código 1. */
    private static void salir(String mensaje) {
        System.err.println(mensaje);
        System.exit(1);
    }
}
