-- =============================================================================
-- V1__esquema_inicial.sql · MediChain (paso 10)
--
-- Esquema inicial completo de la base. Reproduce el que creaba Hibernate con
-- ddl-auto=update al cierre del paso 9, más el SQL de arranque que estaba en
-- InicializadorBaseDatos: secuencias de los códigos, índices únicos parciales
-- y la fila inicial de cadena_estado.
--
-- Diferencias con aquel esquema (verificadas comparando los catálogos):
--   * nombres de restricciones legibles: pk_<tabla>, uk_<tabla>_<columna>,
--     ck_<tabla>_<columna>, fk_<tabla>_<columna>; los ux_ se mantienen porque
--     los usa el código (RestriccionUnica);
--   * columnas en el orden de la entidad (id, datos, auditoría).
-- Las fechas de auditoría (fecha_creacion, fecha_actualizacion) quedan como
-- timestamp sin zona: las pasa a timestamptz V2__fechas_auditoria_utc.
--
-- Los CHECK de los enums tienen la lista COMPLETA de valores: un valor nuevo en
-- un enum guardado exige una migración que reemplace su CHECK.
--
-- NO EDITAR: Flyway compara el checksum de cada migración aplicada; un cambio
-- frena el arranque. Toda corrección va en una V nueva.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Tablas
-- -----------------------------------------------------------------------------

-- Cuentas de todos los roles (Sede, inspectores, empleados de empresa y pacientes). Contraseña en BCrypt.
CREATE TABLE usuarios (
    id uuid NOT NULL,
    email varchar(150) NOT NULL,
    password_hash varchar(255) NOT NULL,
    nombre varchar(150) NOT NULL,
    apellido varchar(150) NOT NULL,
    dni varchar(8) NOT NULL,
    rol varchar(30) NOT NULL,
    activo boolean NOT NULL,
    es_admin_empresa boolean NOT NULL,
    es_director_tecnico boolean NOT NULL,
    ultimo_login timestamp(6),
    empresa_id uuid,
    fecha_creacion timestamp(6) NOT NULL,
    fecha_actualizacion timestamp(6),
    version bigint NOT NULL,
    CONSTRAINT pk_usuarios PRIMARY KEY (id),
    CONSTRAINT uk_usuarios_email UNIQUE (email),
    CONSTRAINT ck_usuarios_rol CHECK (rol IN (
        'SEDE_CENTRAL', 'INSPECTOR', 'LABORATORIO', 'DISTRIBUIDOR', 'FARMACIA', 'PACIENTE'))
);

-- Laboratorios, distribuidoras y farmacias: CUIT, GLN, provincia y estado de habilitación (R2).
CREATE TABLE empresas (
    id uuid NOT NULL,
    tipo varchar(30) NOT NULL,
    cuit varchar(13) NOT NULL,
    razon_social varchar(200) NOT NULL,
    gln varchar(13),
    provincia varchar(30) NOT NULL,
    localidad varchar(150) NOT NULL,
    domicilio varchar(250) NOT NULL,
    numero_habilitacion varchar(50),
    director_tecnico varchar(200),
    documento_nombre varchar(255),
    documento_hash varchar(64),
    estado varchar(30) NOT NULL,
    fecha_solicitud timestamp(6) NOT NULL,
    fecha_habilitacion timestamp(6),
    motivo_rechazo text,
    motivo_suspension text,
    inspector_revisor_id uuid,
    inspector_habilitador_id uuid,
    fecha_creacion timestamp(6) NOT NULL,
    fecha_actualizacion timestamp(6),
    version bigint NOT NULL,
    CONSTRAINT pk_empresas PRIMARY KEY (id),
    CONSTRAINT uk_empresas_cuit UNIQUE (cuit),
    CONSTRAINT uk_empresas_gln UNIQUE (gln),
    CONSTRAINT ck_empresas_tipo CHECK (tipo IN (
        'LABORATORIO', 'DISTRIBUIDOR', 'FARMACIA')),
    CONSTRAINT ck_empresas_provincia CHECK (provincia IN (
        'BUENOS_AIRES', 'CABA', 'CATAMARCA', 'CHACO', 'CHUBUT', 'CORDOBA', 'CORRIENTES', 'ENTRE_RIOS',
        'FORMOSA', 'JUJUY', 'LA_PAMPA', 'LA_RIOJA', 'MENDOZA', 'MISIONES', 'NEUQUEN', 'RIO_NEGRO', 'SALTA',
        'SAN_JUAN', 'SAN_LUIS', 'SANTA_CRUZ', 'SANTA_FE', 'SANTIAGO_DEL_ESTERO', 'TIERRA_DEL_FUEGO', 'TUCUMAN')),
    CONSTRAINT ck_empresas_estado CHECK (estado IN (
        'PENDIENTE', 'HABILITADA', 'RECHAZADA', 'SUSPENDIDA'))
);

