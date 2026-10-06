package com.medichain.modules.registroblockchain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Inicializador VerificacionInicialAnclaje en MediChain (R15).
 * Solo con ANCLAJE_HABILITADO=true. Corre al arrancar, después de los
 * datos iniciales y de la demo ({@code @Order(10)}), y controla la
 * configuración contra la red. Si la red responde y algo es definitivo,
 * la app NO arranca (mensaje claro, sin valores de las variables):
 * <ul>
 *   <li>el RPC apunta a otra red (chainId distinto de Sepolia);</li>
 *   <li>en ANCHOR_CONTRACT_ADDRESS no hay un contrato;</li>
 *   <li>la billetera de WALLET_PRIVATE_KEY no es la dueña del contrato;</li>
 *   <li>el contrato ancló más eventos de los que tiene la base (base
 *       reseteada sin desplegar un contrato nuevo, o eventos borrados).</li>
 * </ul>
 * Además compara el código desplegado con contracts/MediChainAnchor.sol
 * (CodigoEsperadoContrato): acepta sus dos variantes (con y sin
 * optimizador, ignorando metadatos e immutables) y avisa con WARN si no
 * coincide con ninguna (no impide arrancar).
 * Si el RPC no responde, arranca igual con un aviso (la tarea reintenta):
 * el negocio no depende de Alchemy para levantar. Si el último anclaje no
 * coincide con el evento local, arranca pero avisa en ERROR y el anclaje
 * queda detenido (ProcesoAnclaje no ancla sobre una cadena alterada).
 */
@Component
@Order(10)
@ConditionalOnProperty(name = "medichain.anclaje.habilitado", havingValue = "true")
public class VerificacionInicialAnclaje implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(VerificacionInicialAnclaje.class);
    private static final BigDecimal WEI_POR_ETH = new BigDecimal("1000000000000000000");

    private final ClienteBlockchain cliente;
    private final PasosAnclaje pasos;
    private final AnclajeProperties propiedades;
    private final CodigoEsperadoContrato codigoEsperado;

    @Autowired
    public VerificacionInicialAnclaje(ClienteBlockchain cliente, PasosAnclaje pasos, AnclajeProperties propiedades,
                                      CodigoEsperadoContrato codigoEsperado) {
        this.cliente = cliente;
        this.pasos = pasos;
        this.propiedades = propiedades;
        this.codigoEsperado = codigoEsperado;
    }

    /** Controla red, contrato, dueño y coherencia con la cadena local. */
    @Override
    public void run(String... args) {
        long chainId;
        try {
            chainId = cliente.chainId();
        } catch (ErrorBlockchainException e) {
            log.warn("Anclaje habilitado, pero no se pudo consultar la red al arrancar: {}. Se reintenta en cada anclaje.",
                    e.getMessage());
            return;
        }
        if (chainId != propiedades.getChainId()) {
            throw new IllegalStateException("SEPOLIA_RPC_URL apunta a la red con chainId " + chainId
                    + ", pero se esperaba " + propiedades.getRed() + " (" + propiedades.getChainId() + ").");
        }
        try {
            controlarContrato();
        } catch (ErrorBlockchainException e) {
            log.warn("Anclaje habilitado, pero no se pudo leer el contrato al arrancar: {}. Se reintenta en cada anclaje.",
                    e.getMessage());
        }
    }

    /** Contrato desplegado, dueño = billetera, y anclajes coherentes con la base. */
    private void controlarContrato() {
        String codigo = cliente.codigoDelContrato();
        if (codigo.equals("0x")) {
            throw new IllegalStateException("En ANCHOR_CONTRACT_ADDRESS no hay un contrato desplegado en "
                    + propiedades.getRed() + ": revisá la dirección (contracts/GUIA-DESPLIEGUE.md).");
        }
        Optional<String> variante = codigoEsperado.varianteDe(codigo);
        if (variante.isEmpty()) {
            log.warn("El código del contrato de ANCHOR_CONTRACT_ADDRESS no coincide con contracts/MediChainAnchor.sol "
                    + "compilado con solc 0.8.37 (ni con ni sin optimizador, ignorando metadatos e immutables): revisá que "
                    + "sea el contrato correcto (contracts/GUIA-DESPLIEGUE.md). Se siguen los demás controles.");
        } else {
            log.info("Código del contrato verificado: MediChainAnchor.sol compilado {}.", variante.get());
        }
        if (!cliente.duenioDelContrato().equalsIgnoreCase(cliente.direccionBilletera())) {
            throw new IllegalStateException("La billetera de WALLET_PRIVATE_KEY no es la dueña del contrato de "
                    + "ANCHOR_CONTRACT_ADDRESS: desplegalo con esa misma cuenta.");
        }
        long ultimoAnclado = cliente.ultimoNumeroAnclado();
        long ultimoLocal = pasos.ultimoNumeroLocal();
        if (ultimoAnclado > ultimoLocal) {
            throw new IllegalStateException("El contrato ya ancló hasta el evento #" + ultimoAnclado
                    + " y la cadena local llega al #" + ultimoLocal + ". Si reseteaste la base, desplegá un contrato "
                    + "nuevo y actualizá ANCHOR_CONTRACT_ADDRESS (una cadena nueva necesita un contrato nuevo).");
        }
        if (ultimoAnclado > 0) {
            Optional<String> hashLocal = pasos.hashDelEvento(ultimoAnclado);
            if (hashLocal.isEmpty() || !hashLocal.get().equals(cliente.hashAnclado(ultimoAnclado))) {
                log.error("El evento #{} de la base NO coincide con lo anclado en {}: posible alteración de la cadena. "
                        + "El anclaje queda detenido; revisá GET /api/eventos-trazabilidad/verificacion.",
                        ultimoAnclado, propiedades.getRed());
                return;
            }
        }
        BigInteger saldo = cliente.saldoWei();
        log.info("Anclaje en {} habilitado: contrato verificado (dueño = billetera configurada), anclado hasta el "
                + "evento #{} de {}, saldo {} ETH", propiedades.getRed(), ultimoAnclado, ultimoLocal,
                new BigDecimal(saldo).divide(WEI_POR_ETH, 6, RoundingMode.DOWN).toPlainString());
    }
}
