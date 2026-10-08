package com.medichain.modules.unidadtrazable;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO de salida AnclajeDTO en MediChain (R15).
 * Anclaje en blockchain que cubre el último hito del recorrido público de
 * la caja: estado (CONFIRMADO, ENVIADO, PENDIENTE si todavía no se ancló,
 * NO_DISPONIBLE si el anclaje está deshabilitado), red, transacción,
 * bloque y enlace a Etherscan para comprobarlo de forma independiente.
 */
public class AnclajeDTO {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String estado;
    @Schema(nullable = true)
    private String red;
    @Schema(nullable = true)
    private String transactionHash;
    @Schema(nullable = true)
    private Long bloque;
    @Schema(nullable = true)
    private String enlace;

    /** Constructor vacío exigido por Jackson. */
    public AnclajeDTO() {
    }

    /** Anclaje con solo el estado (los demás campos se completan si hay transacción). */
    public static AnclajeDTO conEstado(String estado) {
        AnclajeDTO anclaje = new AnclajeDTO();
        anclaje.setEstado(estado);
        return anclaje;
    }

    /** Devuelve el estado del anclaje. */
    public String getEstado() {
        return estado;
    }

    /** Establece el estado del anclaje. */
    public void setEstado(String estado) {
        this.estado = estado;
    }

    /** Devuelve la red (por ejemplo sepolia). */
    public String getRed() {
        return red;
    }

    /** Establece la red. */
    public void setRed(String red) {
        this.red = red;
    }

    /** Devuelve el hash de la transacción. */
    public String getTransactionHash() {
        return transactionHash;
    }

    /** Establece el hash de la transacción. */
    public void setTransactionHash(String transactionHash) {
        this.transactionHash = transactionHash;
    }

    /** Devuelve el número de bloque. */
    public Long getBloque() {
        return bloque;
    }

    /** Establece el número de bloque. */
    public void setBloque(Long bloque) {
        this.bloque = bloque;
    }

    /** Devuelve el enlace al explorador. */
    public String getEnlace() {
        return enlace;
    }

    /** Establece el enlace al explorador. */
    public void setEnlace(String enlace) {
        this.enlace = enlace;
    }
}
