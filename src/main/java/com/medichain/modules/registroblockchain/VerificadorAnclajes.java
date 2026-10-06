package com.medichain.modules.registroblockchain;

import com.medichain.modules.trazabilidad.EventoTrazabilidad;
import com.medichain.modules.trazabilidad.EventoTrazabilidadRepository;
import com.medichain.modules.trazabilidad.VerificacionCadenaResponseDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio VerificadorAnclajes en MediChain (R15).
 * Compara la cadena local con lo anclado en Sepolia. Detecta lo que la
 * verificación local sola NO puede: alguien con acceso a la base altera un
 * evento y RECALCULA todos los hashes siguientes (el algoritmo es público);
 * la cadena queda coherente consigo misma, pero el hash del evento anclado
 * ya no coincide con el que está en el contrato, que nadie puede reescribir.
 * La lista de anclajes se lee DEL CONTRATO (anclajes(desde, cantidad), en
 * páginas de 500), no de la tabla registros_blockchain: borrar o editar
 * filas de esa tabla no oculta nada. Detecta también eventos borrados (el
 * contrato ancló un número que la base no tiene) y registros locales
 * adulterados. Si la red no responde → NO_CONSULTADA (no es "alterada").
 */
@Service
public class VerificadorAnclajes {

    /** Tamaño de página al leer los anclajes del contrato (como la verificación local). */
    public static final int TAMANIO_PAGINA = 500;

    private final ClienteBlockchain cliente;
    private final EventoTrazabilidadRepository eventoRepository;
    private final RegistroBlockchainRepository registroRepository;
    private final PasosAnclaje pasos;
    private final AnclajeProperties propiedades;

    @Autowired
    public VerificadorAnclajes(ClienteBlockchain cliente, EventoTrazabilidadRepository eventoRepository,
                               RegistroBlockchainRepository registroRepository, PasosAnclaje pasos,
                               AnclajeProperties propiedades) {
        this.cliente = cliente;
        this.eventoRepository = eventoRepository;
        this.registroRepository = registroRepository;
        this.pasos = pasos;
        this.propiedades = propiedades;
    }

    /**
     * Verifica la cadena local contra el contrato. Sin @Transactional a
     * propósito: consulta la red, y no se retiene una conexión a la base
     * mientras tanto (cada lectura de base es su propia transacción corta).
     */
    public VerificacionBlockchainDTO verificar() {
        if (!propiedades.isHabilitado()) {
            return new VerificacionBlockchainDTO(EstadoVerificacionBlockchain.NO_DISPONIBLE,
                    "Anclaje deshabilitado (ANCLAJE_HABILITADO=false): solo se verificó la cadena local, "
                            + "sin respaldo público.");
        }
        try {
            return compararConContrato();
        } catch (ErrorBlockchainException e) {
            VerificacionBlockchainDTO resultado = conDatosDeRed(new VerificacionBlockchainDTO(
                    EstadoVerificacionBlockchain.NO_CONSULTADA,
                    "No se pudo consultar " + propiedades.getRed() + ": " + e.getMessage()
                            + ". Esto NO indica una alteración: reintentá."));
            return resultado;
        }
    }

    /**
     * Resumen en una frase para la respuesta de la verificación (lo que se le
     * muestra al jurado): combina el resultado local con el de la blockchain.
     */
    public static String resumen(VerificacionCadenaResponseDTO local, VerificacionBlockchainDTO blockchain) {
        if (!local.isIntegra()) {
            return "Cadena local ROTA en el evento #" + local.getPrimerNumeroRoto() + ": " + local.getMotivo();
        }
        return switch (blockchain.getEstado()) {
            case VERIFICADA -> "Cadena local íntegra y coincide con los " + blockchain.getAnclajesVerificados()
                    + " anclajes de Sepolia (" + blockchain.getEventosSinAnclar() + " eventos todavía sin anclar).";
            case ALTERADA -> "La cadena local es coherente consigo misma, pero NO coincide con lo anclado en Sepolia: "
                    + "se alteraron eventos y se recalcularon los hashes.";
            case NO_CONSULTADA -> "Cadena local íntegra; no se pudo consultar Sepolia (reintentá).";
            case NO_DISPONIBLE -> "Cadena local íntegra; anclaje deshabilitado (sin respaldo público).";
        };
    }

