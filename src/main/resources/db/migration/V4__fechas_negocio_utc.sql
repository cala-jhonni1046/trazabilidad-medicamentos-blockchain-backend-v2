-- =============================================================================
-- V4__fechas_negocio_utc.sql · MediChain (paso B11d)
--
-- Las 25 fechas y horas del negocio pasan de timestamp sin zona
-- (LocalDateTime) a timestamptz (Instant, siempre UTC), igual que las de
-- auditoria en V2. La API las devuelve en UTC con Z; el cliente las envia con
-- offset obligatorio (OffsetDateTime). El dia (sin hora) se decide con el
-- calendario de Argentina (medichain.zona-horaria); las columnas de solo fecha
-- (lotes.fecha_fabricacion, lotes.fecha_vencimiento) siguen siendo date y NO se
-- tocan.
--
-- Conversion: la app guardaba estas fechas con LocalDateTime.now() y la JVM en
-- UTC (hibernate.jdbc.time_zone=UTC), asi que el valor almacenado ya es UTC;
-- AT TIME ZONE 'UTC' lo interpreta asi y conserva el instante exacto con sus
-- microsegundos.
--
-- No toca la cadena de hashes: estas fechas no entran al hash como columna, y
-- las dos que viajan DENTRO de los datos de un evento (fecha_estimada_entrega
-- en VIAJE_CREADO y fecha_hora de la lectura en RUPTURA_FRIO) ya estan
-- congeladas como texto en eventos_trazabilidad.datos_json, que no se modifica:
-- los eventos viejos (formato sin Z) siguen verificando y los nuevos llevan Z.
--
-- NO EDITAR: Flyway compara checksums. Toda correccion va en una V nueva.
-- =============================================================================

ALTER TABLE usuarios
    ALTER COLUMN ultimo_login TYPE timestamp(6) with time zone USING ultimo_login AT TIME ZONE 'UTC';

ALTER TABLE empresas
    ALTER COLUMN fecha_solicitud TYPE timestamp(6) with time zone USING fecha_solicitud AT TIME ZONE 'UTC',
    ALTER COLUMN fecha_habilitacion TYPE timestamp(6) with time zone USING fecha_habilitacion AT TIME ZONE 'UTC';

ALTER TABLE inspectores_anmat
    ALTER COLUMN fecha_alta TYPE timestamp(6) with time zone USING fecha_alta AT TIME ZONE 'UTC',
    ALTER COLUMN fecha_baja TYPE timestamp(6) with time zone USING fecha_baja AT TIME ZONE 'UTC';

ALTER TABLE enlaces_cuit
    ALTER COLUMN fecha_propuesta TYPE timestamp(6) with time zone USING fecha_propuesta AT TIME ZONE 'UTC',
    ALTER COLUMN fecha_aceptacion_distribuidor TYPE timestamp(6) with time zone USING fecha_aceptacion_distribuidor AT TIME ZONE 'UTC',
    ALTER COLUMN fecha_aceptacion_farmacia TYPE timestamp(6) with time zone USING fecha_aceptacion_farmacia AT TIME ZONE 'UTC',
    ALTER COLUMN fecha_aprobacion TYPE timestamp(6) with time zone USING fecha_aprobacion AT TIME ZONE 'UTC';

ALTER TABLE lotes
    ALTER COLUMN fecha_liberacion TYPE timestamp(6) with time zone USING fecha_liberacion AT TIME ZONE 'UTC';

ALTER TABLE bultos
    ALTER COLUMN fecha_armado TYPE timestamp(6) with time zone USING fecha_armado AT TIME ZONE 'UTC';

ALTER TABLE despachos_logisticos
    ALTER COLUMN fecha_salida TYPE timestamp(6) with time zone USING fecha_salida AT TIME ZONE 'UTC',
    ALTER COLUMN fecha_estimada_entrega TYPE timestamp(6) with time zone USING fecha_estimada_entrega AT TIME ZONE 'UTC';

ALTER TABLE telemetria_temperatura
    ALTER COLUMN fecha_hora TYPE timestamp(6) with time zone USING fecha_hora AT TIME ZONE 'UTC';

ALTER TABLE telemetria_gps
    ALTER COLUMN fecha_hora TYPE timestamp(6) with time zone USING fecha_hora AT TIME ZONE 'UTC';

ALTER TABLE recepciones
    ALTER COLUMN fecha_hora TYPE timestamp(6) with time zone USING fecha_hora AT TIME ZONE 'UTC';

ALTER TABLE dispensaciones
    ALTER COLUMN fecha_hora TYPE timestamp(6) with time zone USING fecha_hora AT TIME ZONE 'UTC',
    ALTER COLUMN fecha_anulacion TYPE timestamp(6) with time zone USING fecha_anulacion AT TIME ZONE 'UTC';

ALTER TABLE reportes_ciudadanos
    ALTER COLUMN fecha_reporte TYPE timestamp(6) with time zone USING fecha_reporte AT TIME ZONE 'UTC',
    ALTER COLUMN fecha_cierre TYPE timestamp(6) with time zone USING fecha_cierre AT TIME ZONE 'UTC';

ALTER TABLE cuarentenas
    ALTER COLUMN fecha_inicio TYPE timestamp(6) with time zone USING fecha_inicio AT TIME ZONE 'UTC',
    ALTER COLUMN fecha_fin TYPE timestamp(6) with time zone USING fecha_fin AT TIME ZONE 'UTC';

ALTER TABLE registros_blockchain
    ALTER COLUMN proximo_intento TYPE timestamp(6) with time zone USING proximo_intento AT TIME ZONE 'UTC',
    ALTER COLUMN fecha_envio TYPE timestamp(6) with time zone USING fecha_envio AT TIME ZONE 'UTC',
    ALTER COLUMN fecha_confirmacion TYPE timestamp(6) with time zone USING fecha_confirmacion AT TIME ZONE 'UTC';
