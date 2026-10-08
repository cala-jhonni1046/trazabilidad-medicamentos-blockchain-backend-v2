package com.medichain.modules.cuarentena;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Enumeración CausaBloqueo en MediChain (R10).
 * Por qué una caja, un bulto o un lote está bloqueado (no viaja, no se recibe
 * y no se dispensa). Es el código que la API devuelve en "motivoBloqueo";
 * "mensajeBloqueo" trae la explicación para mostrar. No confundir con
 * MotivoBloqueo, que es el motivo de una medida sanitaria (PREVENTIVA, ROBO…).
 */
@Schema(enumAsRef = true, description = "Por qué una caja, un bulto o un lote está bloqueado (R10).")
public enum CausaBloqueo {

    /** El lote ya venció (calendario de Argentina). */
    LOTE_VENCIDO,

    /** El lote está en CUARENTENA (medida de alcance LOTE). */
    LOTE_EN_CUARENTENA,

    /** El lote está en RECALL (retiro del mercado). */
    LOTE_EN_RECALL,

    /** El lote figura en una medida sanitaria vigente de alcance LOTE. */
    LOTE_CON_MEDIDA_VIGENTE,

    /** El bulto figura en una medida vigente de alcance DESPACHO (ruptura de frío, robo) o BULTO (recepción rechazada). */
    BULTO_CON_MEDIDA_VIGENTE
}
