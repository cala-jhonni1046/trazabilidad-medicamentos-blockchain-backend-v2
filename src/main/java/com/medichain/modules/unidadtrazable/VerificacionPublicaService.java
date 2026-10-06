package com.medichain.modules.unidadtrazable;

import com.medichain.modules.bulto.Bulto;
import com.medichain.modules.cuarentena.EvaluadorBloqueo;
import com.medichain.modules.despachologistico.DespachoLogistico;
import com.medichain.modules.despachologistico.DespachoLogisticoRepository;
import com.medichain.modules.lote.EstadoLote;
import com.medichain.modules.lote.Lote;
import com.medichain.modules.medicamento.Medicamento;
import com.medichain.modules.medicamento.MedicamentoRepository;
import com.medichain.modules.recepcion.Recepcion;
import com.medichain.modules.recepcion.RecepcionRepository;
import com.medichain.modules.registroblockchain.AnclajeProperties;
import com.medichain.modules.registroblockchain.PasosAnclaje;
import com.medichain.modules.registroblockchain.RegistroBlockchain;
import com.medichain.modules.registroblockchain.RegistroBlockchainRepository;
import com.medichain.modules.trazabilidad.DatosEventos;
import com.medichain.modules.trazabilidad.EventoTrazabilidadRepository;
import com.medichain.modules.trazabilidad.TipoEvento;
import com.medichain.utils.validacion.Gs1Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Servicio VerificacionPublicaService en MediChain (R10, R13, R14).
 * Verificación pública (sin token) de una caja por GTIN + serie, lo que
 * trae el DataMatrix. Precedencia del estado: ROBADA > BLOQUEADA (cuarentena,
 * recall, vencido, DEVUELTA o RECHAZADA; el recall con su propio mensaje de
 * retiro del mercado) > YA_DISPENSADA > APTA (EN_STOCK) >
 * EN_DISTRIBUCION (todavía no llegó a una farmacia). Un GTIN + serie que
 * no existe → NO_EXISTE.
 * Registra SERIE_ROBADA y SERIE_INEXISTENTE (posibles falsificaciones) con
 * la protección anti-spam de RegistroIntentosVerificacion; SERIE_INEXISTENTE
 * solo si el GTIN es de un medicamento registrado (si no, no es un producto
 * nuestro y no hay evento). NUNCA devuelve datos del paciente ni de la
 * dispensación.
 * Anclaje (R15): el anclaje en Sepolia que cubre el último hito del
 * recorrido público de la caja (lote, bulto, salidas y recepciones), con
 * su transacción y el enlace a Etherscan. Sin la dispensación: el momento
 * del anclaje revelaría cuándo se dispensó (R13). No consulta la red en
 * vivo (un endpoint público no depende de Sepolia ni gasta la cuota del
 * RPC): para comprobarlo de forma independiente está el enlace.
 */
@Service
public class VerificacionPublicaService {

    private static final Logger log = LoggerFactory.getLogger(VerificacionPublicaService.class);

    /** Tipos de evento de los hitos del recorrido público (sin la dispensación, R13). */
    private static final List<TipoEvento> HITOS_DEL_RECORRIDO = List.of(TipoEvento.LOTE_REGISTRADO,
            TipoEvento.LOTE_LIBERADO, TipoEvento.BULTO_ARMADO, TipoEvento.VIAJE_SALIDA, TipoEvento.BULTO_RECIBIDO,
            TipoEvento.BULTO_RECHAZADO);

    private final UnidadTrazableRepository unidadTrazableRepository;
    private final MedicamentoRepository medicamentoRepository;
    private final DespachoLogisticoRepository despachoLogisticoRepository;
    private final RecepcionRepository recepcionRepository;
    private final EvaluadorBloqueo evaluadorBloqueo;
    private final RegistroIntentosVerificacion registroIntentos;
    private final EventoTrazabilidadRepository eventoRepository;
    private final RegistroBlockchainRepository registroBlockchainRepository;
    private final AnclajeProperties propiedadesAnclaje;

    @Autowired
    public VerificacionPublicaService(UnidadTrazableRepository unidadTrazableRepository,
                                      MedicamentoRepository medicamentoRepository,
                                      DespachoLogisticoRepository despachoLogisticoRepository,
                                      RecepcionRepository recepcionRepository, EvaluadorBloqueo evaluadorBloqueo,
                                      RegistroIntentosVerificacion registroIntentos,
                                      EventoTrazabilidadRepository eventoRepository,
                                      RegistroBlockchainRepository registroBlockchainRepository,
                                      AnclajeProperties propiedadesAnclaje) {
        this.unidadTrazableRepository = unidadTrazableRepository;
        this.medicamentoRepository = medicamentoRepository;
        this.despachoLogisticoRepository = despachoLogisticoRepository;
        this.recepcionRepository = recepcionRepository;
        this.evaluadorBloqueo = evaluadorBloqueo;
        this.registroIntentos = registroIntentos;
        this.eventoRepository = eventoRepository;
        this.registroBlockchainRepository = registroBlockchainRepository;
        this.propiedadesAnclaje = propiedadesAnclaje;
    }

