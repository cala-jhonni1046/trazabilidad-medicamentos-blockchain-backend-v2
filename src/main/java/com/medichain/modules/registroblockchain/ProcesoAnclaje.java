package com.medichain.modules.registroblockchain;

import com.medichain.exceptions.ReglaNegocioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Servicio ProcesoAnclaje en MediChain (R15).
 * Orquesta el anclaje del último hash de la cadena en el contrato
 * MediChainAnchor de Sepolia, intercalando llamadas a la red (fuera de
 * toda transacción de base) con los pasos cortos de PasosAnclaje:
 * <ol>
 *   <li>Control: el último anclaje del contrato coincide con el evento local
 *       de ese número. Si no (base alterada o reseteada) NO se ancla: R15.</li>
 *   <li>Frenos de la tarea automática (el anclaje manual de la Sede no los
 *       respeta): el último anclaje terminó FALLIDO por una causa
 *       determinística (revert, sin gas, gas sobre el máximo), o el saldo no
 *       alcanza para saldo-minimo-anclajes anclajes. Solo WARN en el log.</li>
 *   <li>Abre un PENDIENTE (desde, hasta, hash del último evento).</li>
 *   <li>Antes de firmar: simulación (eth_call con gas-maximo) y estimación
 *       (eth_estimateGas). Si revierte o la estimación + 30 % supera
 *       gas-maximo → FALLIDO con el motivo, SIN enviar ni gastar. Nunca se
 *       recorta el límite para enviar igual (PoliticaGas).</li>
 *   <li>Nonce (LATEST), comisiones con tope, firma; guarda la transacción y
 *       recién después la transmite → ENVIADO.</li>
 *   <li>Seguimiento: recibo → confirmaciones → CONFIRMADO. Sin incluir
 *       después de esperaReemplazo → reemplazo con el mismo nonce y +25 % de
 *       comisión. Minada con error → FALLIDO (SIN_GAS si usó todo el límite,
 *       si no REVERT con el error decodificado). Errores de red → espera
 *       creciente; al llegar a max-intentos → FALLIDO (ERROR_DE_RED).</li>
 * </ol>
 * Un solo anclaje en curso: cerrojo en la JVM (tryLock: la tarea programada
 * y el endpoint nunca envían a la vez) e índice único parcial en la base.
 * Con una sola transacción en vuelo, el nonce de la red (LATEST) es siempre
 * el correcto; si un FALLIDO quedó trabado en el mempool, la nueva usa su
 * mismo nonce con +25 % de comisión y lo reemplaza.
 */
@Service
public class ProcesoAnclaje {

    private static final Logger log = LoggerFactory.getLogger(ProcesoAnclaje.class);

    /** Aumento de comisión para reemplazar una transacción (la red exige al menos +10 %). */
    private static final BigInteger AUMENTO_PORCENTAJE = BigInteger.valueOf(125);
    private static final BigInteger CIEN = BigInteger.valueOf(100);
    private static final BigDecimal WEI_POR_ETH = new BigDecimal("1000000000000000000");

    private final ClienteBlockchain cliente;
    private final PasosAnclaje pasos;
    private final AnclajeProperties propiedades;
    private final PoliticaGas politicaGas;
    private final ReentrantLock cerrojo = new ReentrantLock();

    @Autowired
    public ProcesoAnclaje(ClienteBlockchain cliente, PasosAnclaje pasos, AnclajeProperties propiedades) {
        this.cliente = cliente;
        this.pasos = pasos;
        this.propiedades = propiedades;
        this.politicaGas = new PoliticaGas(propiedades);
    }