-- Inspectores ANMAT: provincia y estado (ACTIVO / BAJA). Su cuenta está en usuarios.
CREATE TABLE inspectores_anmat (
    id uuid NOT NULL,
    legajo varchar(20) NOT NULL,
    dni varchar(8) NOT NULL,
    provincia varchar(30) NOT NULL,
    estado varchar(30) NOT NULL,
    fecha_alta timestamp(6) NOT NULL,
    fecha_baja timestamp(6),
    usuario_id uuid NOT NULL,
    usuario_alta_id uuid NOT NULL,
    fecha_creacion timestamp(6) NOT NULL,
    fecha_actualizacion timestamp(6),
    version bigint NOT NULL,
    CONSTRAINT pk_inspectores_anmat PRIMARY KEY (id),
    CONSTRAINT uk_inspectores_anmat_legajo UNIQUE (legajo),
    CONSTRAINT uk_inspectores_anmat_dni UNIQUE (dni),
    CONSTRAINT uk_inspectores_anmat_usuario_id UNIQUE (usuario_id),
    CONSTRAINT ck_inspectores_anmat_provincia CHECK (provincia IN (
        'BUENOS_AIRES', 'CABA', 'CATAMARCA', 'CHACO', 'CHUBUT', 'CORDOBA', 'CORRIENTES', 'ENTRE_RIOS',
        'FORMOSA', 'JUJUY', 'LA_PAMPA', 'LA_RIOJA', 'MENDOZA', 'MISIONES', 'NEUQUEN', 'RIO_NEGRO', 'SALTA',
        'SAN_JUAN', 'SAN_LUIS', 'SANTA_CRUZ', 'SANTA_FE', 'SANTIAGO_DEL_ESTERO', 'TIERRA_DEL_FUEGO', 'TUCUMAN')),
    CONSTRAINT ck_inspectores_anmat_estado CHECK (estado IN (
        'ACTIVO', 'BAJA'))
);

-- Circuitos laboratorio → distribuidora → farmacia (R5). Endpoints /api/circuitos.
CREATE TABLE enlaces_cuit (
    id uuid NOT NULL,
    codigo varchar(20) NOT NULL,
    estado varchar(30) NOT NULL,
    fecha_propuesta timestamp(6) NOT NULL,
    fecha_aceptacion_distribuidor timestamp(6),
    fecha_aceptacion_farmacia timestamp(6),
    fecha_aprobacion timestamp(6),
    motivo_rechazo text,
    rechazado_por varchar(20),
    motivo_suspension text,
    laboratorio_id uuid NOT NULL,
    distribuidor_id uuid NOT NULL,
    farmacia_id uuid NOT NULL,
    propuesto_por_id uuid NOT NULL,
    inspector_revisor_id uuid,
    inspector_aprobador_id uuid,
    suspendido_por_empresa boolean NOT NULL,
    fecha_creacion timestamp(6) NOT NULL,
    fecha_actualizacion timestamp(6),
    version bigint NOT NULL,
    CONSTRAINT pk_enlaces_cuit PRIMARY KEY (id),
    CONSTRAINT uk_enlaces_cuit_codigo UNIQUE (codigo),
    CONSTRAINT ck_enlaces_cuit_estado CHECK (estado IN (
        'PENDIENTE_EMPRESAS', 'PENDIENTE_INSPECTOR', 'APROBADO', 'RECHAZADO', 'SUSPENDIDO')),
    CONSTRAINT ck_enlaces_cuit_rechazado_por CHECK (rechazado_por IN (
        'DISTRIBUIDOR', 'FARMACIA', 'INSPECTOR'))
);

-- Medicamentos de cada laboratorio: GTIN y rango de temperatura.
CREATE TABLE medicamentos (
    id uuid NOT NULL,
    gtin varchar(14) NOT NULL,
    nombre_comercial varchar(200) NOT NULL,
    principio_activo varchar(200) NOT NULL,
    concentracion varchar(100) NOT NULL,
    forma_farmaceutica varchar(100) NOT NULL,
    presentacion varchar(100) NOT NULL,
    temperatura_minima numeric(5,2),
    temperatura_maxima numeric(5,2),
    biologico boolean NOT NULL,
    activo boolean NOT NULL,
    laboratorio_id uuid NOT NULL,
    fecha_creacion timestamp(6) NOT NULL,
    fecha_actualizacion timestamp(6),
    version bigint NOT NULL,
    CONSTRAINT pk_medicamentos PRIMARY KEY (id),
    CONSTRAINT uk_medicamentos_gtin UNIQUE (gtin)
);

-- Lotes de un medicamento. Código único por laboratorio (ux_lote_laboratorio_codigo).
CREATE TABLE lotes (
    id uuid NOT NULL,
    codigo varchar(12) NOT NULL,
    fecha_fabricacion date NOT NULL,
    fecha_vencimiento date NOT NULL,
    cantidad integer NOT NULL,
    estado varchar(30) NOT NULL,
    estado_previo varchar(30),
    fecha_liberacion timestamp(6),
    medicamento_id uuid NOT NULL,
    laboratorio_id uuid NOT NULL,
    liberado_por_id uuid,
    fecha_creacion timestamp(6) NOT NULL,
    fecha_actualizacion timestamp(6),
    version bigint NOT NULL,
    CONSTRAINT pk_lotes PRIMARY KEY (id),
    CONSTRAINT ux_lote_laboratorio_codigo UNIQUE (laboratorio_id, codigo),
    CONSTRAINT ck_lotes_estado CHECK (estado IN (
        'PENDIENTE_LIBERACION', 'LIBERADO', 'CUARENTENA', 'RECALL')),
    CONSTRAINT ck_lotes_estado_previo CHECK (estado_previo IN (
        'PENDIENTE_LIBERACION', 'LIBERADO', 'CUARENTENA', 'RECALL'))
);

