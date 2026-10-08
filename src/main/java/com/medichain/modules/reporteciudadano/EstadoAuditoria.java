package com.medichain.modules.reporteciudadano;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Enumeración EstadoAuditoria en MediChain.
 * Ciclo de vida de un reporte ciudadano: nace ABIERTO, un inspector lo
 * toma y pasa a EN_INVESTIGACION, y finalmente se CIERRA con una
 * conclusión.
 */
@Schema(enumAsRef = true, description = "Ciclo de vida de un reporte ciudadano: nace ABIERTO, un inspector lo toma y pasa a EN_INVESTIGACION, y finalmente se CIERRA con una conclusión.")
public enum EstadoAuditoria {
    ABIERTO,
    EN_INVESTIGACION,
    CERRADO
}