    /**
     * Anclar ya (endpoint de la Sede). Devuelve el anclaje creado: ENVIADO, o
     * FALLIDO con el motivo si no se envió porque fallaría seguro. No respeta
     * los frenos de la tarea automática (sirve justamente para destrabarla),
     * pero exige saldo para UN anclaje. 409 ANCLAJE_DESHABILITADO,
     * ANCLAJE_EN_CURSO, SIN_EVENTOS_NUEVOS, SALDO_INSUFICIENTE, o R15 si la
     * cadena local no coincide con el último anclaje del contrato.
     * Sin @Transactional a propósito: habla con la red; los pasos de base
     * son transaccionales en PasosAnclaje.
     */
    public RegistroBlockchain anclarAhora() {
        if (!propiedades.isHabilitado()) {
            throw new ReglaNegocioException("ANCLAJE_DESHABILITADO",
                    "El anclaje en blockchain está deshabilitado (ANCLAJE_HABILITADO=false)");
        }
        if (!cerrojo.tryLock()) {
            throw enCurso();
        }
        try {
            if (pasos.enCurso().isPresent()) {
                throw enCurso();
            }
            long ultimoAnclado = controlarUltimoAnclaje();
            RegistroBlockchain candidato = pasos.candidato(ultimoAnclado).orElseThrow(() -> new ReglaNegocioException(
                    "SIN_EVENTOS_NUEVOS", "No hubo eventos nuevos desde el último anclaje (la cadena llega al evento #"
                            + pasos.ultimoNumeroLocal() + ")"));
            Optional<String> faltaSaldo = frenoPorSaldoDe(candidato, 1);
            if (faltaSaldo.isPresent()) {
                throw new ReglaNegocioException("SALDO_INSUFICIENTE", faltaSaldo.get());
            }
            Optional<RegistroBlockchain> creado;
            try {
                creado = pasos.crearPendiente(ultimoAnclado);
            } catch (DataIntegrityViolationException e) {
                // El índice único parcial: otra instancia abrió un anclaje al mismo tiempo.
                throw enCurso();
            }
            RegistroBlockchain registro = creado.orElseThrow(() -> new ReglaNegocioException("SIN_EVENTOS_NUEVOS",
                    "No hubo eventos nuevos desde el último anclaje"));
            return intentarEnvio(registro);
        } finally {
            cerrojo.unlock();
        }
    }

    /**
     * Ciclo de la tarea programada (cada 5 minutos): si no hay un anclaje en
     * curso, no está frenada y hubo eventos nuevos, abre uno y lo envía. Nunca
     * lanza: los problemas quedan en el log (saneados) y en el registro.
     */
    public void cicloAnclaje() {
        if (!cerrojo.tryLock()) {
            return;
        }
        try {
            if (pasos.enCurso().isPresent()) {
                return;
            }
            Optional<String> frenoFallo = frenoPorFallo();
            if (frenoFallo.isPresent()) {
                log.warn(frenoFallo.get());
                return;
            }
            long ultimoAnclado = controlarUltimoAnclaje();
            Optional<RegistroBlockchain> candidato = pasos.candidato(ultimoAnclado);
            if (candidato.isEmpty()) {
                return;
            }
            Optional<String> frenoSaldo = frenoPorSaldoDe(candidato.get(), propiedades.getSaldoMinimoAnclajes());
            if (frenoSaldo.isPresent()) {
                log.warn(frenoSaldo.get());
                return;
            }
            Optional<RegistroBlockchain> creado = pasos.crearPendiente(ultimoAnclado);
            if (creado.isPresent()) {
                RegistroBlockchain registro = intentarEnvio(creado.get());
                log.info("Anclaje de los eventos #{} a #{}: {}", registro.getDesdeNumero(), registro.getHastaNumero(),
                        registro.getEstado());
            }
        } catch (ReglaNegocioException e) {
            log.error("Anclaje detenido: {}", e.getMessage());
        } catch (ErrorBlockchainException e) {
            log.warn("Anclaje: no se pudo consultar la red: {}", e.getMessage());
        } catch (DataIntegrityViolationException e) {
            log.info("Anclaje: ya hay un anclaje en curso (abierto por otra instancia)");
        } finally {
            cerrojo.unlock();
        }
    }

