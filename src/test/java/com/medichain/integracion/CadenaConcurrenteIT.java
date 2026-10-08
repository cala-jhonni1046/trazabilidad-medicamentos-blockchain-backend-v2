package com.medichain.integracion;

import com.medichain.modules.trazabilidad.RegistradorEventos;
import com.medichain.modules.trazabilidad.TipoEvento;
import com.medichain.modules.trazabilidad.VerificacionCadenaResponseDTO;
import com.medichain.modules.trazabilidad.VerificadorCadena;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test de integración CadenaConcurrenteIT en MediChain (R15).
 * La cadena de eventos es UNA sola para todo el sistema y la serializa el
 * bloqueo de cadena_estado (SELECT … FOR UPDATE). Con PostgreSQL real y
 * muchos hilos a la vez se comprueba lo que un test con mocks no puede:
 * números correlativos sin huecos ni repetidos, cada hashAnterior igual al
 * hash previo, fechaHora que acompaña al número (A1), y que un rollback del
 * negocio se lleva su evento sin dejar hueco.
 */
class CadenaConcurrenteIT extends IntegracionBase {

    private static final int HILOS = 16;
    private static final int EVENTOS_POR_HILO = 25;

    @Autowired
    private RegistradorEventos registradorEventos;

    @Autowired
    private VerificadorCadena verificadorCadena;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("16 hilos × 25 eventos a la vez → 400 eventos 1..400 sin huecos, cadena íntegra y fechas en orden")
    void altasConcurrentesSinHuecos() throws Exception {
        TransactionTemplate transaccion = new TransactionTemplate(transactionManager);
        List<Callable<?>> tareas = new ArrayList<>();
        for (int hilo = 0; hilo < HILOS; hilo++) {
            int numeroHilo = hilo;
            tareas.add(() -> {
                for (int i = 0; i < EVENTOS_POR_HILO; i++) {
                    int indice = i;
                    transaccion.executeWithoutResult(estado -> registradorEventos.registrar(
                            TipoEvento.MEDICAMENTO_REGISTRADO, "Prueba", UUID.randomUUID(),
                            Map.of("hilo", numeroHilo, "indice", indice), null, null));
                }
                return null;
            });
        }

        List<EnParalelo.Resultado> resultados = EnParalelo.correr(tareas);

        assertEquals(List.of(), EnParalelo.errores(resultados));
        int total = HILOS * EVENTOS_POR_HILO;
        Map<String, Object> numeros = jdbc.queryForMap(
                "SELECT count(*) AS cantidad, count(DISTINCT numero) AS distintos, min(numero) AS minimo, "
                        + "max(numero) AS maximo FROM eventos_trazabilidad");
        assertEquals((long) total, ((Number) numeros.get("cantidad")).longValue());
        assertEquals((long) total, ((Number) numeros.get("distintos")).longValue());
        assertEquals(1L, ((Number) numeros.get("minimo")).longValue());
        assertEquals((long) total, ((Number) numeros.get("maximo")).longValue());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM eventos_trazabilidad a "
                + "JOIN eventos_trazabilidad b ON b.numero = a.numero + 1 WHERE b.hash_anterior <> a.hash",
                Integer.class), "cada hashAnterior es el hash del evento previo");
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM eventos_trazabilidad a "
                + "JOIN eventos_trazabilidad b ON b.numero = a.numero + 1 WHERE b.fecha_hora < a.fecha_hora",
                Integer.class), "A1: fechaHora no retrocede cuando el número avanza");

        VerificacionCadenaResponseDTO verificacion = verificadorCadena.verificar();
        assertTrue(verificacion.isIntegra(), verificacion.getMotivo());
        assertEquals(total, verificacion.getEventosVerificados());
        assertEquals((long) total, jdbc.queryForObject("SELECT ultimo_numero FROM cadena_estado", Long.class));
    }

    @Test
    @DisplayName("Rollback del negocio: el evento se va con él; con altas concurrentes la cadena sigue sin huecos")
    void rollbackSeLlevaElEvento() throws Exception {
        TransactionTemplate transaccion = new TransactionTemplate(transactionManager);
        Callable<Object> tarea = () -> {
            for (int i = 0; i < 20; i++) {
                boolean falla = i % 2 == 1;
                try {
                    transaccion.executeWithoutResult(estado -> {
                        registradorEventos.registrar(TipoEvento.MEDICAMENTO_REGISTRADO, "Prueba", UUID.randomUUID(),
                                Map.of("falla", falla), null, null);
                        if (falla) {
                            throw new IllegalStateException("el negocio falla después de registrar su evento");
                        }
                    });
                } catch (IllegalStateException esperada) {
                    // El rollback de la transacción de negocio se lleva el evento y libera el bloqueo de la cadena.
                }
            }
            return null;
        };

        List<EnParalelo.Resultado> resultados = EnParalelo.correr(8, tarea);

        assertEquals(List.of(), EnParalelo.errores(resultados));
        assertEquals(80, jdbc.queryForObject("SELECT count(*) FROM eventos_trazabilidad", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM eventos_trazabilidad WHERE datos_json LIKE "
                + "'%\"falla\":true%'", Integer.class), "ningún evento de una transacción revertida");
        assertEquals(80L, jdbc.queryForObject("SELECT max(numero) FROM eventos_trazabilidad", Long.class));
        assertTrue(verificadorCadena.verificar().isIntegra());
    }

    @Test
    @DisplayName("propagation MANDATORY: registrar un evento fuera de una transacción de negocio se rechaza")
    void sinTransaccionNoSeRegistra() {
        assertThrows(IllegalTransactionStateException.class, () -> registradorEventos.registrar(
                TipoEvento.MEDICAMENTO_REGISTRADO, "Prueba", UUID.randomUUID(), Map.of(), null, null));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM eventos_trazabilidad", Integer.class));
    }
}
