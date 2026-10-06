package com.medichain.modules.registroblockchain;

import com.medichain.exceptions.ResourceNotFoundException;
import com.medichain.modules.trazabilidad.CadenaEstado;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Servicio RegistroBlockchainService en MediChain (R15).
 * Consulta de los anclajes, "anclar ya" (para la demo, delega en
 * ProcesoAnclaje) y el tablero de estado del anclaje (saldo, comisión,
 * cuántos anclajes alcanzan). Los anclajes los crea y sigue el proceso
 * interno; por API no se crean ni se editan.
 */
@Service
public class RegistroBlockchainService {

    private static final BigDecimal WEI_POR_ETH = new BigDecimal("1000000000000000000");
    private static final BigDecimal WEI_POR_GWEI = new BigDecimal("1000000000");
    /** Hash de relleno para estimar cuando la cadena local todavía no tiene eventos (solo cambia el costo de los datos). */
    private static final String HASH_DE_PRUEBA = "11".repeat(32);

    private final RegistroBlockchainRepository repository;
    private final RegistroBlockchainMapper mapper;
    private final ProcesoAnclaje proceso;
    private final PasosAnclaje pasos;
    private final ClienteBlockchain cliente;
    private final AnclajeProperties propiedades;

    @Autowired
    public RegistroBlockchainService(RegistroBlockchainRepository repository, RegistroBlockchainMapper mapper,
                                     ProcesoAnclaje proceso, PasosAnclaje pasos, ClienteBlockchain cliente,
                                     AnclajeProperties propiedades) {
        this.repository = repository;
        this.mapper = mapper;
        this.proceso = proceso;
        this.pasos = pasos;
        this.cliente = cliente;
        this.propiedades = propiedades;
    }

    /** Devuelve una página de anclajes. */
    @Transactional(readOnly = true)
    public Page<RegistroBlockchain> getAll(Pageable pageable) {
        return repository.findAll(pageable);
    }

