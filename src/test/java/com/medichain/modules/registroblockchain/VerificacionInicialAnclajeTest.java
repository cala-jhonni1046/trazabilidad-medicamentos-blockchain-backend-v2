package com.medichain.modules.registroblockchain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test unitario VerificacionInicialAnclajeTest en MediChain (R15).
 * Controles al arrancar con el anclaje habilitado: otra red, sin contrato,
 * otro dueño o contrato adelantado → la app no arranca (sin mostrar
 * valores); red caída → arranca con aviso; cadena que no coincide →
 * arranca con ERROR (el anclaje queda detenido).
 */
@ExtendWith(MockitoExtension.class)
class VerificacionInicialAnclajeTest {

    private static final String CONTRATO = "0x1111111111111111111111111111111111111111";
    private static final String BILLETERA = "0x2c7536E3605D9C16a7a3D7b1898e529396a65c23";

    @Mock
    private ClienteBlockchain cliente;

    @Mock
    private PasosAnclaje pasos;

    private AnclajeProperties propiedades;

    @BeforeEach
    void setUp() {
        propiedades = new AnclajeProperties();
        propiedades.setHabilitado(true);
        propiedades.setContrato(CONTRATO);
        lenient().when(cliente.chainId()).thenReturn(11_155_111L);
        lenient().when(cliente.codigoDelContrato()).thenReturn(codigoDePrueba("codigo-sepolia-0x9d71-optimizado.hex"));
        lenient().when(cliente.direccionBilletera()).thenReturn(BILLETERA);
        lenient().when(cliente.duenioDelContrato()).thenReturn(BILLETERA.toLowerCase());
        lenient().when(cliente.ultimoNumeroAnclado()).thenReturn(0L);
        lenient().when(cliente.saldoWei()).thenReturn(new BigInteger("50000000000000000"));
        lenient().when(pasos.ultimoNumeroLocal()).thenReturn(28L);
    }

    /** Código real de un contrato de Sepolia guardado en src/test/resources/contrato. */
    static String codigoDePrueba(String archivo) {
        try (InputStream entrada = new ClassPathResource("contrato/" + archivo).getInputStream()) {
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Inicializador bajo prueba. */
    private VerificacionInicialAnclaje verificacion() {
        return new VerificacionInicialAnclaje(cliente, pasos, propiedades, new CodigoEsperadoContrato());
    }

    @Test
    @DisplayName("Todo coherente → arranca e informa el saldo")
    void todoBien() {
        assertDoesNotThrow(() -> verificacion().run());
        verify(cliente).saldoWei();
    }

    @Test
    @DisplayName("El RPC apunta a otra red (chainId 1) → no arranca")
    void otraRed() {
        when(cliente.chainId()).thenReturn(1L);

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> verificacion().run());

        assertTrue(e.getMessage().contains("chainId 1"), e.getMessage());
    }

    @Test
    @DisplayName("No hay contrato en la dirección → no arranca (sin mostrar la dirección)")
    void sinContrato() {
        when(cliente.codigoDelContrato()).thenReturn("0x");

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> verificacion().run());

        assertTrue(e.getMessage().contains("no hay un contrato"));
        assertFalse(e.getMessage().contains(CONTRATO));
    }

    @Test
    @DisplayName("La billetera no es la dueña del contrato → no arranca")
    void otroDuenio() {
        when(cliente.duenioDelContrato()).thenReturn("0x" + "9".repeat(40));

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> verificacion().run());

        assertTrue(e.getMessage().contains("no es la dueña"));
    }

    @Test
    @DisplayName("El contrato ancló más eventos que la base (base reseteada) → no arranca: desplegar un contrato nuevo")
    void contratoAdelantado() {
        when(cliente.ultimoNumeroAnclado()).thenReturn(84L);

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> verificacion().run());

        assertTrue(e.getMessage().contains("desplegá un contrato nuevo"), e.getMessage());
    }

    @Test
    @DisplayName("La red no responde al arrancar → arranca igual (el negocio no depende de Sepolia)")
    void redCaida() {
        when(cliente.chainId()).thenThrow(new ErrorBlockchainException("No se pudo consultar el chainId: timeout"));

        assertDoesNotThrow(() -> verificacion().run());
    }

    @Test
    @DisplayName("El último anclaje no coincide con el evento local → arranca con ERROR, sin informar saldo")
    void cadenaQueNoCoincide() {
        when(cliente.ultimoNumeroAnclado()).thenReturn(20L);
        when(pasos.hashDelEvento(20L)).thenReturn(Optional.of("aa".repeat(32)));
        when(cliente.hashAnclado(20L)).thenReturn("bb".repeat(32));

        assertDoesNotThrow(() -> verificacion().run());
        verify(cliente, never()).saldoWei();
    }

    @Test
    @DisplayName("Código de 0xB126… (mismo fuente sin optimizador, desplegado con Remix) → válido, arranca")
    void codigoSinOptimizadorEsValido() {
        when(cliente.codigoDelContrato()).thenReturn(codigoDePrueba("codigo-sepolia-0xB126-sin-optimizador.hex"));

        assertDoesNotThrow(() -> verificacion().run());
        verify(cliente).saldoWei();
    }

    @Test
    @DisplayName("Código que no es MediChainAnchor.sol → solo WARN: sigue con los demás controles y arranca")
    void codigoDesconocidoSoloAvisa() {
        when(cliente.codigoDelContrato()).thenReturn("0x6080604052348015600e575f5ffd5b50");

        assertDoesNotThrow(() -> verificacion().run());
        verify(cliente).duenioDelContrato();
    }
}
