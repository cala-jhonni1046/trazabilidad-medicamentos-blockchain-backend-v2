package com.medichain.modules.registroblockchain;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

/**
 * Interfaz ClienteBlockchain en MediChain (R15).
 * Todo lo que MediChain le pide a la red Ethereum pasa por acá: lecturas
 * del contrato MediChainAnchor, datos de la billetera y de la red, firma y
 * transmisión de la transacción de anclaje. Es una interfaz para poder
 * simular la blockchain en los tests (Mockito) y para el modo deshabilitado.
 * Toda falla de red se informa con ErrorBlockchainException, con el mensaje
 * ya saneado (sin URL del RPC ni clave).
 */
public interface ClienteBlockchain {

    /** chainId de la red a la que apunta el RPC (Sepolia: 11155111). */
    long chainId();

    /** Dirección pública de la billetera (derivada de la clave privada), en formato checksum. */
    String direccionBilletera();

    /** Saldo de la billetera, en wei. */
    BigInteger saldoWei();

    /** Código desplegado en la dirección configurada (hexadecimal con 0x; "0x" si no hay contrato). */
    String codigoDelContrato();

    /** Dueño del contrato (función duenio()), en minúscula. */
    String duenioDelContrato();

    /** Último número anclado en el contrato (0 si no hay anclajes). */
    long ultimoNumeroAnclado();

    /** Hash anclado para un número (64 hex en minúscula), o null si ese número no se ancló. */
    String hashAnclado(long numero);

    /** Cantidad de anclajes guardados en el contrato. */
    long cantidadAnclajes();

    /** Página de anclajes del contrato: posiciones [desde, desde + cantidad), en orden. */
    List<AnclajeEnContrato> anclajes(long desde, int cantidad);

    /** Número del último bloque de la red. */
    long ultimoBloque();

    /** Comisión base del último bloque y propina sugerida. */
    ComisionesRed comisiones();

    /** Próximo nonce de la billetera según los bloques ya minados (LATEST). */
    BigInteger nonce();

    /**
     * Simula anclar(hash, hastaNumero) con eth_call y gasMaximo como límite,
     * sin enviar nada. Si el contrato la rechazaría lanza ErrorBlockchainException
     * con causa REVERT (error decodificado); si necesita más gas que gasMaximo,
     * con causa GAS_SOBRE_EL_MAXIMO. Un problema de red: causa null.
     */
    void simularAnclaje(String hash, long hastaNumero, BigInteger gasMaximo);

    /**
     * Gas que consumiría anclar(hash, hastaNumero). Si el contrato la rechazaría
     * (por ejemplo NumeroNoCreciente) lanza ErrorBlockchainException con
     * causa REVERT, sin gastar nada.
     */
    BigInteger estimarGasAnclaje(String hash, long hastaNumero);

    /** Firma la transacción anclar(hash, hastaNumero) con los parámetros dados (no la transmite). */
    TransaccionFirmada firmarAnclaje(String hash, long hastaNumero, BigInteger nonce, BigInteger limiteGas,
                                     BigInteger comisionMaximaWei, BigInteger propinaWei);

    /** Transmite una transacción firmada. Si el nodo ya la conocía, no es un error. */
    void transmitir(TransaccionFirmada transaccion);

    /** Recibo de la transacción si ya está en un bloque; vacío si todavía no se incluyó. */
    Optional<ReciboTransaccion> recibo(String hashTransaccion);

    /** true si el nodo conoce la transacción (pendiente en el mempool o ya incluida). */
    boolean transaccionConocida(String hashTransaccion);

    /** Libera las conexiones al cerrar la aplicación. */
    void cerrar();
}
