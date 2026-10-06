package com.medichain.config;

import com.medichain.modules.registroblockchain.AnclajeProperties;
import com.medichain.modules.registroblockchain.ClienteBlockchain;
import com.medichain.modules.registroblockchain.ClienteBlockchainDeshabilitado;
import com.medichain.modules.registroblockchain.ClienteBlockchainWeb3j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

/**
 * Configuración AnclajeConfig en MediChain (R15).
 * Registra AnclajeProperties (medichain.anclaje.*) y elige el cliente de
 * blockchain según el interruptor ANCLAJE_HABILITADO:
 * <ul>
 *   <li>false (por defecto): ClienteBlockchainDeshabilitado, sin conexión
 *       a ninguna red (la app y los tests funcionan sin blockchain).</li>
 *   <li>true: valida el formato de SEPOLIA_RPC_URL, WALLET_PRIVATE_KEY y
 *       ANCHOR_CONTRACT_ADDRESS (si falla, la app no arranca, con un mensaje
 *       que no muestra valores) y crea ClienteBlockchainWeb3j.</li>
 * </ul>
 * También expone el reloj UTC que usan los servicios con tiempos de espera.
 */
@Configuration
@EnableConfigurationProperties(AnclajeProperties.class)
public class AnclajeConfig {

    /** Cliente de blockchain real o deshabilitado; al cerrar la app libera las conexiones. */
    @Bean(destroyMethod = "cerrar")
    public ClienteBlockchain clienteBlockchain(AnclajeProperties propiedades) {
        if (!propiedades.isHabilitado()) {
            return new ClienteBlockchainDeshabilitado();
        }
        propiedades.validarFormato();
        return new ClienteBlockchainWeb3j(propiedades);
    }

    /** Reloj del sistema en UTC (los tests lo reemplazan por uno fijo). */
    @Bean
    public Clock reloj() {
        return Clock.systemUTC();
    }
}