    /**
     * Ciclo de seguimiento (cada 15 segundos): reintenta un PENDIENTE cuya
     * espera venció, y sigue un ENVIADO (confirmaciones, reemplazo o FALLIDO).
     */
    public void cicloSeguimiento() {
        if (!cerrojo.tryLock()) {
            return;
        }
        try {
            Optional<RegistroBlockchain> enCurso = pasos.enCurso();
            if (enCurso.isEmpty()) {
                return;
            }
            RegistroBlockchain registro = enCurso.get();
            if (registro.getEstado() == EstadoAnclaje.PENDIENTE) {
                if (registro.puedeIntentarse(pasos.ahora())) {
                    intentarEnvio(registro);
                }
            } else {
                seguir(registro);
            }
        } catch (ErrorBlockchainException e) {
            log.warn("Anclaje: no se pudo consultar la red: {}", e.getMessage());
        } finally {
            cerrojo.unlock();
        }
    }

    /**
     * Freno por fallo: si el último anclaje terminado es un FALLIDO por una
     * causa determinística (o anterior a causa_fallo), la tarea automática
     * no reintenta sola. Devuelve el motivo para el log y para /estado.
     */
    public Optional<String> frenoPorFallo() {
        Optional<RegistroBlockchain> ultimo = pasos.ultimoTerminado();
        if (ultimo.isEmpty() || !ultimo.get().frenaLaTareaAutomatica()) {
            return Optional.empty();
        }
        RegistroBlockchain fallido = ultimo.get();
        String causa = fallido.getCausaFallo() == null ? "causa no registrada (anterior a esta versión)"
                : fallido.getCausaFallo().name();
        return Optional.of("Anclaje automático frenado: el último anclaje (eventos #" + fallido.getDesdeNumero()
                + " a #" + fallido.getHastaNumero() + ") terminó FALLIDO por " + causa + ": " + fallido.getUltimoError()
                + " La tarea no reintenta sola para no gastar gas; revisá y anclá a mano "
                + "(POST /api/registros-blockchain/anclar).");
    }

    /**
     * Freno por saldo: el saldo tiene que alcanzar para "anclajes" anclajes al
     * costo estimado, y como mínimo para lo que el nodo exige por la
     * transacción (límite × comisión máxima). Devuelve el motivo si no alcanza.
     */
    public Optional<String> frenoPorSaldo(EstimacionAnclaje estimacion, BigInteger saldoWei, int anclajes) {
        BigInteger requerido = estimacion.costoEstimadoWei().multiply(BigInteger.valueOf(anclajes))
                .max(estimacion.costoMaximoWei());
        if (saldoWei.compareTo(requerido) >= 0) {
            return Optional.empty();
        }
        return Optional.of((anclajes > 1 ? "Anclaje automático frenado por saldo" : "Saldo insuficiente para anclar")
                + ": la billetera tiene " + eth(saldoWei) + " ETH y hace falta al menos " + eth(requerido) + " ETH ("
                + (anclajes > 1 ? anclajes + " anclajes de ~" + eth(estimacion.costoEstimadoWei()) + " ETH, "
                        + "medichain.anclaje.saldo-minimo-anclajes" : "lo que el nodo exige: límite de gas × comisión máxima")
                + "). Cargá SepoliaETH en la billetera.");
    }

    /**
     * Estimación del costo de anclar(hash, hastaNumero) con la red de ahora
     * (gas, límite, precio). Sin enviar nada. Si el contrato lo rechazaría,
     * ErrorBlockchainException con causa REVERT.
     */
    public EstimacionAnclaje estimar(String hash, long hastaNumero) {
        BigInteger estimado = cliente.estimarGasAnclaje(hash, hastaNumero);
        BigInteger limite = politicaGas.limitePara(estimado);
        ComisionesRed red = cliente.comisiones();
        BigInteger maxima = red.getBaseWei().shiftLeft(1).add(red.getPropinaWei()).min(propiedades.topeWei());
        return new EstimacionAnclaje(estimado, limite, politicaGas.dentroDelMaximo(limite),
                red.getBaseWei().add(red.getPropinaWei()), maxima);
    }

    /**
     * Freno por saldo para el anclaje candidato. Si el anclaje fallaría seguro
     * (revert o gas sobre el máximo) no frena: el envío lo registra como
     * FALLIDO con su causa, sin gastar.
     */
    private Optional<String> frenoPorSaldoDe(RegistroBlockchain candidato, int anclajes) {
        EstimacionAnclaje estimacion;
        try {
            estimacion = estimar(candidato.getHashAnclado(), candidato.getHastaNumero());
        } catch (ErrorBlockchainException e) {
            if (e.isDeterministico()) {
                return Optional.empty();
            }
            throw e;
        }
        if (!estimacion.isDentroDelMaximo()) {
            return Optional.empty();
        }
        return frenoPorSaldo(estimacion, cliente.saldoWei(), anclajes);
    }