-- Cajas: GTIN + serie, serie única por GTIN (R3, ux_unidad_gtin_serie). GTIN desnormalizado de su medicamento.
CREATE TABLE unidades_trazables (
    id uuid NOT NULL,
    serie varchar(20) NOT NULL,
    gtin varchar(14) NOT NULL,
    estado varchar(30) NOT NULL,
    lote_id uuid NOT NULL,
    empresa_actual_id uuid,
    bulto_id uuid,
    fecha_creacion timestamp(6) NOT NULL,
    fecha_actualizacion timestamp(6),
    version bigint NOT NULL,
    CONSTRAINT pk_unidades_trazables PRIMARY KEY (id),
    CONSTRAINT ux_unidad_gtin_serie UNIQUE (gtin, serie),
    CONSTRAINT ck_unidades_trazables_estado CHECK (estado IN (
        'EN_LABORATORIO', 'EN_TRANSITO', 'EN_DEPOSITO', 'EN_STOCK', 'DISPENSADA', 'DEVUELTA', 'ROBADA',
        'RECHAZADA'))
);

-- Bultos cerrados: cajas de un solo lote LIBERADO con su circuito de destino (R6).
CREATE TABLE bultos (
    id uuid NOT NULL,
    codigo varchar(20) NOT NULL,
    cantidad integer NOT NULL,
    precinto varchar(50) NOT NULL,
    estado varchar(30) NOT NULL,
    fecha_armado timestamp(6) NOT NULL,
    lote_id uuid NOT NULL,
    destino_id uuid NOT NULL,
    ubicacion_empresa_id uuid,
    viaje_actual_id uuid,
    fecha_creacion timestamp(6) NOT NULL,
    fecha_actualizacion timestamp(6),
    version bigint NOT NULL,
    CONSTRAINT pk_bultos PRIMARY KEY (id),
    CONSTRAINT uk_bultos_codigo UNIQUE (codigo),
    CONSTRAINT ck_bultos_estado CHECK (estado IN (
        'ARMADO', 'EN_TRANSITO', 'EN_DEPOSITO', 'RECIBIDO', 'RECHAZADO', 'ROBADO', 'DESARMADO'))
);

-- Viajes (/api/viajes) del tramo 1 o 2 (R7).
CREATE TABLE despachos_logisticos (
    id uuid NOT NULL,
    codigo varchar(20) NOT NULL,
    tramo varchar(30) NOT NULL,
    patente varchar(10) NOT NULL,
    chofer varchar(200) NOT NULL,
    fecha_salida timestamp(6),
    fecha_estimada_entrega timestamp(6) NOT NULL,
    estado varchar(30) NOT NULL,
    motivo_cierre text,
    origen_id uuid NOT NULL,
    creado_por_id uuid NOT NULL,
    fecha_creacion timestamp(6) NOT NULL,
    fecha_actualizacion timestamp(6),
    version bigint NOT NULL,
    CONSTRAINT pk_despachos_logisticos PRIMARY KEY (id),
    CONSTRAINT uk_despachos_logisticos_codigo UNIQUE (codigo),
    CONSTRAINT ck_despachos_logisticos_tramo CHECK (tramo IN (
        'LAB_A_DISTRIBUIDOR', 'DISTRIBUIDOR_A_FARMACIA')),
    CONSTRAINT ck_despachos_logisticos_estado CHECK (estado IN (
        'PROGRAMADO', 'EN_TRANSITO', 'FINALIZADO', 'ROBADO', 'CANCELADO'))
);

-- Bultos de cada viaje: se fijan al crearlo.
CREATE TABLE despacho_bulto (
    despacho_id uuid NOT NULL,
    bulto_id uuid NOT NULL,
    CONSTRAINT pk_despacho_bulto PRIMARY KEY (despacho_id, bulto_id)
);

-- Lecturas de temperatura de un viaje (R9).
CREATE TABLE telemetria_temperatura (
    id uuid NOT NULL,
    sensor_id varchar(100) NOT NULL,
    temperatura numeric(5,2) NOT NULL,
    fuera_de_rango boolean NOT NULL,
    fecha_hora timestamp(6) NOT NULL,
    despacho_id uuid NOT NULL,
    fecha_creacion timestamp(6) NOT NULL,
    fecha_actualizacion timestamp(6),
    version bigint NOT NULL,
    CONSTRAINT pk_telemetria_temperatura PRIMARY KEY (id)
);

-- Lecturas de posición de un viaje.
CREATE TABLE telemetria_gps (
    id uuid NOT NULL,
    sensor_id varchar(100) NOT NULL,
    latitud double precision NOT NULL,
    longitud double precision NOT NULL,
    lugar varchar(255),
    fecha_hora timestamp(6) NOT NULL,
    despacho_id uuid NOT NULL,
    fecha_creacion timestamp(6) NOT NULL,
    fecha_actualizacion timestamp(6),
    version bigint NOT NULL,
    CONSTRAINT pk_telemetria_gps PRIMARY KEY (id)
);

