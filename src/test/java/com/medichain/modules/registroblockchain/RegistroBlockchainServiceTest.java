package com.medichain.modules.registroblockchain;

import com.medichain.modules.trazabilidad.CadenaEstado;
import com.medichain.testutil.DatosDePrueba;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigInteger;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Test unitario RegistroBlockchainServiceTest en MediChain (R15).
 * El tablero /estado: gas REAL del próximo anclaje (primero o siguientes),
 * límite, costo, anclajes que alcanzan, saldo mínimo y los frenos de la
 * tarea automática en "mensaje". Deshabilitado: solo lo local.
 */
@ExtendWith(MockitoExtension.class)
class RegistroBlockchainServiceTest {

    private static final String CONTRATO = "0x" + "1".repeat(40);
    private static final String HASH_EVENTO_28 = "aa".repeat(32);
    private static final BigInteger GWEI = BigInteger.valueOf(1_000_000_000L);

    @Mock
    private RegistroBlockchainRepository repository;

    @Mock
    private ProcesoAnclaje proceso;

    @Mock
    private PasosAnclaje pasos;

    @Mock
    private ClienteBlockchain cliente;

    private AnclajeProperties propiedades;

    @BeforeEach
    void setUp() {
        propiedades = new AnclajeProperties();
        propiedades.setHabilitado(true);
        propiedades.setContrato(CONTRATO);
        lenient().when(pasos.cadena()).thenReturn(new CadenaEstado(CadenaEstado.PRINCIPAL, 28L, HASH_EVENTO_28));
        lenient().when(pasos.enCurso()).thenReturn(Optional.empty());
        lenient().when(repository.findFirstByEstadoInOrderByHastaNumeroDesc(any())).thenReturn(Optional.empty());
        lenient().when(proceso.frenoPorFallo()).thenReturn(Optional.empty());
        lenient().when(proceso.frenoPorSaldo(any(), any(), eq(10))).thenReturn(Optional.empty());
        lenient().when(cliente.direccionBilletera()).thenReturn("0x1eEB5f841204c7603Cf83Fc4345428bB4C2C232D");
        lenient().when(cliente.saldoWei()).thenReturn(new BigInteger("35036654783763510"));
        lenient().when(cliente.ultimoNumeroAnclado()).thenReturn(0L);
    }

    /** Service bajo prueba. */
    private RegistroBlockchainService service() {
        return new RegistroBlockchainService(repository, new RegistroBlockchainMapper(), proceso, pasos, cliente,
                propiedades);
    }

    /** Estimación como la de Sepolia post-Glamsterdam a 1,1 gwei (base 1 + propina 0,1). */
    private EstimacionAnclaje estimacion(long gas, long limite, boolean dentro) {
        return new EstimacionAnclaje(BigInteger.valueOf(gas), BigInteger.valueOf(limite), dentro,
                GWEI.add(GWEI.divide(BigInteger.TEN)), GWEI.multiply(BigInteger.TWO).add(GWEI.divide(BigInteger.TEN)));
    }

    @Test
    @DisplayName("Primer anclaje: gasPorAnclaje = estimación real (353.084), límite 459.010, costo y anclajes que alcanzan")
    void estimacionRealDelPrimerAnclaje() {
        when(proceso.estimar(HASH_EVENTO_28, 29L)).thenReturn(estimacion(353_084, 459_010, true));

        EstadoAnclajeResponseDTO dto = service().estado();

        assertEquals(353_084L, dto.getGasPorAnclaje());
        assertEquals(459_010L, dto.getLimiteGas());
        assertTrue(dto.getPrimerAnclaje());
        assertEquals(500_000L, dto.getGasMaximo());
        assertEquals("0.00038839", dto.getCostoPorAnclajeEth());
        assertEquals(90L, dto.getAnclajesEstimados());
        assertEquals("0.003883", dto.getSaldoMinimoEth());
        assertTrue(dto.getMensaje().contains("primer anclaje"));
        assertFalse(dto.isAnclajeAutomaticoFrenado());
    }

    @Test
    @DisplayName("Anclajes siguientes: estima el número siguiente al último del contrato; no es el primero")
    void estimacionDeLosSiguientes() {
        when(cliente.ultimoNumeroAnclado()).thenReturn(84L);
        when(pasos.cadena()).thenReturn(new CadenaEstado(CadenaEstado.PRINCIPAL, 84L, HASH_EVENTO_28));
        when(proceso.estimar(HASH_EVENTO_28, 85L)).thenReturn(estimacion(154_507, 200_860, true));

        EstadoAnclajeResponseDTO dto = service().estado();

        assertEquals(154_507L, dto.getGasPorAnclaje());
        assertFalse(dto.getPrimerAnclaje());
        assertEquals(0L, dto.getEventosSinAnclar());
        assertNull(dto.getMensaje());
    }

    @Test
    @DisplayName("Frenos en el mensaje: FALLIDO determinístico y saldo bajo → anclajeAutomaticoFrenado; gas sobre el máximo avisado")
    void frenosEnElMensaje() {
        when(proceso.estimar(anyString(), anyLong())).thenReturn(estimacion(400_000, 520_000, false));
        when(proceso.frenoPorFallo()).thenReturn(Optional.of("Anclaje automático frenado: el último anclaje terminó FALLIDO"));
        when(proceso.frenoPorSaldo(any(), any(), eq(10))).thenReturn(Optional.of("Anclaje automático frenado por saldo"));

        EstadoAnclajeResponseDTO dto = service().estado();

        assertTrue(dto.isAnclajeAutomaticoFrenado());
        assertTrue(dto.getMensaje().contains("terminó FALLIDO"));
        assertTrue(dto.getMensaje().contains("frenado por saldo"));
        assertTrue(dto.getMensaje().contains("más que gas-maximo 500000"));
    }

    @Test
    @DisplayName("El próximo anclaje revertiría → aviso en el mensaje, sin gas ni costo")
    void estimacionQueRevierte() {
        when(proceso.estimar(anyString(), anyLong())).thenThrow(
                new ErrorBlockchainException("El contrato rechazaría el anclaje: NoEsElDuenio", CausaFallo.REVERT));

        EstadoAnclajeResponseDTO dto = service().estado();

        assertNull(dto.getGasPorAnclaje());
        assertTrue(dto.getMensaje().contains("NoEsElDuenio"));
    }

    @Test
    @DisplayName("Anclaje deshabilitado → solo lo local y el aviso, sin tocar la red")
    void deshabilitado() {
        propiedades.setHabilitado(false);
        when(repository.findFirstByEstadoInOrderByHastaNumeroDesc(any()))
                .thenReturn(Optional.of(DatosDePrueba.anclajeConfirmado(1, 28, HASH_EVENTO_28, CONTRATO)));

        EstadoAnclajeResponseDTO dto = service().estado();

        assertFalse(dto.isHabilitado());
        assertEquals(28L, dto.getUltimoNumeroLocal());
        assertEquals(28L, dto.getUltimoAnclaje().getHastaNumero());
        assertTrue(dto.getMensaje().contains("deshabilitado"));
        verifyNoInteractions(cliente);
    }
}
