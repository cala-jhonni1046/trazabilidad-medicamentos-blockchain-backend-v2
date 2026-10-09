package com.medichain.modules.registroblockchain;

import com.medichain.exceptions.ReglaNegocioException;
import com.medichain.utils.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Set;

/**
 * Entidad RegistroBlockchain en MediChain (R15).
 * Anclaje en Sepolia del hash del evento hastaNumero, que por el
 * encadenamiento protege a todos los eventos 1..hastaNumero; desdeNumero
 * es el primero que este anclaje cubre por primera vez. Los eventos no
 * tienen FK hacia acá: el evento es inmutable y su hash no puede depender
 * del anclaje posterior.
 * Transiciones (solo por métodos de dominio; otra → TRANSICION_INVALIDA):
 * <ul>
 *   <li>PENDIENTE → ENVIADO (transmitida) → CONFIRMADO (N confirmaciones).</li>
 *   <li>ENVIADO → ENVIADO: reemplazo con el mismo nonce y más comisión.</li>
 *   <li>PENDIENTE → FALLIDO: max-intentos envíos fallidos, o no se envió porque fallaría seguro
 *       (revert simulado o gas sobre el máximo).</li>
 *   <li>ENVIADO → FALLIDO: el contrato la rechazó (revert), se quedó sin gas, o max-intentos sin incluirse.</li>
 * </ul>
 * Todo FALLIDO guarda su causaFallo; las determinísticas (REVERT, SIN_GAS,
 * GAS_SOBRE_EL_MAXIMO) frenan la tarea automática hasta un anclaje manual.
 * Un FALLIDO no deja huecos: el siguiente anclaje cubre desde el último exitoso + 1.
 * Hereda id, fechas de auditoría y version desde BaseEntity.
 */
@Entity
@Table(name = "registros_blockchain")
public class RegistroBlockchain extends BaseEntity {

    private static final BigDecimal WEI_POR_ETH = new BigDecimal("1000000000000000000");

    @Column(name = "desde_numero", nullable = false, unique = false)
    private Long desdeNumero;

    @Column(name = "hasta_numero", nullable = false, unique = false)
    private Long hastaNumero;

    // Hash del evento hastaNumero: 64 hexadecimales en minúscula, sin 0x (lo agrega el anclaje).
    @Column(name = "hash_anclado", nullable = false, length = 66, unique = false)
    private String hashAnclado;

    @Column(name = "red", nullable = false, length = 50, unique = false)
    private String red;

    @Column(name = "direccion_contrato", nullable = true, length = 42, unique = false)
    private String direccionContrato;

    // nullable = true: se conoce al firmar la transacción (antes de transmitirla).
    @Column(name = "transaction_hash", nullable = true, length = 66, unique = false)
    private String transactionHash;

    // nullable = true: nonce de la billetera de la transacción vigente (se conoce al firmar).
    @Column(name = "nonce", nullable = true, unique = false)
    private Long nonce;

    // nullable = true: límite de gas de la transacción vigente.
    @Column(name = "limite_gas", nullable = true, unique = false)
    private Long limiteGas;

    // nullable = true: comisión máxima por unidad de gas de la transacción vigente, en wei.
    @Column(name = "comision_maxima_wei", nullable = true, unique = false)
    private Long comisionMaximaWei;

    // nullable = true: propina por unidad de gas de la transacción vigente, en wei.
    @Column(name = "propina_wei", nullable = true, unique = false)
    private Long propinaWei;

    // nullable = true: solo tiene valor una vez que la transacción se incluye en un bloque.
    @Column(name = "bloque", nullable = true, unique = false)
    private Long bloque;

    @Column(name = "confirmaciones", nullable = false, unique = false)
    private Integer confirmaciones;

    // nullable = true: gas consumido y precio pagado, del recibo de la transacción.
    @Column(name = "gas_usado", nullable = true, unique = false)
    private Long gasUsado;