-- Recepción de un bulto por su empresa destino (R8).
CREATE TABLE recepciones (
    id uuid NOT NULL,
    fecha_hora timestamp(6) NOT NULL,
    temperatura numeric(5,2) NOT NULL,
    precinto_intacto boolean NOT NULL,
    cantidad_verificada integer NOT NULL,
    conforme boolean NOT NULL,
    motivo_rechazo varchar(200),
    observacion text,
    bulto_id uuid NOT NULL,
    despacho_id uuid NOT NULL,
    receptora_id uuid NOT NULL,
    registrada_por_id uuid NOT NULL,
    fecha_creacion timestamp(6) NOT NULL,
    fecha_actualizacion timestamp(6),
    version bigint NOT NULL,
    CONSTRAINT pk_recepciones PRIMARY KEY (id)
);

-- Dispensación de una caja (R11). R13: el DNI se guarda solo enmascarado.
CREATE TABLE dispensaciones (
    id uuid NOT NULL,
    fecha_hora timestamp(6) NOT NULL,
    particular boolean NOT NULL,
    obra_social varchar(200),
    numero_afiliado varchar(50),
    numero_receta varchar(100) NOT NULL,
    dni_enmascarado varchar(8),
    anulada boolean NOT NULL,
    fecha_anulacion timestamp(6),
    motivo_anulacion text,
    unidad_trazable_id uuid NOT NULL,
    farmacia_id uuid NOT NULL,
    farmaceutico_id uuid NOT NULL,
    fecha_creacion timestamp(6) NOT NULL,
    fecha_actualizacion timestamp(6),
    version bigint NOT NULL,
    CONSTRAINT pk_dispensaciones PRIMARY KEY (id)
);

-- Reportes de pacientes sobre una caja.
CREATE TABLE reportes_ciudadanos (
    id uuid NOT NULL,
    codigo varchar(20) NOT NULL,
    gtin_reportado varchar(14) NOT NULL,
    serie_reportada varchar(20) NOT NULL,
    motivo varchar(30) NOT NULL,
    descripcion text,
    estado varchar(30) NOT NULL,
    conclusion text,
    provincia varchar(30) NOT NULL,
    fecha_reporte timestamp(6) NOT NULL,
    fecha_cierre timestamp(6),
    paciente_id uuid NOT NULL,
    investiga_id uuid,
    unidad_trazable_id uuid,
    fecha_creacion timestamp(6) NOT NULL,
    fecha_actualizacion timestamp(6),
    version bigint NOT NULL,
    CONSTRAINT pk_reportes_ciudadanos PRIMARY KEY (id),
    CONSTRAINT uk_reportes_ciudadanos_codigo UNIQUE (codigo),
    CONSTRAINT ck_reportes_ciudadanos_motivo CHECK (motivo IN (
        'SOSPECHA_FALSIFICACION', 'ENVASE_DANADO', 'EFECTO_ADVERSO', 'OTRO')),
    CONSTRAINT ck_reportes_ciudadanos_estado CHECK (estado IN (
        'ABIERTO', 'EN_INVESTIGACION', 'CERRADO')),
    CONSTRAINT ck_reportes_ciudadanos_provincia CHECK (provincia IN (
        'BUENOS_AIRES', 'CABA', 'CATAMARCA', 'CHACO', 'CHUBUT', 'CORDOBA', 'CORRIENTES', 'ENTRE_RIOS',
        'FORMOSA', 'JUJUY', 'LA_PAMPA', 'LA_RIOJA', 'MENDOZA', 'MISIONES', 'NEUQUEN', 'RIO_NEGRO', 'SALTA',
        'SAN_JUAN', 'SAN_LUIS', 'SANTA_CRUZ', 'SANTA_FE', 'SANTIAGO_DEL_ESTERO', 'TIERRA_DEL_FUEGO', 'TUCUMAN'))
);

