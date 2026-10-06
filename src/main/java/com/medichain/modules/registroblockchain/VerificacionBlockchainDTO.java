package com.medichain.modules.registroblockchain;

/**
 * DTO de salida VerificacionBlockchainDTO en MediChain (R15).
 * Parte "blockchain" de la verificación de la cadena: compara cada
 * anclaje guardado EN EL CONTRATO (no en la base) con el evento local de
 * ese número. alteradoDesde..alteradoHasta es el rango donde está la
 * alteración (entre el anclaje anterior que coincide y el primero que no).
 * eventosSinAnclar: los últimos eventos, protegidos todavía solo por la
 * cadena local (la ventana hasta el próximo anclaje).
 */
public class VerificacionBlockchainDTO {

    private EstadoVerificacionBlockchain estado;
    private String red;
    private String contrato;
    private long anclajesVerificados;
    private Long ultimoNumeroAnclado;
    private Long eventosSinAnclar;
    private Long alteradoDesde;
    private Long alteradoHasta;
    private String motivo;

    /** Constructor vacío exigido por Jackson. */
    public VerificacionBlockchainDTO() {
    }

    /** Crea el resultado con su estado y el motivo (o null). */
    public VerificacionBlockchainDTO(EstadoVerificacionBlockchain estado, String motivo) {
        this.estado = estado;
        this.motivo = motivo;
    }

    /** Devuelve el estado de la verificación contra la blockchain. */
    public EstadoVerificacionBlockchain getEstado() {
        return estado;
    }

    /** Establece el estado de la verificación contra la blockchain. */
    public void setEstado(EstadoVerificacionBlockchain estado) {
        this.estado = estado;
    }

    /** Devuelve la red consultada. */
    public String getRed() {
        return red;
    }

    /** Establece la red consultada. */
    public void setRed(String red) {
        this.red = red;
    }

    /** Devuelve la dirección del contrato consultado. */
    public String getContrato() {
        return contrato;
    }

    /** Establece la dirección del contrato consultado. */
    public void setContrato(String contrato) {
        this.contrato = contrato;
    }

    /** Devuelve cuántos anclajes del contrato coincidieron con la cadena local. */
    public long getAnclajesVerificados() {
        return anclajesVerificados;
    }

    /** Establece cuántos anclajes coincidieron. */
    public void setAnclajesVerificados(long anclajesVerificados) {
        this.anclajesVerificados = anclajesVerificados;
    }

    /** Devuelve el último número anclado en el contrato. */
    public Long getUltimoNumeroAnclado() {
        return ultimoNumeroAnclado;
    }

    /** Establece el último número anclado en el contrato. */
    public void setUltimoNumeroAnclado(Long ultimoNumeroAnclado) {
        this.ultimoNumeroAnclado = ultimoNumeroAnclado;
    }

    /** Devuelve cuántos eventos locales todavía no están cubiertos por un anclaje. */
    public Long getEventosSinAnclar() {
        return eventosSinAnclar;
    }

    /** Establece cuántos eventos locales todavía no están anclados. */
    public void setEventosSinAnclar(Long eventosSinAnclar) {
        this.eventosSinAnclar = eventosSinAnclar;
    }

    /** Devuelve el primer evento del rango alterado (o null). */
    public Long getAlteradoDesde() {
        return alteradoDesde;
    }

    /** Establece el primer evento del rango alterado. */
    public void setAlteradoDesde(Long alteradoDesde) {
        this.alteradoDesde = alteradoDesde;
    }

    /** Devuelve el último evento del rango alterado: el anclaje que no coincide (o null). */
    public Long getAlteradoHasta() {
        return alteradoHasta;
    }

    /** Establece el último evento del rango alterado. */
    public void setAlteradoHasta(Long alteradoHasta) {
        this.alteradoHasta = alteradoHasta;
    }

    /** Devuelve la explicación (o null si está verificada). */
    public String getMotivo() {
        return motivo;
    }

    /** Establece la explicación. */
    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }
}
