-- =============================================================================
-- V5__indice_eventos_por_entidad.sql · MediChain (paso B12e)
--
-- Los eventos se buscan por la entidad sobre la que ocurrieron (entidad_id) para
-- la trazabilidad por código (GET /api/eventos-trazabilidad/recorrido) y para el
-- filtro ?entidadId= de listarEventos. entidad_id NO es una clave foránea (guarda
-- el id de un lote, un bulto, un viaje, una empresa…: tablas distintas), por eso
-- no entra en la regla de V3 (un ix_ de una sola columna sobre cada FK).
--
-- Es compuesto (entidad_id, numero): trae los eventos de una entidad ya en el orden
-- de la cadena, sin ordenar aparte. Solo agrega un índice; no cambia datos ni
-- entidades, y no toca la cadena ni sus hashes.
-- =============================================================================

CREATE INDEX ix_eventos_trazabilidad_entidad_id_numero
    ON eventos_trazabilidad (entidad_id, numero);
