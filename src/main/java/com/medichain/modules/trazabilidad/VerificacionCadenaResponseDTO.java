package com.medichain.modules.trazabilidad;

import com.medichain.modules.registroblockchain.VerificacionBlockchainDTO;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO de salida VerificacionCadenaResponseDTO en MediChain.
 * Resultado de recorrer y recalcular la cadena de eventos completa
 * (integra, eventosVerificados, primerNumeroRoto, motivo: SOLO la cadena
 * local), más la comparación con los anclajes de Sepolia (blockchain) y un
 * resumen de ambas en una frase. Una cadena puede estar íntegra localmente
 * y ALTERADA según la blockchain: alguien recalculó los hashes.
 */
public class VerificacionCadenaResponseDTO {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean integra;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private long eventosVerificados;
    @Schema(nullable = true)
    private Long primerNumeroRoto;
    @Schema(nullable = true)
    private String motivo;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private VerificacionBlockchainDTO blockchain;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String resumen;

    /** Constructor vacío exigido por Jackson. */
    public VerificacionCadenaResponseDTO() {
    }

    /** Crea el resultado con todos sus campos. */
    public VerificacionCadenaResponseDTO(boolean integra, long eventosVerificados, Long primerNumeroRoto,
                                         String motivo) {
        this.integra = integra;
        this.eventosVerificados = eventosVerificados;
        this.primerNumeroRoto = primerNumeroRoto;
        this.motivo = motivo;
    }

    /** Indica si la cadena está íntegra. */
    public boolean isIntegra() {
        return integra;
    }

    /** Establece si la cadena está íntegra. */
    public void setIntegra(boolean integra) {
        this.integra = integra;
    }

    /** Devuelve cuántos eventos se verificaron correctamente antes de terminar. */
    public long getEventosVerificados() {
        return eventosVerificados;
    }

    /** Establece cuántos eventos se verificaron. */
    public void setEventosVerificados(long eventosVerificados) {
        this.eventosVerificados = eventosVerificados;
    }

    /** Devuelve el número del primer evento con problema, o null si está íntegra. */
    public Long getPrimerNumeroRoto() {
        return primerNumeroRoto;
    }

    /** Establece el número del primer evento con problema. */
    public void setPrimerNumeroRoto(Long primerNumeroRoto) {
        this.primerNumeroRoto = primerNumeroRoto;
    }

    /** Devuelve la explicación del problema, o null si está íntegra. */
    public String getMotivo() {
        return motivo;
    }

    /** Establece la explicación del problema. */
    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    /** Devuelve la comparación con los anclajes de la blockchain. */
    public VerificacionBlockchainDTO getBlockchain() {
        return blockchain;
    }

    /** Establece la comparación con los anclajes de la blockchain. */
    public void setBlockchain(VerificacionBlockchainDTO blockchain) {
        this.blockchain = blockchain;
    }

    /** Devuelve el resumen en una frase (local + blockchain). */
    public String getResumen() {
        return resumen;
    }

    /** Establece el resumen en una frase. */
    public void setResumen(String resumen) {
        this.resumen = resumen;
    }
}