    /**
     * Compara el último anclaje del contrato con el evento local de ese
     * número y devuelve ese número (0 si no hay anclajes). Si el evento no
     * existe o su hash no coincide, la cadena local ya no es la anclada: no se
     * ancla nada encima (sería anclar una cadena posiblemente alterada) → R15.
     */
    private long controlarUltimoAnclaje() {
        long ultimo = cliente.ultimoNumeroAnclado();
        if (ultimo == 0) {
            return 0;
        }
        Optional<String> local = pasos.hashDelEvento(ultimo);
        if (local.isEmpty()) {
            throw new ReglaNegocioException("R15", "El contrato ya ancló hasta el evento #" + ultimo
                    + " y la base no tiene ese evento (¿se reseteó la base sin desplegar un contrato nuevo, o se "
                    + "borraron eventos?). No se ancla.");
        }
        if (!local.get().equals(cliente.hashAnclado(ultimo))) {
            throw new ReglaNegocioException("R15", "El evento #" + ultimo + " de la base no coincide con lo anclado "
                    + "en Sepolia: posible alteración de la cadena. No se ancla; revisá "
                    + "GET /api/eventos-trazabilidad/verificacion.");
        }
        return ultimo;
    }

    /** Primer envío (o reintento) de un PENDIENTE. Devuelve el registro actualizado. */
    private RegistroBlockchain intentarEnvio(RegistroBlockchain registro) {
        UUID id = registro.getId();
        // Recuperación: un intento anterior firmó y quizás transmitió antes de un reinicio.
        if (registro.getTransactionHash() != null && laRedLaConoce(registro.getTransactionHash())) {
            return pasos.marcarEnviado(id);
        }
        pasos.iniciarIntento(id);
        try {
            // 1. Simulación con gas-maximo como límite: ¿el contrato lo rechazaría? ¿necesita más gas que el máximo?
            cliente.simularAnclaje(registro.getHashAnclado(), registro.getHastaNumero(), politicaGas.getGasMaximo());
            // 2. Límite = estimación + 30 % dentro de [gas-minimo; gas-maximo]; si no entra, no se envía.
            BigInteger limiteGas = politicaGas.limiteParaEnviar(
                    cliente.estimarGasAnclaje(registro.getHashAnclado(), registro.getHastaNumero()));
            BigInteger nonce = cliente.nonce();
            Comisiones comisiones = calcularComisiones(pisoPorTransaccionTrabada(nonce));
            TransaccionFirmada transaccion = cliente.firmarAnclaje(registro.getHashAnclado(), registro.getHastaNumero(),
                    nonce, limiteGas, comisiones.getMaximaWei(), comisiones.getPropinaWei());
            // Primero se guarda (con su hash) y después se transmite: si la app se cae en el medio,
            // el próximo intento la reconoce en la red en lugar de duplicarla.
            pasos.registrarTransaccion(id, transaccion);
            cliente.transmitir(transaccion);
            return pasos.marcarEnviado(id);
        } catch (ErrorBlockchainException e) {
            if (e.isDeterministico()) {
                log.warn("Anclaje de los eventos #{} a #{} no enviado ({}): {}", registro.getDesdeNumero(),
                        registro.getHastaNumero(), e.getCausa(), e.getMessage());
                return pasos.marcarFallido(id, e.getMessage(), e.getCausa());
            }
            return pasos.registrarFallo(id, e.getMessage());
        }
    }