-- Cuarentenas y recalls de alcance LOTE, DESPACHO o BULTO (R10, R12).
CREATE TABLE cuarentenas (
    id uuid NOT NULL,
    alcance varchar(30) NOT NULL,
    tipo varchar(30) NOT NULL,
    motivo varchar(30) NOT NULL,
    descripcion text,
    automatica boolean NOT NULL,
    estado varchar(30) NOT NULL,
    dictamen text,
    provincia varchar(30) NOT NULL,
    fecha_inicio timestamp(6) NOT NULL,
    fecha_fin timestamp(6),
    lote_id uuid,
    despacho_id uuid,
    inspector_id uuid,
    inspector_revisor_id uuid,
    reporte_origen_id uuid,
    fecha_creacion timestamp(6) NOT NULL,
    fecha_actualizacion timestamp(6),
    version bigint NOT NULL,
    CONSTRAINT pk_cuarentenas PRIMARY KEY (id),
    CONSTRAINT ck_cuarentenas_alcance CHECK (alcance IN (
        'LOTE', 'DESPACHO', 'BULTO')),
    CONSTRAINT ck_cuarentenas_tipo CHECK (tipo IN (
        'CUARENTENA', 'RECALL')),
    CONSTRAINT ck_cuarentenas_motivo CHECK (motivo IN (
        'PREVENTIVA', 'DEFECTO_CALIDAD', 'RUPTURA_FRIO', 'ROBO', 'RECHAZO_RECEPCION')),
    CONSTRAINT ck_cuarentenas_estado CHECK (estado IN (
        'ACTIVA', 'LEVANTADA', 'CONVERTIDA_EN_RECALL')),
    CONSTRAINT ck_cuarentenas_provincia CHECK (provincia IN (
        'BUENOS_AIRES', 'CABA', 'CATAMARCA', 'CHACO', 'CHUBUT', 'CORDOBA', 'CORRIENTES', 'ENTRE_RIOS',
        'FORMOSA', 'JUJUY', 'LA_PAMPA', 'LA_RIOJA', 'MENDOZA', 'MISIONES', 'NEUQUEN', 'RIO_NEGRO', 'SALTA',
        'SAN_JUAN', 'SAN_LUIS', 'SANTA_CRUZ', 'SANTA_FE', 'SANTIAGO_DEL_ESTERO', 'TIERRA_DEL_FUEGO', 'TUCUMAN'))
);

-- Bultos alcanzados por una cuarentena de alcance DESPACHO o BULTO.
CREATE TABLE cuarentena_bulto (
    cuarentena_id uuid NOT NULL,
    bulto_id uuid NOT NULL,
    CONSTRAINT pk_cuarentena_bulto PRIMARY KEY (cuarentena_id, bulto_id)
);

-- Un evento SERIE_INEXISTENTE / SERIE_ROBADA por (tipo, GTIN, serie) y por día UTC.
CREATE TABLE intento_verificacion (
    id uuid NOT NULL,
    tipo varchar(40) NOT NULL,
    gtin varchar(14) NOT NULL,
    serie varchar(20) NOT NULL,
    fecha date NOT NULL,
    fecha_creacion timestamp(6) NOT NULL,
    fecha_actualizacion timestamp(6),
    version bigint NOT NULL,
    CONSTRAINT pk_intento_verificacion PRIMARY KEY (id),
    CONSTRAINT ux_intento_verificacion_dia UNIQUE (tipo, gtin, serie, fecha),
    CONSTRAINT ck_intento_verificacion_tipo CHECK (tipo IN (
        'ALTA_INSPECTOR', 'BAJA_INSPECTOR', 'REACTIVACION_INSPECTOR', 'SOLICITUD_HABILITACION',
        'SOLICITUD_TOMADA', 'SOLICITUD_ASIGNADA', 'HABILITACION_APROBADA', 'HABILITACION_RECHAZADA',
        'EMPRESA_SUSPENDIDA', 'EMPRESA_REHABILITADA', 'CIRCUITO_PROPUESTO', 'CIRCUITO_ACEPTADO',
        'CIRCUITO_RECHAZADO', 'CIRCUITO_TOMADO', 'CIRCUITO_ASIGNADO', 'CIRCUITO_APROBADO',
        'CIRCUITO_SUSPENDIDO', 'CIRCUITO_REHABILITADO', 'MEDICAMENTO_REGISTRADO', 'LOTE_REGISTRADO',
        'LOTE_LIBERADO', 'INTENTO_SERIE_INVALIDA', 'BULTO_ARMADO', 'BULTO_DESARMADO', 'VIAJE_CREADO',
        'VIAJE_SALIDA', 'VIAJE_CANCELADO', 'RUPTURA_FRIO', 'VIAJE_FINALIZADO', 'ROBO_EXTRAVIO',
        'BULTO_RECIBIDO', 'BULTO_RECHAZADO', 'BULTO_INEXISTENTE', 'BULTO_DUPLICADO', 'CUARENTENA',
        'CUARENTENA_TOMADA', 'CUARENTENA_LEVANTADA', 'RECALL', 'DISPENSACION', 'ANULACION_DISPENSA',
        'DEVOLUCION', 'INTENTO_DUPLICADO', 'SERIE_INEXISTENTE', 'SERIE_ROBADA', 'REPORTE_CIUDADANO',
        'REPORTE_TOMADO', 'REPORTE_CERRADO'))
);

-- Fila única con el último número y hash de la cadena de eventos (se bloquea con FOR UPDATE).
CREATE TABLE cadena_estado (
    id uuid NOT NULL,
    nombre varchar(20) NOT NULL,
    ultimo_numero bigint NOT NULL,
    ultimo_hash varchar(64) NOT NULL,
    fecha_creacion timestamp(6) NOT NULL,
    fecha_actualizacion timestamp(6),
    version bigint NOT NULL,
    CONSTRAINT pk_cadena_estado PRIMARY KEY (id),
    CONSTRAINT uk_cadena_estado_nombre UNIQUE (nombre)
);

