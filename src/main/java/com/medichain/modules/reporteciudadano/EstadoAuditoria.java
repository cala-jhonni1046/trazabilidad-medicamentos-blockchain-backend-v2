package com.medichain.modules.reporteciudadano;

/**
 * Enumeración EstadoAuditoria en MediChain.
 * Ciclo de vida de un reporte ciudadano: nace ABIERTO, un inspector lo
 * toma y pasa a EN_INVESTIGACION, y finalmente se CIERRA con una
 * conclusión.
 */
public enum EstadoAuditoria {
    ABIERTO,
    EN_INVESTIGACION,
    CERRADO
}
