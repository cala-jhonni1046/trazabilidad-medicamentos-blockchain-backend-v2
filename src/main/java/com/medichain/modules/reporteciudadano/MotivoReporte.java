package com.medichain.modules.reporteciudadano;

/**
 * Enumeración MotivoReporte en MediChain.
 * Por qué un paciente reporta una caja. Código fijo (va al evento); el
 * detalle libre del paciente queda solo en el reporte (nunca en la cadena).
 */
public enum MotivoReporte {
    SOSPECHA_FALSIFICACION,
    ENVASE_DANADO,
    EFECTO_ADVERSO,
    OTRO
}