-- Cadena de eventos encadenados por hash SHA-256 (R15). Solo inserciones.
CREATE TABLE eventos_trazabilidad (
    id uuid NOT NULL,
    numero bigint NOT NULL,
    tipo varchar(40) NOT NULL,
    fecha_hora timestamp(6) with time zone NOT NULL,
    entidad_tipo varchar(100) NOT NULL,
    entidad_id uuid NOT NULL,
    datos_json text NOT NULL,
    actor_usuario_id uuid,
    actor_empresa_id uuid,
    hash_anterior varchar(64) NOT NULL,
    hash varchar(64) NOT NULL,
    fecha_creacion timestamp(6) NOT NULL,
    fecha_actualizacion timestamp(6),
    version bigint NOT NULL,
    CONSTRAINT pk_eventos_trazabilidad PRIMARY KEY (id),
    CONSTRAINT uk_eventos_trazabilidad_numero UNIQUE (numero),
    CONSTRAINT uk_eventos_trazabilidad_hash_anterior UNIQUE (hash_anterior),
    CONSTRAINT uk_eventos_trazabilidad_hash UNIQUE (hash),
    CONSTRAINT ck_eventos_trazabilidad_tipo CHECK (tipo IN (
        'ALTA_INSPECTOR', 'BAJA_INSPECTOR', 'REACTIVACION_INSPECTOR', 'SOLICITUD_HABILITACION',
        'SOLICITUD_TOMADA', 'SOLICITUD_ASIGNADA', 'HABILITACION_APROBADA', 'HABILITACION_RECHAZADA',
        'EMPRESA_SUSPENDIDA', 'EMPRESA_REHABILITADA', 'CIRCUITO_PROPUESTO', 'CIRCUITO_ACEPTADO',
        'CIRCUITO_RECHAZADO', 'CIRCUITO_TOMADO', 'CIRCUITO_ASIGNADO', 'CIRCUITO_APROBADO',
        'CIRCUITO_SUSPENDIDO', 'CIRCUITO_REHABILITADO', 'MEDICAMENTO_REGISTRADO', 'LOTE_REGISTRADO',
        'LOTE_LIBERADO', 'INTENTO_SERIE_INVALIDA', 'BULTO_ARMADO', 'BULTO_DESARMADO', 'VIAJE_CREADO',
        'VIAJE_SALIDA', 'VIAJE_CANCELADO', 'RUPTURA_FRIO', 'VIAJE_FINALIZADO', 'ROBO_EXTRAVIO',
        'BULTO_RECIBIDO', 'BULTO_RECHAZADO', 'BULTO_INEXISTENTE', 'BULTO_DUPLICADO', 'CUARENTENA',
        'CUARENTENA_TOMADA', 'CUARENTENA_LEVANTADA', 'RECALL', 'DISPENSACION', 'ANULACION_DISPENSA',
        'DEVOLUCION', 'INTENTO_DUPLICADO', 'SERIE_INEXISTENTE', 'SERIE_ROBADA', 'REPORTE_CIUDADANO',
        'REPORTE_TOMADO', 'REPORTE_CERRADO'))
);

-- Anclajes del último hash de la cadena en Ethereum Sepolia (R15).
CREATE TABLE registros_blockchain (
    id uuid NOT NULL,
    desde_numero bigint NOT NULL,
    hasta_numero bigint NOT NULL,
    hash_anclado varchar(66) NOT NULL,
    red varchar(50) NOT NULL,
    direccion_contrato varchar(42),
    transaction_hash varchar(66),
    nonce bigint,
    limite_gas bigint,
    comision_maxima_wei bigint,
    propina_wei bigint,
    bloque bigint,
    confirmaciones integer NOT NULL,
    gas_usado bigint,
    precio_efectivo_wei bigint,
    estado varchar(30) NOT NULL,
    intentos integer NOT NULL,
    causa_fallo varchar(30),
    ultimo_error text,
    proximo_intento timestamp(6),
    fecha_envio timestamp(6),
    fecha_confirmacion timestamp(6),
    fecha_creacion timestamp(6) NOT NULL,
    fecha_actualizacion timestamp(6),
    version bigint NOT NULL,
    CONSTRAINT pk_registros_blockchain PRIMARY KEY (id),
    CONSTRAINT ck_registros_blockchain_estado CHECK (estado IN (
        'PENDIENTE', 'ENVIADO', 'CONFIRMADO', 'FALLIDO')),
    CONSTRAINT ck_registros_blockchain_causa_fallo CHECK (causa_fallo IN (
        'REVERT', 'SIN_GAS', 'GAS_SOBRE_EL_MAXIMO', 'SIN_INCLUIR', 'ERROR_DE_RED'))
);

-- ---------------------------------------------------------------------------
-- Claves foráneas (al final: hay referencias circulares entre usuarios, empresas e inspectores_anmat).
-- ---------------------------------------------------------------------------

ALTER TABLE usuarios ADD CONSTRAINT fk_usuarios_empresa_id
    FOREIGN KEY (empresa_id) REFERENCES empresas (id);

ALTER TABLE empresas ADD CONSTRAINT fk_empresas_inspector_habilitador_id
    FOREIGN KEY (inspector_habilitador_id) REFERENCES inspectores_anmat (id);
ALTER TABLE empresas ADD CONSTRAINT fk_empresas_inspector_revisor_id
    FOREIGN KEY (inspector_revisor_id) REFERENCES inspectores_anmat (id);