    /** Verifica la caja. GTIN y serie ya validados en formato por el Controller. */
    @Transactional(readOnly = true)
    public VerificacionPublicaResponseDTO verificar(String gtinRecibido, String serie) {
        String gtin = Gs1Util.normalizar(gtinRecibido);
        VerificacionPublicaResponseDTO respuesta = new VerificacionPublicaResponseDTO();
        respuesta.setGtin(gtin);
        respuesta.setSerie(serie);

        Optional<UnidadTrazable> encontrada = unidadTrazableRepository.findByGtinAndSerie(gtin, serie);
        if (encontrada.isEmpty()) {
            Optional<Medicamento> medicamento = medicamentoRepository.findByGtin(gtin);
            medicamento.ifPresent(m -> {
                completarProducto(respuesta, m);
                registrarIntento(TipoEvento.SERIE_INEXISTENTE, gtin, serie, "Medicamento", m,
                        DatosEventos.serieInexistente(gtin, serie, "VERIFICACION_PUBLICA"));
            });
            respuesta.setEstado(EstadoVerificacion.NO_EXISTE);
            respuesta.setMensaje("No encontramos esta caja: puede ser falsificada. No la consumas y denunciala.");
            return respuesta;
        }

        UnidadTrazable caja = encontrada.get();
        Lote lote = caja.getLote();
        completarProducto(respuesta, lote.getMedicamento());
        respuesta.setLote(lote.getCodigo());
        respuesta.setVencimiento(lote.getFechaVencimiento());
        List<DespachoLogistico> salidas = caja.getBulto() == null ? List.of()
                : despachoLogisticoRepository.findSalidasDelBulto(caja.getBulto().getId());
        respuesta.setRecorrido(recorrido(caja, salidas));
        respuesta.setAnclaje(anclajeDe(caja, salidas));
        definirEstado(respuesta, caja);
        if (caja.getEstado() == EstadoUnidad.ROBADA) {
            registrarIntento(TipoEvento.SERIE_ROBADA, gtin, serie, "UnidadTrazable", caja,
                    DatosEventos.serieRobada(caja, "VERIFICACION_PUBLICA"));
        }
        return respuesta;
    }

    /** Estado y mensaje para el paciente, con la precedencia de la descripción de la clase. */
    private void definirEstado(VerificacionPublicaResponseDTO respuesta, UnidadTrazable caja) {
        EstadoUnidad estado = caja.getEstado();
        if (estado == EstadoUnidad.ROBADA) {
            respuesta.setEstado(EstadoVerificacion.ROBADA);
            respuesta.setMensaje("Esta caja fue reportada como robada. No la compres ni la consumas, y denunciala.");
            return;
        }
        if (evaluadorBloqueo.esRetiroDelMercado(caja)) {
            respuesta.setEstado(EstadoVerificacion.BLOQUEADA);
            respuesta.setMensaje("Retiro del mercado (recall): no consumir. Devolvela en tu farmacia.");
            return;
        }
        Optional<String> bloqueo = evaluadorBloqueo.bloqueoDeCaja(caja);
        if (estado == EstadoUnidad.DEVUELTA || estado == EstadoUnidad.RECHAZADA || bloqueo.isPresent()) {
            respuesta.setEstado(EstadoVerificacion.BLOQUEADA);
            respuesta.setMensaje(mensajeDeBloqueo(caja, bloqueo));
            return;
        }
        switch (estado) {
            case DISPENSADA -> {
                respuesta.setEstado(EstadoVerificacion.YA_DISPENSADA);
                respuesta.setMensaje("Esta caja ya fue entregada a un paciente. Si te la ofrecen de nuevo, no la compres y denunciala.");
            }
            case EN_STOCK -> {
                respuesta.setEstado(EstadoVerificacion.APTA);
                respuesta.setMensaje("Medicamento auténtico, apto para dispensar.");
            }
            default -> {
                respuesta.setEstado(EstadoVerificacion.EN_DISTRIBUCION);
                respuesta.setMensaje("Esta caja todavía no llegó a una farmacia. Si la tenés, denunciala.");
            }
        }
    }

    /** Mensaje de una caja bloqueada que no está en recall: cuarentena, vencida, devuelta o rechazada. */
    private String mensajeDeBloqueo(UnidadTrazable caja, Optional<String> bloqueo) {
        if (caja.getLote().getEstado() == EstadoLote.CUARENTENA) {
            return "Medicamento en cuarentena preventiva: no consumir hasta nuevo aviso.";
        }
        if (caja.getEstado() == EstadoUnidad.DEVUELTA) {
            return "No consumir: la caja fue retirada de la venta.";
        }
        if (caja.getEstado() == EstadoUnidad.RECHAZADA) {
            return "No consumir: la caja fue rechazada en la cadena de distribución.";
        }
        return "No consumir: " + bloqueo.orElse("la caja está bloqueada") + ".";
    }

