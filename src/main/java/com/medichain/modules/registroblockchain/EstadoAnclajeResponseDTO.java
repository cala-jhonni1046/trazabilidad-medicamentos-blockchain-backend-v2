package com.medichain.modules.registroblockchain;

/**
 * DTO de salida EstadoAnclajeResponseDTO en MediChain (R15).
 * Tablero del anclaje para la Sede y los inspectores: red, contrato y
 * billetera (con enlaces a Etherscan), saldo, comisión actual, gas REAL del
 * próximo anclaje (eth_estimateGas: el primero de un contrato cuesta más que
 * los siguientes), límite de gas y su piso y techo, costo, cuántos anclajes
 * alcanzan, saldo mínimo de la tarea automática y si está frenada, último
 * evento local y anclado, y el anclaje en curso. Nunca incluye la URL del
 * RPC ni la clave.
 */
public class EstadoAnclajeResponseDTO {

    private boolean habilitado;
    private String red;
    private Long chainId;
    private String contrato;
    private String enlaceContrato;
    private String billetera;
    private String enlaceBilletera;
    private String saldoEth;
    private String comisionActualGwei;
    private Long gasPorAnclaje;
    private Boolean primerAnclaje;
    private Long limiteGas;
    private Long gasMinimo;
    private Long gasMaximo;
    private String costoPorAnclajeEth;
    private Long anclajesEstimados;
    private String saldoMinimoEth;
    private boolean anclajeAutomaticoFrenado;
    private Long ultimoNumeroLocal;
    private Long ultimoNumeroAnclado;
    private Long eventosSinAnclar;
    private RegistroBlockchainResponseDTO ultimoAnclaje;
    private RegistroBlockchainResponseDTO anclajeEnCurso;
    private String mensaje;

    /** Constructor vacío exigido por Jackson. */
    public EstadoAnclajeResponseDTO() {
    }

    /** Devuelve si el anclaje está habilitado. */
    public boolean isHabilitado() {
        return habilitado;
    }

    /** Establece si el anclaje está habilitado. */
    public void setHabilitado(boolean habilitado) {
        this.habilitado = habilitado;
    }

    /** Devuelve la red. */
    public String getRed() {
        return red;
    }

    /** Establece la red. */
    public void setRed(String red) {
        this.red = red;
    }

    /** Devuelve el chainId de la red. */
    public Long getChainId() {
        return chainId;
    }

    /** Establece el chainId de la red. */
    public void setChainId(Long chainId) {
        this.chainId = chainId;
    }

    /** Devuelve la dirección del contrato. */
    public String getContrato() {
        return contrato;
    }

    /** Establece la dirección del contrato. */
    public void setContrato(String contrato) {
        this.contrato = contrato;
    }

    /** Devuelve el enlace al contrato en Etherscan. */
    public String getEnlaceContrato() {
        return enlaceContrato;
    }

    /** Establece el enlace al contrato en Etherscan. */
    public void setEnlaceContrato(String enlaceContrato) {
        this.enlaceContrato = enlaceContrato;
    }

    /** Devuelve la dirección pública de la billetera. */
    public String getBilletera() {
        return billetera;
    }

    /** Establece la dirección pública de la billetera. */
    public void setBilletera(String billetera) {
        this.billetera = billetera;
    }

    /** Devuelve el enlace a la billetera en Etherscan. */
    public String getEnlaceBilletera() {
        return enlaceBilletera;
    }

    /** Establece el enlace a la billetera en Etherscan. */
    public void setEnlaceBilletera(String enlaceBilletera) {
        this.enlaceBilletera = enlaceBilletera;
    }

    /** Devuelve el saldo de la billetera, en ETH. */
    public String getSaldoEth() {
        return saldoEth;
    }

    /** Establece el saldo de la billetera, en ETH. */
    public void setSaldoEth(String saldoEth) {
        this.saldoEth = saldoEth;
    }

    /** Devuelve la comisión actual de la red (base + propina), en gwei por unidad de gas. */
    public String getComisionActualGwei() {
        return comisionActualGwei;
    }

    /** Establece la comisión actual de la red (base + propina), en gwei por unidad de gas. */
    public void setComisionActualGwei(String comisionActualGwei) {
        this.comisionActualGwei = comisionActualGwei;
    }

    /** Devuelve el gas del próximo anclaje según eth_estimateGas (real, con la red de ahora). */
    public Long getGasPorAnclaje() {
        return gasPorAnclaje;
    }

    /** Establece el gas del próximo anclaje según eth_estimateGas (real, con la red de ahora). */
    public void setGasPorAnclaje(Long gasPorAnclaje) {
        this.gasPorAnclaje = gasPorAnclaje;
    }

    /** Devuelve si el próximo es el primer anclaje del contrato (crea la lista: cuesta más que los siguientes). */
    public Boolean getPrimerAnclaje() {
        return primerAnclaje;
    }

    /** Establece si el próximo es el primer anclaje del contrato (crea la lista: cuesta más que los siguientes). */
    public void setPrimerAnclaje(Boolean primerAnclaje) {
        this.primerAnclaje = primerAnclaje;
    }