ALTER TABLE inspectores_anmat ADD CONSTRAINT fk_inspectores_anmat_usuario_alta_id
    FOREIGN KEY (usuario_alta_id) REFERENCES usuarios (id);
ALTER TABLE inspectores_anmat ADD CONSTRAINT fk_inspectores_anmat_usuario_id
    FOREIGN KEY (usuario_id) REFERENCES usuarios (id);

ALTER TABLE enlaces_cuit ADD CONSTRAINT fk_enlaces_cuit_distribuidor_id
    FOREIGN KEY (distribuidor_id) REFERENCES empresas (id);
ALTER TABLE enlaces_cuit ADD CONSTRAINT fk_enlaces_cuit_farmacia_id
    FOREIGN KEY (farmacia_id) REFERENCES empresas (id);
ALTER TABLE enlaces_cuit ADD CONSTRAINT fk_enlaces_cuit_inspector_aprobador_id
    FOREIGN KEY (inspector_aprobador_id) REFERENCES inspectores_anmat (id);
ALTER TABLE enlaces_cuit ADD CONSTRAINT fk_enlaces_cuit_inspector_revisor_id
    FOREIGN KEY (inspector_revisor_id) REFERENCES inspectores_anmat (id);
ALTER TABLE enlaces_cuit ADD CONSTRAINT fk_enlaces_cuit_laboratorio_id
    FOREIGN KEY (laboratorio_id) REFERENCES empresas (id);
ALTER TABLE enlaces_cuit ADD CONSTRAINT fk_enlaces_cuit_propuesto_por_id
    FOREIGN KEY (propuesto_por_id) REFERENCES usuarios (id);

ALTER TABLE medicamentos ADD CONSTRAINT fk_medicamentos_laboratorio_id
    FOREIGN KEY (laboratorio_id) REFERENCES empresas (id);

ALTER TABLE lotes ADD CONSTRAINT fk_lotes_laboratorio_id
    FOREIGN KEY (laboratorio_id) REFERENCES empresas (id);
ALTER TABLE lotes ADD CONSTRAINT fk_lotes_liberado_por_id
    FOREIGN KEY (liberado_por_id) REFERENCES usuarios (id);
ALTER TABLE lotes ADD CONSTRAINT fk_lotes_medicamento_id
    FOREIGN KEY (medicamento_id) REFERENCES medicamentos (id);

ALTER TABLE unidades_trazables ADD CONSTRAINT fk_unidades_trazables_bulto_id
    FOREIGN KEY (bulto_id) REFERENCES bultos (id);
ALTER TABLE unidades_trazables ADD CONSTRAINT fk_unidades_trazables_empresa_actual_id
    FOREIGN KEY (empresa_actual_id) REFERENCES empresas (id);
ALTER TABLE unidades_trazables ADD CONSTRAINT fk_unidades_trazables_lote_id
    FOREIGN KEY (lote_id) REFERENCES lotes (id);

ALTER TABLE bultos ADD CONSTRAINT fk_bultos_destino_id
    FOREIGN KEY (destino_id) REFERENCES enlaces_cuit (id);
ALTER TABLE bultos ADD CONSTRAINT fk_bultos_lote_id
    FOREIGN KEY (lote_id) REFERENCES lotes (id);
ALTER TABLE bultos ADD CONSTRAINT fk_bultos_ubicacion_empresa_id
    FOREIGN KEY (ubicacion_empresa_id) REFERENCES empresas (id);
ALTER TABLE bultos ADD CONSTRAINT fk_bultos_viaje_actual_id
    FOREIGN KEY (viaje_actual_id) REFERENCES despachos_logisticos (id);

ALTER TABLE despachos_logisticos ADD CONSTRAINT fk_despachos_logisticos_creado_por_id
    FOREIGN KEY (creado_por_id) REFERENCES usuarios (id);
ALTER TABLE despachos_logisticos ADD CONSTRAINT fk_despachos_logisticos_origen_id
    FOREIGN KEY (origen_id) REFERENCES empresas (id);

ALTER TABLE despacho_bulto ADD CONSTRAINT fk_despacho_bulto_bulto_id
    FOREIGN KEY (bulto_id) REFERENCES bultos (id);
ALTER TABLE despacho_bulto ADD CONSTRAINT fk_despacho_bulto_despacho_id
    FOREIGN KEY (despacho_id) REFERENCES despachos_logisticos (id);

ALTER TABLE telemetria_temperatura ADD CONSTRAINT fk_telemetria_temperatura_despacho_id
    FOREIGN KEY (despacho_id) REFERENCES despachos_logisticos (id);

ALTER TABLE telemetria_gps ADD CONSTRAINT fk_telemetria_gps_despacho_id
    FOREIGN KEY (despacho_id) REFERENCES despachos_logisticos (id);

ALTER TABLE recepciones ADD CONSTRAINT fk_recepciones_bulto_id
    FOREIGN KEY (bulto_id) REFERENCES bultos (id);
ALTER TABLE recepciones ADD CONSTRAINT fk_recepciones_despacho_id
    FOREIGN KEY (despacho_id) REFERENCES despachos_logisticos (id);