    /**
     * Recorrido resumido: fabricación y liberación del lote, cada salida de
     * viaje y cada recepción del bulto. Termina en la recepción en farmacia:
     * nada de la dispensación.
     */
    private List<EtapaRecorridoDTO> recorrido(UnidadTrazable caja, List<DespachoLogistico> salidas) {
        Lote lote = caja.getLote();
        String laboratorio = lote.getLaboratorio().getRazonSocial();
        List<EtapaRecorridoDTO> etapas = new ArrayList<>();
        etapas.add(new EtapaRecorridoDTO("FABRICADO", laboratorio, lote.getFechaFabricacion()));
        if (lote.getFechaLiberacion() != null) {
            etapas.add(new EtapaRecorridoDTO("LIBERADO", laboratorio, lote.getFechaLiberacion().toLocalDate()));
        }
        Bulto bulto = caja.getBulto();
        if (bulto == null) {
            return etapas;
        }
        List<EtapaRecorridoDTO> movimientos = new ArrayList<>();
        for (DespachoLogistico viaje : salidas) {
            movimientos.add(new EtapaRecorridoDTO("DESPACHADO", viaje.getOrigen().getRazonSocial(),
                    viaje.getFechaSalida().toLocalDate()));
        }
        for (Recepcion recepcion : recepcionRepository.findByBultoIdOrderByFechaHoraAsc(bulto.getId())) {
            movimientos.add(new EtapaRecorridoDTO(recepcion.getConforme() ? "RECIBIDO" : "RECHAZADO",
                    recepcion.getReceptora().getRazonSocial(), recepcion.getFechaHora().toLocalDate()));
        }
        movimientos.sort((a, b) -> a.getFecha().compareTo(b.getFecha()));
        etapas.addAll(movimientos);
        return etapas;
    }

    /**
     * Anclaje que cubre el último hito del recorrido: el primero ENVIADO o
     * CONFIRMADO con hastaNumero ≥ el número de ese evento (anclar el evento N
     * protege a todos los anteriores). Si todavía no hay uno: PENDIENTE (anclaje
     * habilitado) o NO_DISPONIBLE (deshabilitado).
     */
    private AnclajeDTO anclajeDe(UnidadTrazable caja, List<DespachoLogistico> salidas) {
        List<UUID> entidades = new ArrayList<>();
        entidades.add(caja.getLote().getId());
        if (caja.getBulto() != null) {
            entidades.add(caja.getBulto().getId());
            for (DespachoLogistico viaje : salidas) {
                entidades.add(viaje.getId());
            }
        }
        Long ultimoHito = eventoRepository.ultimoNumeroDe(entidades, HITOS_DEL_RECORRIDO);
        if (ultimoHito != null) {
            Optional<RegistroBlockchain> anclaje = registroBlockchainRepository
                    .findFirstByHastaNumeroGreaterThanEqualAndEstadoInOrderByHastaNumeroAsc(ultimoHito,
                            PasosAnclaje.EXITOSOS);
            if (anclaje.isPresent()) {
                AnclajeDTO dto = AnclajeDTO.conEstado(anclaje.get().getEstado().name());
                dto.setRed(anclaje.get().getRed());
                dto.setTransactionHash(anclaje.get().getTransactionHash());
                dto.setBloque(anclaje.get().getBloque());
                dto.setEnlace(anclaje.get().enlaceEtherscan());
                return dto;
            }
        }
        return AnclajeDTO.conEstado(propiedadesAnclaje.isHabilitado() ? "PENDIENTE" : "NO_DISPONIBLE");
    }

    /** Datos públicos del producto y su laboratorio. */
    private void completarProducto(VerificacionPublicaResponseDTO respuesta, Medicamento medicamento) {
        respuesta.setProducto(medicamento.getNombreComercial());
        respuesta.setPrincipioActivo(medicamento.getPrincipioActivo());
        respuesta.setConcentracion(medicamento.getConcentracion());
        respuesta.setPresentacion(medicamento.getPresentacion());
        respuesta.setLaboratorio(medicamento.getLaboratorio().getRazonSocial());
    }

    /**
     * Registra el intento (transacción aparte, un evento por día por caja). Si
     * dos consultas simultáneas chocan en la restricción única, se ignora: el
     * evento del día ya quedó registrado por la otra.
     */
    private void registrarIntento(TipoEvento tipo, String gtin, String serie, String entidadTipo, Object entidad,
                                  Map<String, Object> datos) {
        UUID entidadId = entidad instanceof UnidadTrazable caja ? caja.getId() : ((Medicamento) entidad).getId();
        try {
            registroIntentos.registrarSiCorresponde(tipo, gtin, serie, entidadTipo, entidadId, datos);
        } catch (DataIntegrityViolationException carrera) {
            log.debug("Intento de verificación ya registrado hoy por otra consulta simultánea");
        }
    }
}
