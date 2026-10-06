package com.medichain.modules.registroblockchain;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

/**
 * Implementación ClienteBlockchainDeshabilitado en MediChain.
 * Se usa cuando ANCLAJE_HABILITADO=false (el valor por defecto): no se
 * conecta a ninguna red. Los servicios consultan
 * AnclajeProperties.isHabilitado() antes de usar el cliente, así que
 * llegar a uno de estos métodos es un error de programación.
 */
public class ClienteBlockchainDeshabilitado implements ClienteBlockchain {

    private static final String MENSAJE = "El anclaje en blockchain está deshabilitado (ANCLAJE_HABILITADO=false)";

    /** Crea el cliente deshabilitado. */
    public ClienteBlockchainDeshabilitado() {
    }

    @Override
    public long chainId() {
        throw new IllegalStateException(MENSAJE);
    }

    @Override
    public String direccionBilletera() {
        throw new IllegalStateException(MENSAJE);
    }

    @Override
    public BigInteger saldoWei() {
        throw new IllegalStateException(MENSAJE);
    }

    @Override
    public String codigoDelContrato() {
        throw new IllegalStateException(MENSAJE);
    }

    @Override
    public String duenioDelContrato() {
        throw new IllegalStateException(MENSAJE);
    }

    @Override
    public long ultimoNumeroAnclado() {
        throw new IllegalStateException(MENSAJE);
    }

    @Override
    public String hashAnclado(long numero) {
        throw new IllegalStateException(MENSAJE);
    }

    @Override
    public long cantidadAnclajes() {
        throw new IllegalStateException(MENSAJE);
    }

    @Override
    public List<AnclajeEnContrato> anclajes(long desde, int cantidad) {
        throw new IllegalStateException(MENSAJE);
    }

    @Override
    public long ultimoBloque() {
        throw new IllegalStateException(MENSAJE);
    }

    @Override
    public ComisionesRed comisiones() {
        throw new IllegalStateException(MENSAJE);
    }

    @Override
    public BigInteger nonce() {
        throw new IllegalStateException(MENSAJE);
    }

    @Override
    public void simularAnclaje(String hash, long hastaNumero, BigInteger gasMaximo) {
        throw new IllegalStateException(MENSAJE);
    }

    @Override
    public BigInteger estimarGasAnclaje(String hash, long hastaNumero) {
        throw new IllegalStateException(MENSAJE);
    }

    @Override
    public TransaccionFirmada firmarAnclaje(String hash, long hastaNumero, BigInteger nonce, BigInteger limiteGas,
                                            BigInteger comisionMaximaWei, BigInteger propinaWei) {
        throw new IllegalStateException(MENSAJE);
    }

    @Override
    public void transmitir(TransaccionFirmada transaccion) {
        throw new IllegalStateException(MENSAJE);
    }

    @Override
    public Optional<ReciboTransaccion> recibo(String hashTransaccion) {
        throw new IllegalStateException(MENSAJE);
    }

    @Override
    public boolean transaccionConocida(String hashTransaccion) {
        throw new IllegalStateException(MENSAJE);
    }

    /** No hay conexiones que cerrar. */
    @Override
    public void cerrar() {
    }
}
