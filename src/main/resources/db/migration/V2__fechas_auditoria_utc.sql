-- =============================================================================
-- V2__fechas_auditoria_utc.sql · MediChain (paso 10)
--
-- Las fechas de auditoría de BaseEntity (fecha_creacion y fecha_actualizacion,
-- en las 19 tablas de entidades) pasan de timestamp sin zona (LocalDateTime) a
-- timestamptz (Instant, siempre UTC). La API las devuelve en UTC con Z.
--
-- Conversión: la app guardaba estas fechas en hora UTC (JVM en UTC y
-- hibernate.jdbc.time_zone=UTC); AT TIME ZONE 'UTC' las interpreta así y
-- conserva el instante exacto, con sus microsegundos.
--
-- No toca la cadena de hashes: estas fechas no entran al hash, y
-- eventos_trazabilidad.fecha_hora (la que sí entra) ya es timestamptz(6) y no
-- se modifica acá.
--
-- Las otras 25 columnas de fecha del negocio (fecha_salida, fecha_hora de la
-- dispensación, lecturas de telemetría, etc.) siguen como timestamp sin zona:
-- pendiente, con su propio diseño.
--
-- NO EDITAR: Flyway compara checksums. Toda corrección va en una V nueva.
-- =============================================================================

ALTER TABLE usuarios
    ALTER COLUMN fecha_creacion TYPE timestamp(6) with time zone USING fecha_creacion AT TIME ZONE 'UTC',
    ALTER COLUMN fecha_actualizacion TYPE timestamp(6) with time zone USING fecha_actualizacion AT TIME ZONE 'UTC';

ALTER TABLE empresas
    ALTER COLUMN fecha_creacion TYPE timestamp(6) with time zone USING fecha_creacion AT TIME ZONE 'UTC',
    ALTER COLUMN fecha_actualizacion TYPE timestamp(6) with time zone USING fecha_actualizacion AT TIME ZONE 'UTC';

ALTER TABLE inspectores_anmat
    ALTER COLUMN fecha_creacion TYPE timestamp(6) with time zone USING fecha_creacion AT TIME ZONE 'UTC',
    ALTER COLUMN fecha_actualizacion TYPE timestamp(6) with time zone USING fecha_actualizacion AT TIME ZONE 'UTC';

ALTER TABLE enlaces_cuit
    ALTER COLUMN fecha_creacion TYPE timestamp(6) with time zone USING fecha_creacion AT TIME ZONE 'UTC',
    ALTER COLUMN fecha_actualizacion TYPE timestamp(6) with time zone USING fecha_actualizacion AT TIME ZONE 'UTC';

ALTER TABLE medicamentos
    ALTER COLUMN fecha_creacion TYPE timestamp(6) with time zone USING fecha_creacion AT TIME ZONE 'UTC',
    ALTER COLUMN fecha_actualizacion TYPE timestamp(6) with time zone USING fecha_actualizacion AT TIME ZONE 'UTC';

ALTER TABLE lotes
    ALTER COLUMN fecha_creacion TYPE timestamp(6) with time zone USING fecha_creacion AT TIME ZONE 'UTC',
    ALTER COLUMN fecha_actualizacion TYPE timestamp(6) with time zone USING fecha_actualizacion AT TIME ZONE 'UTC';

ALTER TABLE unidades_trazables
    ALTER COLUMN fecha_creacion TYPE timestamp(6) with time zone USING fecha_creacion AT TIME ZONE 'UTC',
    ALTER COLUMN fecha_actualizacion TYPE timestamp(6) with time zone USING fecha_actualizacion AT TIME ZONE 'UTC';

ALTER TABLE bultos
    ALTER COLUMN fecha_creacion TYPE timestamp(6) with time zone USING fecha_creacion AT TIME ZONE 'UTC',
    ALTER COLUMN fecha_actualizacion TYPE timestamp(6) with time zone USING fecha_actualizacion AT TIME ZONE 'UTC';

ALTER TABLE despachos_logisticos
    ALTER COLUMN fecha_creacion TYPE timestamp(6) with time zone USING fecha_creacion AT TIME ZONE 'UTC',
    ALTER COLUMN fecha_actualizacion TYPE timestamp(6) with time zone USING fecha_actualizacion AT TIME ZONE 'UTC';

ALTER TABLE telemetria_temperatura
    ALTER COLUMN fecha_creacion TYPE timestamp(6) with time zone USING fecha_creacion AT TIME ZONE 'UTC',
    ALTER COLUMN fecha_actualizacion TYPE timestamp(6) with time zone USING fecha_actualizacion AT TIME ZONE 'UTC';

ALTER TABLE telemetria_gps
    ALTER COLUMN fecha_creacion TYPE timestamp(6) with time zone USING fecha_creacion AT TIME ZONE 'UTC',
    ALTER COLUMN fecha_actualizacion TYPE timestamp(6) with time zone USING fecha_actualizacion AT TIME ZONE 'UTC';

ALTER TABLE recepciones
    ALTER COLUMN fecha_creacion TYPE timestamp(6) with time zone USING fecha_creacion AT TIME ZONE 'UTC',
    ALTER COLUMN fecha_actualizacion TYPE timestamp(6) with time zone USING fecha_actualizacion AT TIME ZONE 'UTC';

ALTER TABLE dispensaciones
    ALTER COLUMN fecha_creacion TYPE timestamp(6) with time zone USING fecha_creacion AT TIME ZONE 'UTC',
    ALTER COLUMN fecha_actualizacion TYPE timestamp(6) with time zone USING fecha_actualizacion AT TIME ZONE 'UTC';

ALTER TABLE reportes_ciudadanos
    ALTER COLUMN fecha_creacion TYPE timestamp(6) with time zone USING fecha_creacion AT TIME ZONE 'UTC',
    ALTER COLUMN fecha_actualizacion TYPE timestamp(6) with time zone USING fecha_actualizacion AT TIME ZONE 'UTC';

ALTER TABLE cuarentenas
    ALTER COLUMN fecha_creacion TYPE timestamp(6) with time zone USING fecha_creacion AT TIME ZONE 'UTC',
    ALTER COLUMN fecha_actualizacion TYPE timestamp(6) with time zone USING fecha_actualizacion AT TIME ZONE 'UTC';

ALTER TABLE intento_verificacion
    ALTER COLUMN fecha_creacion TYPE timestamp(6) with time zone USING fecha_creacion AT TIME ZONE 'UTC',
    ALTER COLUMN fecha_actualizacion TYPE timestamp(6) with time zone USING fecha_actualizacion AT TIME ZONE 'UTC';

ALTER TABLE cadena_estado
    ALTER COLUMN fecha_creacion TYPE timestamp(6) with time zone USING fecha_creacion AT TIME ZONE 'UTC',
    ALTER COLUMN fecha_actualizacion TYPE timestamp(6) with time zone USING fecha_actualizacion AT TIME ZONE 'UTC';

ALTER TABLE eventos_trazabilidad
    ALTER COLUMN fecha_creacion TYPE timestamp(6) with time zone USING fecha_creacion AT TIME ZONE 'UTC',
    ALTER COLUMN fecha_actualizacion TYPE timestamp(6) with time zone USING fecha_actualizacion AT TIME ZONE 'UTC';

ALTER TABLE registros_blockchain
    ALTER COLUMN fecha_creacion TYPE timestamp(6) with time zone USING fecha_creacion AT TIME ZONE 'UTC',
    ALTER COLUMN fecha_actualizacion TYPE timestamp(6) with time zone USING fecha_actualizacion AT TIME ZONE 'UTC';