    @Column(name = "precio_efectivo_wei", nullable = true, unique = false)
    private Long precioEfectivoWei;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 30, unique = false)
    private EstadoAnclaje estado;

    @Column(name = "intentos", nullable = false, unique = false)
    private Integer intentos;

    // nullable = true: solo en FALLIDO. Null en los FALLIDO anteriores a esta columna (se tratan como determinísticos).
    @Enumerated(EnumType.STRING)
    @Column(name = "causa_fallo", nullable = true, length = 30, unique = false)
    private CausaFallo causaFallo;

    // nullable = true: último error (ya saneado: sin URL del RPC ni clave).
    @Column(name = "ultimo_error", nullable = true, columnDefinition = "TEXT")
    private String ultimoError;

    // nullable = true: cuándo reintentar después de un envío fallido.
    @Column(name = "proximo_intento", nullable = true, unique = false)
    private Instant proximoIntento;

    // nullable = true: cuándo se transmitió la transacción vigente (UTC).
    @Column(name = "fecha_envio", nullable = true, unique = false)
    private Instant fechaEnvio;

    // nullable = true: cuándo alcanzó las confirmaciones necesarias (UTC).
    @Column(name = "fecha_confirmacion", nullable = true, unique = false)
    private Instant fechaConfirmacion;

    /** Constructor vacío exigido por JPA. */
    protected RegistroBlockchain() {
    }

    /**
     * Abre un anclaje PENDIENTE del evento hastaNumero con su hash, sin
     * transacción todavía, con cero intentos y cero confirmaciones.
     */
    public RegistroBlockchain(Long desdeNumero, Long hastaNumero, String hashAnclado, String red,
                              String direccionContrato) {
        this.desdeNumero = desdeNumero;
        this.hastaNumero = hastaNumero;
        this.hashAnclado = hashAnclado;
        this.red = red;
        this.direccionContrato = direccionContrato;
        this.estado = EstadoAnclaje.PENDIENTE;
        this.confirmaciones = 0;
        this.intentos = 0;
    }

    // ---------- Métodos de dominio ----------

    /** true si está PENDIENTE o ENVIADO (todavía no terminó). */
    public boolean estaEnCurso() {
        return estado == EstadoAnclaje.PENDIENTE || estado == EstadoAnclaje.ENVIADO;
    }

    /** true si se puede intentar ahora (no hay una espera programada o ya venció). */
    public boolean puedeIntentarse(Instant ahora) {
        return proximoIntento == null || !ahora.isBefore(proximoIntento);
    }

    /** Empieza un intento de envío (o de reemplazo): suma uno a intentos. PENDIENTE o ENVIADO. */
    public void iniciarIntento() {
        exigirEstado(Set.of(EstadoAnclaje.PENDIENTE, EstadoAnclaje.ENVIADO), "intentar el envío");
        this.intentos = this.intentos + 1;
    }

    /**
     * Guarda la transacción firmada (todavía PENDIENTE): se persiste ANTES de
     * transmitirla, para poder reconocerla si la app se reinicia a mitad de camino.
     */
    public void registrarTransaccion(TransaccionFirmada transaccion) {
        exigirEstado(Set.of(EstadoAnclaje.PENDIENTE), "registrar la transacción");
        aplicarTransaccion(transaccion);
    }

    /** PENDIENTE → ENVIADO: la red aceptó la transacción. */
    public void marcarEnviado(Instant ahora) {
        exigirEstado(Set.of(EstadoAnclaje.PENDIENTE), "marcar como enviado");
        if (transactionHash == null) {
            throw new ReglaNegocioException("TRANSICION_INVALIDA", "No hay transacción firmada para marcar como enviada");
        }
        this.estado = EstadoAnclaje.ENVIADO;
        this.fechaEnvio = ahora;
        this.proximoIntento = null;
        this.ultimoError = null;
    }

    /** ENVIADO → ENVIADO: la transacción de reemplazo (mismo nonce, más comisión) fue aceptada. */
    public void registrarReemplazo(TransaccionFirmada transaccion, Instant ahora) {
        exigirEstado(Set.of(EstadoAnclaje.ENVIADO), "reemplazar la transacción");
        aplicarTransaccion(transaccion);
        this.bloque = null;
        this.confirmaciones = 0;
        this.fechaEnvio = ahora;
        this.proximoIntento = null;
        this.ultimoError = null;
    }

    /**
     * Un intento falló (error ya saneado). Si se llegó a maxIntentos → FALLIDO;
     * si no, queda en su estado esperando hasta proximoIntento.
     */
    public void registrarFallo(String error, Instant proximoIntento, int maxIntentos) {
        exigirEstado(Set.of(EstadoAnclaje.PENDIENTE, EstadoAnclaje.ENVIADO), "registrar un fallo");
        this.ultimoError = error;
        if (intentos >= maxIntentos) {
            this.estado = EstadoAnclaje.FALLIDO;
            this.causaFallo = CausaFallo.ERROR_DE_RED;
            this.proximoIntento = null;
        } else {
            this.proximoIntento = proximoIntento;
        }
    }

    /**
     * La transacción está en un bloque: guarda bloque, gas y confirmaciones
     * (bloques construidos encima). Con las requeridas → CONFIRMADO.
     */
    public void registrarInclusion(ReciboTransaccion recibo, int confirmacionesActuales, int requeridas,
                                   Instant ahora) {
        exigirEstado(Set.of(EstadoAnclaje.ENVIADO), "registrar la inclusión en un bloque");
        this.bloque = recibo.getBloque();
        this.gasUsado = recibo.getGasUsado();
        this.precioEfectivoWei = recibo.getPrecioEfectivoWei();
        this.confirmaciones = Math.max(0, confirmacionesActuales);
        if (this.confirmaciones >= requeridas) {
            this.estado = EstadoAnclaje.CONFIRMADO;
            this.fechaConfirmacion = ahora;
        }
    }

    /** El recibo desapareció (reorganización de la cadena): vuelve a esperar la inclusión. */
    public void registrarSinInclusion() {
        exigirEstado(Set.of(EstadoAnclaje.ENVIADO), "registrar la falta de inclusión");
        this.bloque = null;
        this.confirmaciones = 0;
    }

    /** PENDIENTE o ENVIADO → FALLIDO, con el motivo y su causa (por ejemplo REVERT o SIN_GAS). */
    public void marcarFallido(String error, CausaFallo causa) {
        exigirEstado(Set.of(EstadoAnclaje.PENDIENTE, EstadoAnclaje.ENVIADO), "marcar como fallido");
        this.estado = EstadoAnclaje.FALLIDO;
        this.causaFallo = causa;
        this.ultimoError = error;
        this.proximoIntento = null;
    }

    /**
     * true si este anclaje FALLIDO frena la tarea automática: su causa es
     * determinística (reintentar igual volvería a fallar y gastaría gas). Un
     * FALLIDO sin causa es anterior a la columna causa_fallo y se trata igual
     * (los 6 de out of gas del 06/10/2026). Nunca frena el anclaje manual.
     */
    public boolean frenaLaTareaAutomatica() {
        return estado == EstadoAnclaje.FALLIDO && (causaFallo == null || causaFallo.frenaLaTarea());
    }

    /** Costo real del anclaje en ETH (gas usado × precio efectivo), o null si todavía no se incluyó. */
    public BigDecimal costoEth() {
        if (gasUsado == null || precioEfectivoWei == null) {
            return null;
        }
        BigDecimal wei = new BigDecimal(BigInteger.valueOf(gasUsado).multiply(BigInteger.valueOf(precioEfectivoWei)));
        return wei.divide(WEI_POR_ETH).stripTrailingZeros();
    }

    /**
     * URL de la transacción de anclaje en el explorador Etherscan de la red.
     * Null si todavía no hay transacción firmada.
     */
    public String enlaceEtherscan() {
        if (transactionHash == null || transactionHash.isBlank()) {
            return null;
        }
        return urlEtherscan(red) + "/tx/" + transactionHash;
    }

    /** URL base de Etherscan para una red ("sepolia" → https://sepolia.etherscan.io). */
    public static String urlEtherscan(String red) {
        String subdominio = (red == null || red.isBlank() || red.equalsIgnoreCase("mainnet"))
                ? "" : red.toLowerCase() + ".";
        return "https://" + subdominio + "etherscan.io";
    }

    /** Copia los datos de la transacción firmada vigente. */
    private void aplicarTransaccion(TransaccionFirmada transaccion) {
        this.transactionHash = transaccion.getHash();
        this.nonce = transaccion.getNonce().longValueExact();
        this.limiteGas = transaccion.getLimiteGas().longValueExact();
        this.comisionMaximaWei = transaccion.getComisionMaximaWei().longValueExact();
        this.propinaWei = transaccion.getPropinaWei().longValueExact();
    }

    /** Exige uno de los estados dados; si no, TRANSICION_INVALIDA. */
    private void exigirEstado(Set<EstadoAnclaje> permitidos, String accion) {
        if (!permitidos.contains(estado)) {
            throw new ReglaNegocioException("TRANSICION_INVALIDA",
                    "No se puede " + accion + " un anclaje en estado " + estado);
        }
    }

    // ---------- Getters (sin setters: el estado cambia solo por los métodos de dominio) ----------

    /** Devuelve el primer evento que este anclaje cubre por primera vez. */
    public Long getDesdeNumero() {
        return desdeNumero;
    }

    /** Devuelve el número del evento cuyo hash se ancla. */
    public Long getHastaNumero() {
        return hastaNumero;
    }

    /** Devuelve el hash anclado (64 hexadecimales, sin 0x). */
    public String getHashAnclado() {
        return hashAnclado;
    }

    /** Devuelve la red (sepolia). */
    public String getRed() {
        return red;
    }

    /** Devuelve la dirección del contrato. */
    public String getDireccionContrato() {
        return direccionContrato;
    }

    /** Devuelve el hash de la transacción vigente (0x + 64 hexadecimales). */
    public String getTransactionHash() {
        return transactionHash;
    }

    /** Devuelve el nonce de la transacción vigente. */
    public Long getNonce() {
        return nonce;
    }

    /** Devuelve el límite de gas de la transacción vigente. */
    public Long getLimiteGas() {
        return limiteGas;
    }

    /** Devuelve la comisión máxima por unidad de gas de la transacción vigente, en wei. */
    public Long getComisionMaximaWei() {
        return comisionMaximaWei;
    }

    /** Devuelve la propina por unidad de gas de la transacción vigente, en wei. */
    public Long getPropinaWei() {
        return propinaWei;
    }

    /** Devuelve el bloque que incluyó la transacción. */
    public Long getBloque() {
        return bloque;
    }

    /** Devuelve las confirmaciones (bloques construidos encima). */
    public Integer getConfirmaciones() {
        return confirmaciones;
    }

    /** Devuelve el gas consumido según el recibo. */
    public Long getGasUsado() {
        return gasUsado;
    }

    /** Devuelve el precio efectivo pagado por unidad de gas, en wei. */
    public Long getPrecioEfectivoWei() {
        return precioEfectivoWei;
    }

    /** Devuelve el estado del anclaje. */
    public EstadoAnclaje getEstado() {
        return estado;
    }

    /** Devuelve la cantidad de intentos de envío realizados. */
    public Integer getIntentos() {
        return intentos;
    }

    /** Devuelve la causa del FALLIDO (null si no falló, o si es anterior a esta columna). */
    public CausaFallo getCausaFallo() {
        return causaFallo;
    }

    /** Devuelve el último error (saneado), si hubo. */
    public String getUltimoError() {
        return ultimoError;
    }

    /** Devuelve cuándo se reintenta, si hay una espera programada. */
    public Instant getProximoIntento() {
        return proximoIntento;
    }

    /** Devuelve cuándo se transmitió la transacción vigente. */
    public Instant getFechaEnvio() {
        return fechaEnvio;
    }

    /** Devuelve cuándo alcanzó las confirmaciones necesarias. */
    public Instant getFechaConfirmacion() {
        return fechaConfirmacion;
    }
}
