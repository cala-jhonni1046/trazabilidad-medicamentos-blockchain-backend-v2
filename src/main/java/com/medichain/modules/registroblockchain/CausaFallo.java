package com.medichain.modules.registroblockchain;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Enumeración CausaFallo en MediChain (R15).
 * Por qué un anclaje terminó FALLIDO. Las tres primeras son determinísticas
 * (reintentar igual volvería a fallar y gastaría gas): FRENAN la tarea
 * automática hasta que la Sede ancle a mano (POST /api/registros-blockchain/anclar).
 * Las fallas de red y las transacciones que no se incluyeron no frenan: la
 * tarea vuelve a intentar en el ciclo siguiente.
 * <ul>
 *   <li>REVERT: el contrato rechazó el anclaje (simulado o ya minado), con el error decodificado.</li>
 *   <li>SIN_GAS: la transacción se minó y se quedó sin gas (out of gas).</li>
 *   <li>GAS_SOBRE_EL_MAXIMO: la estimación + 30 % supera medichain.anclaje.gas-maximo; no se envió.</li>
 *   <li>SIN_INCLUIR: después de max-intentos reemplazos no entró en un bloque.</li>
 *   <li>ERROR_DE_RED: max-intentos envíos fallidos por el RPC o el nodo.</li>
 * </ul>
 */
@Schema(enumAsRef = true, description = "Por qué un anclaje terminó FALLIDO. Las tres primeras son determinísticas (reintentar igual volvería a fallar y gastaría gas): FRENAN la tarea automática hasta que la Sede ancle a mano (POST /api/registros-blockchain/anclar).")
public enum CausaFallo {
    REVERT(true),
    SIN_GAS(true),
    GAS_SOBRE_EL_MAXIMO(true),
    SIN_INCLUIR(false),
    ERROR_DE_RED(false);

    private final boolean frenaLaTarea;

    CausaFallo(boolean frenaLaTarea) {
        this.frenaLaTarea = frenaLaTarea;
    }

    /** true si un FALLIDO por esta causa frena la tarea automática. */
    public boolean frenaLaTarea() {
        return frenaLaTarea;
    }
}
