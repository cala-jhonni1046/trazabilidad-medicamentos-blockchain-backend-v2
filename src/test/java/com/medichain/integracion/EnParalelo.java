package com.medichain.integracion;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Utilidad de prueba EnParalelo en MediChain.
 * Corre varias tareas en hilos distintos que largan JUNTAS (una barrera las
 * suelta a la vez), para provocar de verdad las carreras que se prueban
 * contra PostgreSQL: dos altas del mismo par, dos lotes con las mismas
 * series, dos dispensaciones de la misma caja. Devuelve, por tarea, su
 * valor o la excepción que lanzó. Si algo no termina en el plazo (por
 * ejemplo un deadlock), falla en lugar de colgar la suite.
 */
public final class EnParalelo {

    private static final long PLAZO_SEGUNDOS = 60;

    private EnParalelo() {
    }

    /** Corre las tareas a la vez y devuelve un resultado por tarea, en el mismo orden. */
    public static List<Resultado> correr(List<Callable<?>> tareas) throws InterruptedException {
        ExecutorService hilos = Executors.newFixedThreadPool(tareas.size());
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<?>> futuros = new ArrayList<>();
            for (Callable<?> tarea : tareas) {
                futuros.add(hilos.submit(() -> {
                    largada.await();
                    return tarea.call();
                }));
            }
            largada.countDown();
            List<Resultado> resultados = new ArrayList<>();
            for (Future<?> futuro : futuros) {
                resultados.add(Resultado.de(futuro));
            }
            return resultados;
        } finally {
            hilos.shutdownNow();
        }
    }

    /** Corre "veces" copias de la misma tarea a la vez. */
    public static List<Resultado> correr(int veces, Callable<?> tarea) throws InterruptedException {
        List<Callable<?>> tareas = new ArrayList<>();
        for (int i = 0; i < veces; i++) {
            tareas.add(tarea);
        }
        return correr(tareas);
    }

    /** Cantidad de tareas que terminaron sin excepción. */
    public static long exitos(List<Resultado> resultados) {
        return resultados.stream().filter(Resultado::isExito).count();
    }

    /** Excepciones lanzadas por las tareas que fallaron. */
    public static List<Throwable> errores(List<Resultado> resultados) {
        return resultados.stream().filter(r -> !r.isExito()).map(Resultado::getError).toList();
    }

    /** Resultado de una tarea: su valor o la excepción que lanzó. */
    public static final class Resultado {

        private final Object valor;
        private final Throwable error;

        private Resultado(Object valor, Throwable error) {
            this.valor = valor;
            this.error = error;
        }

        /** Espera la tarea (con plazo) y arma su resultado. */
        static Resultado de(Future<?> futuro) throws InterruptedException {
            try {
                return new Resultado(futuro.get(PLAZO_SEGUNDOS, TimeUnit.SECONDS), null);
            } catch (java.util.concurrent.ExecutionException e) {
                return new Resultado(null, e.getCause());
            } catch (java.util.concurrent.TimeoutException e) {
                throw new AssertionError("Una tarea no terminó en " + PLAZO_SEGUNDOS + " s (¿deadlock?)", e);
            }
        }

        public boolean isExito() {
            return error == null;
        }

        public Object getValor() {
            return valor;
        }

        public Throwable getError() {
            return error;
        }
    }
}