    /** Sigue un ENVIADO: confirmaciones, reorganización, reemplazo o FALLIDO. */
    private void seguir(RegistroBlockchain registro) {
        UUID id = registro.getId();
        Optional<ReciboTransaccion> recibo = cliente.recibo(registro.getTransactionHash());
        if (recibo.isPresent()) {
            if (!recibo.get().isExitosa()) {
                registrarFalloMinado(registro, recibo.get());
                return;
            }
            RegistroBlockchain actualizado = pasos.registrarInclusion(id, recibo.get(), cliente.ultimoBloque());
            if (actualizado.getEstado() == EstadoAnclaje.CONFIRMADO) {
                log.info("Anclaje CONFIRMADO: eventos #{} a #{}, bloque {}, gas {}", actualizado.getDesdeNumero(),
                        actualizado.getHastaNumero(), actualizado.getBloque(), actualizado.getGasUsado());
            }
            return;
        }
        if (registro.getBloque() != null) {
            pasos.registrarSinInclusion(id);
        }
        Duration esperando = Duration.between(registro.getFechaEnvio(), pasos.ahora());
        if (esperando.compareTo(propiedades.getEsperaReemplazo()) < 0 || !registro.puedeIntentarse(pasos.ahora())) {
            return;
        }
        reemplazar(registro);
    }

    /**
     * La transacción se minó con error. Si usó todo su límite → SIN_GAS; si no,
     * el contrato la rechazó → REVERT, con el error decodificado simulándola de
     * nuevo (sin enviar nada). Las dos frenan la tarea automática.
     */
    private void registrarFalloMinado(RegistroBlockchain registro, ReciboTransaccion recibo) {
        if (registro.getLimiteGas() != null && recibo.getGasUsado() >= registro.getLimiteGas()) {
            pasos.marcarFallido(registro.getId(), "La transacción se quedó sin gas (out of gas) en el bloque "
                    + recibo.getBloque() + ": usó todo su límite de " + registro.getLimiteGas() + ". La tarea automática "
                    + "queda frenada; revisá la estimación en GET /api/registros-blockchain/estado.", CausaFallo.SIN_GAS);
            return;
        }
        String detalle = "";
        try {
            cliente.simularAnclaje(registro.getHashAnclado(), registro.getHastaNumero(), politicaGas.getGasMaximo());
        } catch (ErrorBlockchainException e) {
            if (e.getCausa() == CausaFallo.REVERT) {
                detalle = " (" + e.getMessage() + ")";
            }
        }
        pasos.marcarFallido(registro.getId(), "El contrato rechazó la transacción (revert) en el bloque "
                + recibo.getBloque() + detalle, CausaFallo.REVERT);
    }

    /** Reemplaza una transacción sin incluir: mismo nonce, +25 % de comisión (o FALLIDO si no quedan intentos). */
    private void reemplazar(RegistroBlockchain registro) {
        UUID id = registro.getId();
        if (registro.getIntentos() >= propiedades.getMaxIntentos()) {
            pasos.marcarFallido(id, "La transacción no se incluyó en un bloque después de "
                    + registro.getIntentos() + " intentos", CausaFallo.SIN_INCLUIR);
            return;
        }
        pasos.iniciarIntento(id);
        try {
            Comisiones comisiones = calcularComisiones(aumentadas(registro));
            TransaccionFirmada transaccion = cliente.firmarAnclaje(registro.getHashAnclado(), registro.getHastaNumero(),
                    BigInteger.valueOf(registro.getNonce()), BigInteger.valueOf(registro.getLimiteGas()),
                    comisiones.getMaximaWei(), comisiones.getPropinaWei());
            // Se transmite primero: si falla (por ejemplo, la original ya se minó) se conserva el hash anterior.
            cliente.transmitir(transaccion);
            pasos.registrarReemplazo(id, transaccion);
            log.info("Anclaje: transacción reemplazada con más comisión (nonce {})", registro.getNonce());
        } catch (ErrorBlockchainException e) {
            pasos.registrarFallo(id, "Reemplazo: " + e.getMessage());
        }
    }

