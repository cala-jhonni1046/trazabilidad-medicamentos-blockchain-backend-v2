package com.medichain.modules.registroblockchain;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO de salida RegistroBlockchainResponseDTO en MediChain.
 * Estado completo de un anclaje: rango de eventos, hash anclado,
 * transacción vigente (hash, nonce), bloque, confirmaciones, gas y costo
 * real, intentos y el enlace a Etherscan. ultimoError ya viene saneado.
 */
public class RegistroBlockchainResponseDTO {

    private UUID id;
    private Instant fechaCreacion;
    private Instant fechaActualizacion;
    private Long version;
    private Long desdeNumero;
    private Long hastaNumero;
    private String hashAnclado;
    private String red;
    private String direccionContrato;
    private String transactionHash;
    private Long nonce;
    private Long bloque;
    private Integer confirmaciones;
    private Long gasUsado;
    private String costoEth;
    private EstadoAnclaje estado;
    private Integer intentos;
    private String ultimoError;
    private LocalDateTime proximoIntento;
    private LocalDateTime fechaEnvio;
    private LocalDateTime fechaConfirmacion;
    private String enlaceEtherscan;

    /** Constructor vacío exigido por Jackson. */
    public RegistroBlockchainResponseDTO() {
    }

    /** Devuelve el id del anclaje. */
    public UUID getId() {
        return id;
    }

    /** Establece el id del anclaje. */
    public void setId(UUID id) {
        this.id = id;
    }

    /** Devuelve la fecha de creación. */
    public Instant getFechaCreacion() {
        return fechaCreacion;
    }

    /** Establece la fecha de creación. */
    public void setFechaCreacion(Instant fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    /** Devuelve la fecha de la última actualización. */
    public Instant getFechaActualizacion() {
        return fechaActualizacion;
    }

    /** Establece la fecha de la última actualización. */
    public void setFechaActualizacion(Instant fechaActualizacion) {
        this.fechaActualizacion = fechaActualizacion;
    }

    /** Devuelve la versión (locking optimista). */
    public Long getVersion() {
        return version;
    }

    /** Establece la versión (locking optimista). */
    public void setVersion(Long version) {
        this.version = version;
    }

    /** Devuelve el primer evento que este anclaje cubre por primera vez. */
    public Long getDesdeNumero() {
        return desdeNumero;
    }

    /** Establece el primer evento que este anclaje cubre por primera vez. */
    public void setDesdeNumero(Long desdeNumero) {
        this.desdeNumero = desdeNumero;
    }

    /** Devuelve el número del evento cuyo hash se ancla. */
    public Long getHastaNumero() {
        return hastaNumero;
    }

    /** Establece el número del evento cuyo hash se ancla. */
    public void setHastaNumero(Long hastaNumero) {
        this.hastaNumero = hastaNumero;
    }

    /** Devuelve el hash anclado (64 hexadecimales). */
    public String getHashAnclado() {
        return hashAnclado;
    }

    /** Establece el hash anclado (64 hexadecimales). */
    public void setHashAnclado(String hashAnclado) {
        this.hashAnclado = hashAnclado;
    }

    /** Devuelve la red. */
    public String getRed() {
        return red;
    }

    /** Establece la red. */
    public void setRed(String red) {
        this.red = red;
    }

    /** Devuelve la dirección del contrato. */
    public String getDireccionContrato() {
        return direccionContrato;
    }

    /** Establece la dirección del contrato. */
    public void setDireccionContrato(String direccionContrato) {
        this.direccionContrato = direccionContrato;
    }

    /** Devuelve el hash de la transacción vigente. */
    public String getTransactionHash() {
        return transactionHash;
    }

    /** Establece el hash de la transacción vigente. */
    public void setTransactionHash(String transactionHash) {
        this.transactionHash = transactionHash;
    }

    /** Devuelve el nonce de la transacción vigente. */
    public Long getNonce() {
        return nonce;
    }

    /** Establece el nonce de la transacción vigente. */
    public void setNonce(Long nonce) {
        this.nonce = nonce;
    }

    /** Devuelve el bloque que incluyó la transacción. */
    public Long getBloque() {
        return bloque;
    }

    /** Establece el bloque que incluyó la transacción. */
    public void setBloque(Long bloque) {
        this.bloque = bloque;
    }

    /** Devuelve las confirmaciones (bloques encima). */
    public Integer getConfirmaciones() {
        return confirmaciones;
    }

    /** Establece las confirmaciones (bloques encima). */
    public void setConfirmaciones(Integer confirmaciones) {
        this.confirmaciones = confirmaciones;
    }

    /** Devuelve el gas consumido. */
    public Long getGasUsado() {
        return gasUsado;
    }

    /** Establece el gas consumido. */
    public void setGasUsado(Long gasUsado) {
        this.gasUsado = gasUsado;
    }

    /** Devuelve el costo real en ETH (gas usado × precio efectivo). */
    public String getCostoEth() {
        return costoEth;
    }

    /** Establece el costo real en ETH (gas usado × precio efectivo). */
    public void setCostoEth(String costoEth) {
        this.costoEth = costoEth;
    }

    /** Devuelve el estado del anclaje. */
    public EstadoAnclaje getEstado() {
        return estado;
    }

    /** Establece el estado del anclaje. */
    public void setEstado(EstadoAnclaje estado) {
        this.estado = estado;
    }

    /** Devuelve los intentos de envío. */
    public Integer getIntentos() {
        return intentos;
    }

    /** Establece los intentos de envío. */
    public void setIntentos(Integer intentos) {
        this.intentos = intentos;
    }

    /** Devuelve el último error (saneado). */
    public String getUltimoError() {
        return ultimoError;
    }

    /** Establece el último error (saneado). */
    public void setUltimoError(String ultimoError) {
        this.ultimoError = ultimoError;
    }

    /** Devuelve cuándo se reintenta. */
    public LocalDateTime getProximoIntento() {
        return proximoIntento;
    }

    /** Establece cuándo se reintenta. */
    public void setProximoIntento(LocalDateTime proximoIntento) {
        this.proximoIntento = proximoIntento;
    }

    /** Devuelve cuándo se transmitió la transacción vigente. */
    public LocalDateTime getFechaEnvio() {
        return fechaEnvio;
    }

    /** Establece cuándo se transmitió la transacción vigente. */
    public void setFechaEnvio(LocalDateTime fechaEnvio) {
        this.fechaEnvio = fechaEnvio;
    }

    /** Devuelve cuándo se confirmó. */
    public LocalDateTime getFechaConfirmacion() {
        return fechaConfirmacion;
    }

    /** Establece cuándo se confirmó. */
    public void setFechaConfirmacion(LocalDateTime fechaConfirmacion) {
        this.fechaConfirmacion = fechaConfirmacion;
    }

    /** Devuelve el enlace a la transacción en Etherscan. */
    public String getEnlaceEtherscan() {
        return enlaceEtherscan;
    }

    /** Establece el enlace a la transacción en Etherscan. */
    public void setEnlaceEtherscan(String enlaceEtherscan) {
        this.enlaceEtherscan = enlaceEtherscan;
    }
}