    /** Recorre los anclajes del contrato y los compara con los eventos locales. */
    private VerificacionBlockchainDTO compararConContrato() {
        long total = cliente.cantidadAnclajes();
        long ultimoLocal = pasos.ultimoNumeroLocal();
        Map<Long, String> enContrato = new HashMap<>();
        long anterior = 0;
        long verificados = 0;
        for (long desde = 0; desde < total; desde += TAMANIO_PAGINA) {
            List<AnclajeEnContrato> pagina = cliente.anclajes(desde, TAMANIO_PAGINA);
            if (pagina.isEmpty()) {
                break;
            }
            Map<Long, String> locales = hashesLocales(pagina);
            for (AnclajeEnContrato anclaje : pagina) {
                long numero = anclaje.getNumero();
                String local = locales.get(numero);
                if (local == null) {
                    return alterada(anterior + 1, numero, verificados, "El contrato ancló el evento #" + numero
                            + " y la base no lo tiene: se borraron eventos (o la base se reseteó sin desplegar un "
                            + "contrato nuevo).");
                }
                if (!local.equals(anclaje.getHash())) {
                    return alterada(anterior + 1, numero, verificados, "El evento #" + numero + " de la base no "
                            + "coincide con el hash anclado en " + propiedades.getRed() + ": se alteró algún evento "
                            + "entre el #" + (anterior + 1) + " y el #" + numero + " y se recalcularon los hashes.");
                }
                enContrato.put(numero, anclaje.getHash());
                anterior = numero;
                verificados++;
            }
        }
        // Cada registro CONFIRMADO de la base (de este contrato) tiene que estar en el contrato con el mismo hash.
        for (RegistroBlockchain registro : registroRepository.findByEstadoOrderByHastaNumeroAsc(EstadoAnclaje.CONFIRMADO)) {
            if (!propiedades.getContrato().equalsIgnoreCase(registro.getDireccionContrato())) {
                continue;
            }
            String hash = enContrato.get(registro.getHastaNumero());
            if (hash == null || !hash.equals(registro.getHashAnclado())) {
                return alterada(null, registro.getHastaNumero(), verificados, "El registro de anclaje del evento #"
                        + registro.getHastaNumero() + " en la base no coincide con el contrato: se adulteró la tabla "
                        + "de anclajes.");
            }
        }
        VerificacionBlockchainDTO resultado = conDatosDeRed(new VerificacionBlockchainDTO(
                EstadoVerificacionBlockchain.VERIFICADA,
                total == 0 ? "Todavía no hay anclajes en el contrato." : null));
        resultado.setAnclajesVerificados(verificados);
        resultado.setUltimoNumeroAnclado(anterior);
        resultado.setEventosSinAnclar(Math.max(0L, ultimoLocal - anterior));
        return resultado;
    }

    /** Hashes locales de los números de la página (un solo SELECT). */
    private Map<Long, String> hashesLocales(List<AnclajeEnContrato> pagina) {
        List<Long> numeros = new ArrayList<>();
        for (AnclajeEnContrato anclaje : pagina) {
            numeros.add(anclaje.getNumero());
        }
        Map<Long, String> hashes = new HashMap<>();
        for (EventoTrazabilidad evento : eventoRepository.findByNumeroIn(numeros)) {
            hashes.put(evento.getNumero(), evento.getHash());
        }
        return hashes;
    }

    /** Resultado ALTERADA con el rango y el motivo. */
    private VerificacionBlockchainDTO alterada(Long desde, long hasta, long verificados, String motivo) {
        VerificacionBlockchainDTO resultado = conDatosDeRed(
                new VerificacionBlockchainDTO(EstadoVerificacionBlockchain.ALTERADA, motivo));
        resultado.setAlteradoDesde(desde);
        resultado.setAlteradoHasta(hasta);
        resultado.setAnclajesVerificados(verificados);
        return resultado;
    }

    /** Completa red y contrato. */
    private VerificacionBlockchainDTO conDatosDeRed(VerificacionBlockchainDTO resultado) {
        resultado.setRed(propiedades.getRed());
        resultado.setContrato(propiedades.getContrato());
        return resultado;
    }
}