    /**
     * Comisiones para firmar: propina sugerida y máxima = 2 × base + propina
     * (margen ante subas de la base), nunca menores que el piso (reemplazo) ni
     * mayores que el tope configurado (tope-gwei). Si la red está por encima
     * del tope, no se envía: se reintenta más tarde.
     */
    private Comisiones calcularComisiones(Comisiones piso) {
        ComisionesRed red = cliente.comisiones();
        BigInteger propina = red.getPropinaWei().max(piso.getPropinaWei());
        BigInteger maxima = red.getBaseWei().shiftLeft(1).add(propina).max(piso.getMaximaWei());
        BigInteger tope = propiedades.topeWei();
        if (red.getBaseWei().add(propina).compareTo(tope) > 0 || piso.getMaximaWei().compareTo(tope) > 0) {
            throw new ErrorBlockchainException("La comisión de la red (base " + gwei(red.getBaseWei())
                    + " gwei) supera el tope de " + propiedades.getTopeGwei() + " gwei: se reintenta más tarde");
        }
        maxima = maxima.min(tope);
        return new Comisiones(maxima, propina.min(maxima));
    }

    /**
     * Si el último FALLIDO usó este mismo nonce y su transacción sigue en el
     * mempool (sin recibo), la nueva la tiene que superar en +25 % para
     * reemplazarla; si no, no hay piso.
     */
    private Comisiones pisoPorTransaccionTrabada(BigInteger nonce) {
        Optional<RegistroBlockchain> fallido = pasos.ultimoFallido();
        if (fallido.isEmpty() || fallido.get().getNonce() == null || fallido.get().getTransactionHash() == null
                || fallido.get().getNonce() != nonce.longValueExact()) {
            return Comisiones.NINGUNA;
        }
        String hash = fallido.get().getTransactionHash();
        boolean trabada = cliente.recibo(hash).isEmpty() && cliente.transaccionConocida(hash);
        return trabada ? aumentadas(fallido.get()) : Comisiones.NINGUNA;
    }

    /** Las comisiones de la transacción vigente del registro, +25 %. */
    private Comisiones aumentadas(RegistroBlockchain registro) {
        if (registro.getComisionMaximaWei() == null || registro.getPropinaWei() == null) {
            return Comisiones.NINGUNA;
        }
        return new Comisiones(porcentaje(registro.getComisionMaximaWei(), AUMENTO_PORCENTAJE),
                porcentaje(registro.getPropinaWei(), AUMENTO_PORCENTAJE));
    }

    /** valor × porcentaje / 100, redondeado hacia arriba. */
    private static BigInteger porcentaje(long valor, BigInteger porcentaje) {
        BigInteger[] division = BigInteger.valueOf(valor).multiply(porcentaje).divideAndRemainder(CIEN);
        return division[1].signum() == 0 ? division[0] : division[0].add(BigInteger.ONE);
    }

    /** Wei → gwei con 3 decimales, para los mensajes. */
    private static String gwei(BigInteger wei) {
        return new BigDecimal(wei).divide(BigDecimal.valueOf(1_000_000_000L), 3, RoundingMode.HALF_UP).toPlainString();
    }

    /** Wei → ETH con 6 decimales, para los mensajes. */
    private static String eth(BigInteger wei) {
        return new BigDecimal(wei).divide(WEI_POR_ETH, 6, RoundingMode.UP).toPlainString();
    }

    /** true si la red tiene el recibo o conoce la transacción (pendiente). */
    private boolean laRedLaConoce(String hashTransaccion) {
        return cliente.recibo(hashTransaccion).isPresent() || cliente.transaccionConocida(hashTransaccion);
    }

    /** Error 409 ANCLAJE_EN_CURSO. */
    private static ReglaNegocioException enCurso() {
        return new ReglaNegocioException("ANCLAJE_EN_CURSO",
                "Ya hay un anclaje en curso (PENDIENTE o ENVIADO): esperá a que se confirme");
    }

    /** Comisión máxima y propina por unidad de gas, en wei. */
    private static final class Comisiones {

        static final Comisiones NINGUNA = new Comisiones(BigInteger.ZERO, BigInteger.ZERO);

        private final BigInteger maximaWei;
        private final BigInteger propinaWei;

        Comisiones(BigInteger maximaWei, BigInteger propinaWei) {
            this.maximaWei = maximaWei;
            this.propinaWei = propinaWei;
        }

        BigInteger getMaximaWei() {
            return maximaWei;
        }

        BigInteger getPropinaWei() {
            return propinaWei;
        }
    }
}