ALTER TABLE recepciones ADD CONSTRAINT fk_recepciones_receptora_id
    FOREIGN KEY (receptora_id) REFERENCES empresas (id);
ALTER TABLE recepciones ADD CONSTRAINT fk_recepciones_registrada_por_id
    FOREIGN KEY (registrada_por_id) REFERENCES usuarios (id);

ALTER TABLE dispensaciones ADD CONSTRAINT fk_dispensaciones_farmaceutico_id
    FOREIGN KEY (farmaceutico_id) REFERENCES usuarios (id);
ALTER TABLE dispensaciones ADD CONSTRAINT fk_dispensaciones_farmacia_id
    FOREIGN KEY (farmacia_id) REFERENCES empresas (id);
ALTER TABLE dispensaciones ADD CONSTRAINT fk_dispensaciones_unidad_trazable_id
    FOREIGN KEY (unidad_trazable_id) REFERENCES unidades_trazables (id);

ALTER TABLE reportes_ciudadanos ADD CONSTRAINT fk_reportes_ciudadanos_investiga_id
    FOREIGN KEY (investiga_id) REFERENCES inspectores_anmat (id);
ALTER TABLE reportes_ciudadanos ADD CONSTRAINT fk_reportes_ciudadanos_paciente_id
    FOREIGN KEY (paciente_id) REFERENCES usuarios (id);
ALTER TABLE reportes_ciudadanos ADD CONSTRAINT fk_reportes_ciudadanos_unidad_trazable_id
    FOREIGN KEY (unidad_trazable_id) REFERENCES unidades_trazables (id);

ALTER TABLE cuarentenas ADD CONSTRAINT fk_cuarentenas_despacho_id
    FOREIGN KEY (despacho_id) REFERENCES despachos_logisticos (id);
ALTER TABLE cuarentenas ADD CONSTRAINT fk_cuarentenas_inspector_id
    FOREIGN KEY (inspector_id) REFERENCES inspectores_anmat (id);
ALTER TABLE cuarentenas ADD CONSTRAINT fk_cuarentenas_inspector_revisor_id
    FOREIGN KEY (inspector_revisor_id) REFERENCES inspectores_anmat (id);
ALTER TABLE cuarentenas ADD CONSTRAINT fk_cuarentenas_lote_id
    FOREIGN KEY (lote_id) REFERENCES lotes (id);
ALTER TABLE cuarentenas ADD CONSTRAINT fk_cuarentenas_reporte_origen_id
    FOREIGN KEY (reporte_origen_id) REFERENCES reportes_ciudadanos (id);

ALTER TABLE cuarentena_bulto ADD CONSTRAINT fk_cuarentena_bulto_bulto_id
    FOREIGN KEY (bulto_id) REFERENCES bultos (id);
ALTER TABLE cuarentena_bulto ADD CONSTRAINT fk_cuarentena_bulto_cuarentena_id
    FOREIGN KEY (cuarentena_id) REFERENCES cuarentenas (id);

-- -----------------------------------------------------------------------------
-- Secuencias de los códigos legibles. nextval es atómico: dos altas simultáneas
-- nunca reciben el mismo número.
-- -----------------------------------------------------------------------------

CREATE SEQUENCE circuito_codigo_seq START WITH 1 INCREMENT BY 1; -- CIR-0001
CREATE SEQUENCE bulto_codigo_seq START WITH 1 INCREMENT BY 1;    -- BUL-0001
CREATE SEQUENCE viaje_codigo_seq START WITH 1 INCREMENT BY 1;    -- VJ-0001
CREATE SEQUENCE reporte_codigo_seq START WITH 1 INCREMENT BY 1;  -- REP-0001

-- -----------------------------------------------------------------------------
-- Índices únicos parciales (redes de seguridad ante altas simultáneas)
-- -----------------------------------------------------------------------------

-- R5: un solo circuito VIGENTE por par laboratorio–farmacia (RECHAZADO no cuenta).
-- EnlaceCuitService traduce su violación a 409 R5.
CREATE UNIQUE INDEX ux_circuito_par_vigente ON enlaces_cuit (laboratorio_id, farmacia_id)
    WHERE estado IN ('PENDIENTE_EMPRESAS', 'PENDIENTE_INSPECTOR', 'APROBADO', 'SUSPENDIDO');

-- R15: a lo sumo un anclaje en curso (PENDIENTE o ENVIADO) por red.
CREATE UNIQUE INDEX ux_anclaje_en_curso ON registros_blockchain (red)
    WHERE estado IN ('PENDIENTE', 'ENVIADO');

-- -----------------------------------------------------------------------------
-- Datos que el esquema necesita para funcionar
-- -----------------------------------------------------------------------------

-- Estado inicial de la cadena de eventos: número 0, hash GENESIS (RegistradorEventos
-- lo bloquea con FOR UPDATE en cada alta). Fechas en hora UTC, como las guarda la app.
INSERT INTO cadena_estado (id, nombre, ultimo_numero, ultimo_hash, fecha_creacion, fecha_actualizacion, version)
VALUES (gen_random_uuid(), 'PRINCIPAL', 0, 'GENESIS', now() AT TIME ZONE 'UTC', now() AT TIME ZONE 'UTC', 0);
