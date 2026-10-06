package com.medichain.modules.registroblockchain;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tarea programada TareaAnclaje en MediChain (R15).
 * Solo existe con ANCLAJE_HABILITADO=true. Dos tareas con fixedDelay (la
 * siguiente corrida empieza cuando terminó la anterior, nunca se pisan):
 * <ul>
 *   <li>Cada 5 minutos: si hubo eventos nuevos, ancla el hash del último.</li>
 *   <li>Cada 15 segundos: sigue el anclaje en curso (confirmaciones,
 *       reintentos, reemplazo), para ver CONFIRMADO en menos de un minuto.</li>
 * </ul>
 * Corren en el hilo del scheduler: el negocio nunca espera a la blockchain.
 */
@Component
@ConditionalOnProperty(name = "medichain.anclaje.habilitado", havingValue = "true")
public class TareaAnclaje {

    private final ProcesoAnclaje proceso;

    @Autowired
    public TareaAnclaje(ProcesoAnclaje proceso) {
        this.proceso = proceso;
    }

    /** Ancla el último hash si hubo eventos nuevos desde el último anclaje. */
    @Scheduled(initialDelayString = "${medichain.anclaje.demora-inicial:PT1M}",
            fixedDelayString = "${medichain.anclaje.intervalo:PT5M}")
    public void anclar() {
        proceso.cicloAnclaje();
    }

    /** Sigue el anclaje en curso hasta CONFIRMADO o FALLIDO. */
    @Scheduled(initialDelayString = "PT20S", fixedDelayString = "${medichain.anclaje.intervalo-seguimiento:PT15S}")
    public void seguir() {
        proceso.cicloSeguimiento();
    }
}