    /** Devuelve el límite de gas que se usaría: estimación + 30 %, al menos gas-minimo. */
    public Long getLimiteGas() {
        return limiteGas;
    }

    /** Establece el límite de gas que se usaría: estimación + 30 %, al menos gas-minimo. */
    public void setLimiteGas(Long limiteGas) {
        this.limiteGas = limiteGas;
    }

    /** Devuelve el piso del límite de gas (medichain.anclaje.gas-minimo). */
    public Long getGasMinimo() {
        return gasMinimo;
    }

    /** Establece el piso del límite de gas (medichain.anclaje.gas-minimo). */
    public void setGasMinimo(Long gasMinimo) {
        this.gasMinimo = gasMinimo;
    }

    /** Devuelve el techo del límite de gas (medichain.anclaje.gas-maximo): por encima no se envía. */
    public Long getGasMaximo() {
        return gasMaximo;
    }

    /** Establece el techo del límite de gas (medichain.anclaje.gas-maximo): por encima no se envía. */
    public void setGasMaximo(Long gasMaximo) {
        this.gasMaximo = gasMaximo;
    }

    /** Devuelve el costo estimado del próximo anclaje (gas estimado × comisión actual), en ETH. */
    public String getCostoPorAnclajeEth() {
        return costoPorAnclajeEth;
    }

    /** Establece el costo estimado del próximo anclaje (gas estimado × comisión actual), en ETH. */
    public void setCostoPorAnclajeEth(String costoPorAnclajeEth) {
        this.costoPorAnclajeEth = costoPorAnclajeEth;
    }

    /** Devuelve cuántos anclajes de ese costo alcanzan con el saldo. */
    public Long getAnclajesEstimados() {
        return anclajesEstimados;
    }

    /** Establece cuántos anclajes de ese costo alcanzan con el saldo. */
    public void setAnclajesEstimados(Long anclajesEstimados) {
        this.anclajesEstimados = anclajesEstimados;
    }

    /** Devuelve el saldo mínimo para que la tarea automática ancle (saldo-minimo-anclajes anclajes), en ETH. */
    public String getSaldoMinimoEth() {
        return saldoMinimoEth;
    }

    /** Establece el saldo mínimo para que la tarea automática ancle (saldo-minimo-anclajes anclajes), en ETH. */
    public void setSaldoMinimoEth(String saldoMinimoEth) {
        this.saldoMinimoEth = saldoMinimoEth;
    }

    /** Devuelve si la tarea automática está frenada (por un FALLIDO determinístico o por saldo). */
    public boolean isAnclajeAutomaticoFrenado() {
        return anclajeAutomaticoFrenado;
    }

    /** Establece si la tarea automática está frenada (por un FALLIDO determinístico o por saldo). */
    public void setAnclajeAutomaticoFrenado(boolean anclajeAutomaticoFrenado) {
        this.anclajeAutomaticoFrenado = anclajeAutomaticoFrenado;
    }

    /** Devuelve el último evento de la cadena local. */
    public Long getUltimoNumeroLocal() {
        return ultimoNumeroLocal;
    }

    /** Establece el último evento de la cadena local. */
    public void setUltimoNumeroLocal(Long ultimoNumeroLocal) {
        this.ultimoNumeroLocal = ultimoNumeroLocal;
    }

    /** Devuelve el último evento anclado en el contrato. */
    public Long getUltimoNumeroAnclado() {
        return ultimoNumeroAnclado;
    }

    /** Establece el último evento anclado en el contrato. */
    public void setUltimoNumeroAnclado(Long ultimoNumeroAnclado) {
        this.ultimoNumeroAnclado = ultimoNumeroAnclado;
    }

    /** Devuelve cuántos eventos todavía no están anclados. */
    public Long getEventosSinAnclar() {
        return eventosSinAnclar;
    }

    /** Establece cuántos eventos todavía no están anclados. */
    public void setEventosSinAnclar(Long eventosSinAnclar) {
        this.eventosSinAnclar = eventosSinAnclar;
    }

    /** Devuelve el último anclaje ENVIADO o CONFIRMADO. */
    public RegistroBlockchainResponseDTO getUltimoAnclaje() {
        return ultimoAnclaje;
    }

    /** Establece el último anclaje ENVIADO o CONFIRMADO. */
    public void setUltimoAnclaje(RegistroBlockchainResponseDTO ultimoAnclaje) {
        this.ultimoAnclaje = ultimoAnclaje;
    }

    /** Devuelve el anclaje PENDIENTE o ENVIADO en curso. */
    public RegistroBlockchainResponseDTO getAnclajeEnCurso() {
        return anclajeEnCurso;
    }

    /** Establece el anclaje PENDIENTE o ENVIADO en curso. */
    public void setAnclajeEnCurso(RegistroBlockchainResponseDTO anclajeEnCurso) {
        this.anclajeEnCurso = anclajeEnCurso;
    }

    /** Devuelve avisos: anclaje deshabilitado, frenos de la tarea automática, gas sobre el máximo o red sin respuesta. */
    public String getMensaje() {
        return mensaje;
    }

    /** Establece avisos: anclaje deshabilitado, frenos de la tarea automática, gas sobre el máximo o red sin respuesta. */
    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }
}