    /** Busca un anclaje por id o lanza ResourceNotFoundException si no existe. */
    @Transactional(readOnly = true)
    public RegistroBlockchain getById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RegistroBlockchain no encontrado con id: " + id));
    }

    /**
     * Ancla ya el último evento (sin esperar la tarea de 5 minutos).
     * Sin @Transactional a propósito: habla con la red (ver ProcesoAnclaje).
     */
    public RegistroBlockchain anclarAhora() {
        return proceso.anclarAhora();
    }

    /**
     * Tablero del anclaje. Sin @Transactional a propósito: consulta la red;
     * las lecturas de base son transacciones cortas de los repositorios.
     * gasPorAnclaje es la estimación REAL del próximo anclaje
     * (eth_estimateGas con la red de ahora: el primero de un contrato cuesta
     * más que los siguientes); con ella se calculan el costo, cuántos anclajes
     * alcanzan y el saldo mínimo de la tarea automática. Los frenos y avisos
     * van en "mensaje". Si la red no responde, devuelve lo local con un aviso.
     */
    public EstadoAnclajeResponseDTO estado() {
        EstadoAnclajeResponseDTO dto = new EstadoAnclajeResponseDTO();
        List<String> mensajes = new ArrayList<>();
        dto.setHabilitado(propiedades.isHabilitado());
        dto.setRed(propiedades.getRed());
        dto.setGasMinimo(propiedades.getGasMinimo());
        dto.setGasMaximo(propiedades.getGasMaximo());
        CadenaEstado cadena = pasos.cadena();
        dto.setUltimoNumeroLocal(cadena.getUltimoNumero());
        repository.findFirstByEstadoInOrderByHastaNumeroDesc(PasosAnclaje.EXITOSOS)
                .ifPresent(r -> dto.setUltimoAnclaje(mapper.toResponseDTO(r)));
        pasos.enCurso().ifPresent(r -> dto.setAnclajeEnCurso(mapper.toResponseDTO(r)));
        if (!propiedades.isHabilitado()) {
            dto.setMensaje("El anclaje está deshabilitado (ANCLAJE_HABILITADO=false).");
            return dto;
        }
        Optional<String> frenoFallo = proceso.frenoPorFallo();
        frenoFallo.ifPresent(mensajes::add);
        dto.setAnclajeAutomaticoFrenado(frenoFallo.isPresent());
        String explorador = RegistroBlockchain.urlEtherscan(propiedades.getRed());
        dto.setChainId(propiedades.getChainId());
        dto.setContrato(propiedades.getContrato());
        dto.setEnlaceContrato(explorador + "/address/" + propiedades.getContrato());
        try {
            String billetera = cliente.direccionBilletera();
            dto.setBilletera(billetera);
            dto.setEnlaceBilletera(explorador + "/address/" + billetera);
            BigInteger saldo = cliente.saldoWei();
            dto.setSaldoEth(enEth(saldo, 6));
            long ultimoAnclado = cliente.ultimoNumeroAnclado();
            dto.setUltimoNumeroAnclado(ultimoAnclado);
            dto.setEventosSinAnclar(Math.max(0L, cadena.getUltimoNumero() - ultimoAnclado));
            dto.setPrimerAnclaje(ultimoAnclado == 0);
            completarCostos(dto, cadena, ultimoAnclado, saldo, mensajes);
        } catch (ErrorBlockchainException e) {
            mensajes.add("No se pudo consultar " + propiedades.getRed() + ": " + e.getMessage());
        }
        dto.setMensaje(mensajes.isEmpty() ? null : String.join(" | ", mensajes));
        return dto;
    }

    /**
     * Estima el próximo anclaje (número siguiente al mayor entre la cadena
     * local y el contrato) y completa gas, límite, comisión, costo, anclajes
     * que alcanzan, saldo mínimo y los avisos de gas y de saldo.
     */
    private void completarCostos(EstadoAnclajeResponseDTO dto, CadenaEstado cadena, long ultimoAnclado,
                                 BigInteger saldo, List<String> mensajes) {
        String hash = cadena.getUltimoHash().matches("[0-9a-f]{64}") ? cadena.getUltimoHash() : HASH_DE_PRUEBA;
        long numero = Math.max(cadena.getUltimoNumero(), ultimoAnclado) + 1;
        EstimacionAnclaje estimacion;
        try {
            estimacion = proceso.estimar(hash, numero);
        } catch (ErrorBlockchainException e) {
            if (!e.isDeterministico()) {
                throw e;
            }
            mensajes.add("No se puede estimar el próximo anclaje: " + e.getMessage());
            return;
        }
        dto.setGasPorAnclaje(estimacion.getGasEstimado().longValueExact());
        dto.setLimiteGas(estimacion.getLimiteGas().longValueExact());
        dto.setComisionActualGwei(new BigDecimal(estimacion.getPrecioWei()).divide(WEI_POR_GWEI, 3, RoundingMode.HALF_UP)
                .toPlainString());
        BigInteger costo = estimacion.costoEstimadoWei();
        dto.setCostoPorAnclajeEth(enEth(costo, 8));
        dto.setAnclajesEstimados(costo.signum() == 0 ? null : saldo.divide(costo).longValue());
        int anclajes = propiedades.getSaldoMinimoAnclajes();
        dto.setSaldoMinimoEth(enEth(costo.multiply(BigInteger.valueOf(anclajes)).max(estimacion.costoMaximoWei()), 6));
        if (ultimoAnclado == 0) {
            mensajes.add("El próximo es el primer anclaje de este contrato (crea la lista): los siguientes cuestan "
                    + "bastante menos gas.");
        }
        if (!estimacion.isDentroDelMaximo()) {
            mensajes.add("El próximo anclaje necesitaría " + estimacion.getLimiteGas() + " de gas (estimación + "
                    + PoliticaGas.MARGEN_PORCENTAJE + " %), más que gas-maximo " + propiedades.getGasMaximo()
                    + ": no se enviaría.");
        }
        Optional<String> frenoSaldo = proceso.frenoPorSaldo(estimacion, saldo, anclajes);
        if (frenoSaldo.isPresent()) {
            mensajes.add(frenoSaldo.get() + " El anclaje manual puede enviar si alcanza para uno.");
            dto.setAnclajeAutomaticoFrenado(true);
        }
    }

    /** Wei → ETH con la cantidad de decimales dada. */
    private static String enEth(BigInteger wei, int decimales) {
        return new BigDecimal(wei).divide(WEI_POR_ETH, decimales, RoundingMode.DOWN).toPlainString();
    }
}
