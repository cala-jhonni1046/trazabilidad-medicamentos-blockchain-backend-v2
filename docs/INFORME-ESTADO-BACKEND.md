# Informe de estado — Backend MediChain

- **Fecha:** 2026-10-24 (hora de Argentina, UTC−3; en UTC ya es 2026-10-25)
- **Commit:** `6f0a7fe` — *Bitácora: paso B12 hecho (contrato, filtros, nombres, tablero, trazabilidad por código, demo y contrato nuevo por script)*
- **Alcance:** este informe describe el backend tal como está en el código en ese commit. Cada dato se verificó leyendo las fuentes citadas entre paréntesis. Lo que no se pudo confirmar está en la sección 18.
- **Lectura:** pensado para leerse completo en ~20 minutos.

---

## 1. Qué es el sistema

MediChain es un backend de **trazabilidad de medicamentos para Argentina** (contexto ANMAT). Registra cada movimiento de un medicamento desde el laboratorio hasta el paciente —fabricación del lote, armado de bultos, despacho, recepción, dispensación— como una **cadena única de eventos encadenados por hash SHA-256**. El último hash se **ancla periódicamente en Ethereum Sepolia**, para que cualquiera pueda auditar que la historia no se alteró. Participan laboratorios, distribuidoras, farmacias, inspectores de ANMAT, una Sede central y pacientes. Expone una API REST (sin frontend propio: el frontend es un proyecto Angular aparte que genera su cliente desde el contrato OpenAPI). Cuida el aislamiento entre empresas, la privacidad del paciente (Ley 25.326) y la integridad de la cadena. Es el trabajo de backend de un proyecto académico/hackathon.

## 2. Stack y versiones exactas (`pom.xml`, `contracts/MediChainAnchor.sol`)

| Componente | Versión |
|---|---|
| Java | 21 |
| Spring Boot (parent) | 4.1.1 |
| springdoc-openapi-starter-webmvc-ui | 3.1.0 |
| jjwt (api/impl/jackson) | 0.13.0 |
| web3j core | 6.0.0 (sin el módulo `kms` de AWS, excluido) |
| PostgreSQL driver | el que gestiona Spring Boot 4.1.1 |
| Flyway | `spring-boot-starter-flyway` + `flyway-database-postgresql` (gestionados por Boot) |
| Testcontainers | `spring-boot-testcontainers` + `testcontainers-junit-jupiter` + `testcontainers-postgresql` (gestionados por Boot) |
| Solidity (contrato) | 0.8.37 (`pragma solidity 0.8.37;`) |
| groupId / artifactId | `com.trazabilidad` / `con-blockchain` `0.0.1-SNAPSHOT` |

Starters usados: data-jpa, security, webmvc, validation (más los `*-test`). El plugin **failsafe** corre los `**/*IT.java`; para omitir integración: `./mvnw verify -DskipITs`.

## 3. Estructura de carpetas y paquetes

Paquete base `com.medichain` (`src/main/java/com/medichain/`):

- **`MedichainApplication.java`** — arranque.
- **`config/`** — configuración transversal: `SecurityConfig`, `JwtAuthenticationFilter`, `PasswordConfig` (BCrypt), `OpenApiConfig` (contrato), `RespuestasError` (anotación de errores), `AnclajeConfig`, `ProgramacionConfig` (@Scheduled), `ZonaHorariaConfig`, `DatosIniciales` (Sede al arrancar), `DatosDemo` (perfil demo), `EjecutorComoUsuario`, `RestAccessDeniedHandler`/`RestAuthenticationEntryPoint` (401/403 como JSON).
- **`exceptions/`** — `ResourceNotFoundException` (404), `ReglaNegocioException(regla, mensaje)` (409), `ConsultaInvalidaException` (400).
- **`utils/`** — `BaseEntity`, `ErrorResponseDTO`, `GlobalExceptionHandler`, `Tiempo`, `Calendario`, `BusquedaTexto`, `RestriccionUnica`, y los subpaquetes `enums/` (p. ej. `Provincia`), `seguridad/` (`UsuarioActual`, `VerificadorEmpresa`, `VerificadorUsuario`), `validacion/` (`Gs1Util`, `SerieUtil`, `CuitUtil`, `DniUtil`), `documentos/`.
- **`modules/<modulo>/`** — una carpeta por funcionalidad. Cada módulo de dominio tiene su Entidad, `*RequestDTO`, `*ResponseDTO`, `*Mapper`, `*Repository`, `*Service`, `*Controller` y sus enums.

**19 carpetas de módulo:** los 17 de dominio (`empresa`, `usuario`, `inspectoranmat`, `enlacecuit`, `medicamento`, `lote`, `unidadtrazable`, `bulto`, `despachologistico`, `telemetriatemperatura`, `telemetriagps`, `recepcion`, `dispensacion`, `cuarentena`, `reporteciudadano`, `trazabilidad`, `registroblockchain`) más **`auth`** (login/JWT) y **`tablero`** (totales para el frontend, sin entidad propia).

## 4. Modelo de dominio

19 entidades (17 de dominio + `CadenaEstado` + `IntentoVerificacion`), en **21 tablas en total, que incluyen las 2 de unión** (`despacho_bulto`, `cuarentena_bulto`) — es decir, 19 tablas de entidad + 2 de unión = 21, como en la sección 12. Todas las entidades extienden `BaseEntity` (`utils/BaseEntity.java`): `id` UUID, `fechaCreacion`/`fechaActualizacion` (`Instant`, UTC), `@Version` (locking optimista).

### Entidades, relaciones (FK `@ManyToOne LAZY`) y estados

| Entidad (tabla) | Campos clave | Relaciones (FK) | Enum de estado y valores |
|---|---|---|---|
| **Empresa** (`empresas`) | cuit, gln, razonSocial, provincia, tipo (LABORATORIO/DISTRIBUIDOR/FARMACIA), documentoHash, fechaSolicitud, fechaHabilitacion | `inspector_revisor_id`, `inspector_habilitador_id` | `EstadoHabilitacion`: PENDIENTE, HABILITADA, RECHAZADA, SUSPENDIDA |
| **Usuario** (`usuarios`) | email, passwordHash, rol, nombre, apellido, activo, esAdminEmpresa, esDirectorTecnico, ultimoLogin | `empresa_id` | — (rol: enum `RolUsuario`) |
| **InspectorAnmat** (`inspectores_anmat`) | legajo, dni, nombre, apellido, provincia, fechaAlta, fechaBaja | `usuario_id`, `usuario_alta_id` | `EstadoInspector`: ACTIVO, BAJA |
| **EnlaceCuit** = circuito (`enlaces_cuit`) | codigo (CIR-####), fechaPropuesta/Aceptacion*/Aprobacion | `laboratorio_id`, `distribuidor_id`, `farmacia_id`, `propuesto_por_id`, `inspector_revisor_id`, `inspector_aprobador_id` | `EstadoEnlaceCuit`: PENDIENTE_EMPRESAS, PENDIENTE_INSPECTOR, APROBADO, RECHAZADO, SUSPENDIDO |
| **Medicamento** (`medicamentos`) | gtin (único), nombreComercial, principioActivo, concentracion, formaFarmaceutica, presentacion, temperaturaMinima/Maxima, biologico, activo | `laboratorio_id` | — |
| **Lote** (`lotes`) | codigo (único por lab), fechaFabricacion, fechaVencimiento (`LocalDate`), cantidad, fechaLiberacion | `medicamento_id`, `laboratorio_id`, `liberado_por_id` | `EstadoLote`: PENDIENTE_LIBERACION, LIBERADO, CUARENTENA, RECALL |
| **UnidadTrazable** = caja (`unidades_trazables`) | gtin (desnormalizado), serie (único por gtin) | `lote_id`, `empresa_actual_id`, `bulto_id` | `EstadoUnidad`: EN_LABORATORIO, EN_TRANSITO, EN_DEPOSITO, EN_STOCK, DISPENSADA, DEVUELTA, ROBADA, RECHAZADA |
| **Bulto** (`bultos`) | codigo (BUL-####), gtin, cantidad, fechaArmado | `lote_id`, `destino_id` (circuito), `ubicacion_empresa_id`, `viaje_actual_id` | `EstadoBulto`: ARMADO, EN_TRANSITO, EN_DEPOSITO, RECIBIDO, RECHAZADO, ROBADO, DESARMADO |
| **DespachoLogistico** = viaje (`despachos_logisticos`) | codigo (VJ-####), tramo, patente, chofer, fechaSalida, fechaEstimadaEntrega | `origen_id`, `creado_por_id`; **`@ManyToMany` `bultos`** (`despacho_bulto`) | `EstadoDespacho`: PROGRAMADO, EN_TRANSITO, FINALIZADO, ROBADO, CANCELADO |
| **TelemetriaTemperatura** (`telemetria_temperatura`) | sensorId, temperatura, fueraDeRango, fechaHora | `despacho_id` | — |
| **TelemetriaGps** (`telemetria_gps`) | sensorId, latitud, longitud, lugar, fechaHora | `despacho_id` | — |
| **Recepcion** (`recepciones`) | conforme, motivos, cantidad, fechaHora | `bulto_id`, `despacho_id`, `receptora_id`, `registrada_por_id` | — |
| **Dispensacion** (`dispensaciones`) | obraSocial, afiliado, particular, dniEnmascarado, fechaHora, fechaAnulacion, motivoAnulacion | `unidad_trazable_id`, `farmacia_id`, `farmaceutico_id` | — |
| **Cuarentena** (`cuarentenas`) | alcance (LOTE/DESPACHO/BULTO), motivo, fundamento/conclusión (hash), fechaInicio, fechaFin | `lote_id`, `despacho_id`, `inspector_id`, `inspector_revisor_id`, `reporte_origen_id`; **`@ManyToMany` `bultos`** (`cuarentena_bulto`) | `EstadoCuarentena`: ACTIVA, LEVANTADA, CONVERTIDA_EN_RECALL |
| **ReporteCiudadano** (`reportes_ciudadanos`) | codigo (REP-####), gtin, serie, motivo, provincia, cajaExiste, conclusión | `paciente_id`, `investiga_id`, `unidad_trazable_id` | `EstadoAuditoria`: ABIERTO, EN_INVESTIGACION, CERRADO |
| **EventoTrazabilidad** (`eventos_trazabilidad`) | numero (Long correlativo), tipo, fechaHora (`Instant`), entidadTipo, entidadId, datosJson, actorUsuarioId, actorEmpresaId, hashAnterior, hash | — (sin FK: todo valor inmutable) | — (solo GET) |
| **RegistroBlockchain** (`registros_blockchain`) | desdeNumero, hastaNumero, hashAnclado, transactionHash, nonce, comisiones, bloque, confirmaciones, gasUsado, causaFallo | — | `EstadoAnclaje`: PENDIENTE, ENVIADO, CONFIRMADO, FALLIDO |
| **CadenaEstado** (`cadena_estado`) | fila única (nombre PRINCIPAL), ultimoNumero, ultimoHash | — | — |
| **IntentoVerificacion** (`intento_verificacion`) | tipo, gtin, serie, fecha (día UTC) — tope de SERIE_INEXISTENTE/ROBADA | — | — |

Reglas de relación (CLAUDE.md, verificadas): **nunca EAGER**, **sin `@OneToMany`**, `@ManyToMany` **solo** en `DespachoLogistico.bultos` y `Cuarentena.bultos`.

### Transiciones de estado (métodos de dominio de cada entidad; cualquier otra → 409 `TRANSICION_INVALIDA`)

- **Empresa:** PENDIENTE → HABILITADA | RECHAZADA · HABILITADA ↔ SUSPENDIDA. Habilitar/rechazar exige haber tomado (o tener asignada) la solicitud. Suspender arrastra sus circuitos APROBADO; al rehabilitar vuelven solo esos, si las tres empresas están HABILITADA.
- **InspectorAnmat:** ACTIVO ↔ BAJA. La baja devuelve a la bandeja sus solicitudes tomadas; la reactivación no.
- **EnlaceCuit:** PENDIENTE_EMPRESAS → PENDIENTE_INSPECTOR → APROBADO | RECHAZADO · PENDIENTE_EMPRESAS → RECHAZADO · APROBADO ↔ SUSPENDIDO. RECHAZADO es definitivo.
- **Lote:** PENDIENTE_LIBERACION → LIBERADO ↔ CUARENTENA → RECALL. Sin LIBERADO → RECALL directo.
- **Bulto:** ARMADO → EN_TRANSITO → EN_DEPOSITO → EN_TRANSITO → RECIBIDO · EN_TRANSITO → RECHAZADO | ROBADO · ARMADO → DESARMADO · RECHAZADO → EN_DEPOSITO | RECIBIDO (solo aceptación por dictamen tras PRECINTO_ROTO).
- **UnidadTrazable:** EN_LABORATORIO → EN_TRANSITO → EN_DEPOSITO → EN_TRANSITO → EN_STOCK → DISPENSADA · DISPENSADA → EN_STOCK (anulación ≤2 h) · EN_STOCK → DEVUELTA · EN_TRANSITO → ROBADA | RECHAZADA · RECHAZADA → EN_DEPOSITO | EN_STOCK (aceptación por dictamen).
- **DespachoLogistico:** PROGRAMADO → EN_TRANSITO → FINALIZADO · EN_TRANSITO → ROBADO · PROGRAMADO → CANCELADO. Los bultos se fijan al crearlo.
- **Cuarentena:** ACTIVA → LEVANTADA | CONVERTIDA_EN_RECALL (dictaminar exige haberla tomado).
- **ReporteCiudadano:** ABIERTO → EN_INVESTIGACION → CERRADO.
- **RegistroBlockchain:** PENDIENTE → ENVIADO → CONFIRMADO · ENVIADO → ENVIADO (reemplazo) · PENDIENTE/ENVIADO → FALLIDO. Sin setters: solo métodos de dominio.

## 5. Convenciones de capas y decisiones de estilo

| Capa | Responsabilidad |
|---|---|
| **Entity** | Estado + reglas de transición (métodos de dominio que lanzan `ReglaNegocioException`). Constructor vacío `protected` + constructor con obligatorios; `@Table`/`@Column` explícitos; `@Enumerated(EnumType.STRING)`. |
| **Repository** | Consultas (Spring Data JPA). Métodos con scope por rol (`findDelLaboratorioPorEstado`, `findVisiblesParaFarmacia`, …). |
| **Service** | Lógica de negocio y transacciones (`@Transactional` por método). Resuelve relaciones con `findById(...).orElseThrow(...)`, aplica reglas, filtra por empresa/provincia, genera eventos. |
| **Controller** | Solo DTOs, `ResponseEntity` con status explícito, `@Valid`, `@PreAuthorize`, `@Tag`/`@Operation`/`@RespuestasError`. |
| **DTO** | `*RequestDTO` (entrada, con Bean Validation) y `*ResponseDTO` (salida, con `@Schema`). Sin exponer entidades. |
| **Mapper** | Conversión entidad↔DTO **a mano**. No accede a la base (el controller/service le pasa lo calculado, p. ej. el mapa de bloqueo R10). |

**Decisiones de estilo (verificadas en código y CLAUDE.md):**
- **Sin PUT ni DELETE de negocio.** Los estados cambian solo por endpoints de acción `POST /api/<recurso>/{id}/<accion>` (aprobar, liberar, recibir, anular…), para que cada cambio sea explícito, auditable y genere su evento. `EventoTrazabilidad` y `RegistroBlockchain` solo tienen GET.
- **Mappers a mano, sin Lombok ni MapStruct ni records:** el código es explícito y legible, sin generación oculta; getters/setters escritos.
- **Relaciones LAZY siempre:** evita traer medio grafo por accidente; el service decide qué cargar. Para los listados, `hibernate.default_batch_fetch_size=100` evita N+1 al leer los nombres de las relaciones (una consulta por grupo, no por fila).
- **Inyección por constructor** con campos `private final` y `@Autowired` en el constructor.
- **Recurso de otra empresa → 404, no 403** (no revela que existe).

## 6. Tabla completa de endpoints (97 operaciones, 21 controllers)

Roles: **SEDE**=SEDE_CENTRAL · **INSP**=INSPECTOR · **LAB**=LABORATORIO · **DIST**=DISTRIBUIDOR · **FARM**=FARMACIA · **PAC**=PACIENTE · **Púb**=público (sin token). Éxito: **201** en las altas, **202** en `/anclar`, **200** el resto. "Errores" = lista declarada en `@RespuestasError` (`ErrorResponseDTO` en todos).

### Autenticación y registro público (`auth`, `RegistroController`)
| M | Ruta | operationId | Roles | Qué hace | Errores |
|---|---|---|---|---|---|
| POST | /api/auth/login | iniciarSesion | Púb | Devuelve JWT + datos de sesión | 400,401 |
| POST | /api/registro/empresas | registrarEmpresa | Púb | Alta de empresa + admin + PDF (multipart) | 400,409 |
| POST | /api/registro/pacientes | registrarPaciente | Púb | Alta de cuenta de paciente | 400,409 |

### Empresas (`/api/empresas`)
| M | Ruta | operationId | Roles | Qué hace | Errores |
|---|---|---|---|---|---|
| GET | / | listarEmpresas | SEDE,INSP,LAB,DIST,FARM | Lista (scope por rol; `?estado=`) | 400,401,403 |
| GET | /sin-inspector | listarEmpresasSinInspector | SEDE | PENDIENTE de provincias sin inspector | 400,401,403 |
| GET | /bandeja | bandejaEmpresas | INSP | Bandeja del inspector | 400,401,403 |
| GET | /por-cuit | buscarEmpresaPorCuit | LAB | Busca empresa por CUIT (para circuitos) | 400,401,403,404 |
| GET | /{id} | obtenerEmpresa | SEDE,INSP,LAB,DIST,FARM | Detalle | 400,401,403,404 |
| GET | /{id}/documento | descargarDocumentoEmpresa | SEDE,INSP | Descarga el PDF de habilitación | 400,401,403,404 |
| POST | /{id}/tomar | tomarEmpresa | INSP | El inspector toma la solicitud | 400,401,403,404,409 |
| POST | /{id}/asignar | asignarEmpresa | SEDE | La Sede asigna la solicitud a un inspector | 400,401,403,404,409 |
| POST | /{id}/habilitar | habilitarEmpresa | INSP | Habilita la empresa | 400,401,403,404,409 |
| POST | /{id}/rechazar | rechazarEmpresa | INSP | Rechaza la solicitud | 400,401,403,404,409 |
| POST | /{id}/suspender | suspenderEmpresa | SEDE | Suspende (arrastra circuitos) | 400,401,403,404,409 |
| POST | /{id}/rehabilitar | rehabilitarEmpresa | SEDE | Rehabilita | 400,401,403,404,409 |

### Inspectores ANMAT (`/api/inspectores-anmat`)
| M | Ruta | operationId | Roles | Qué hace | Errores |
|---|---|---|---|---|---|
| GET | / | listarInspectores | SEDE,INSP | Lista (`?estado=`) | 400,401,403 |
| GET | /{id} | obtenerInspector | SEDE,INSP | Detalle | 400,401,403,404 |
| POST | / | crearInspector | SEDE | Alta de inspector (R1) | 400,401,403,409 |
| POST | /{id}/baja | darDeBajaInspector | SEDE | Baja (devuelve sus tomadas) | 400,401,403,404,409 |
| POST | /{id}/reactivar | reactivarInspector | SEDE | Reactiva | 400,401,403,404,409 |

### Usuarios (`/api/usuarios`)
| M | Ruta | operationId | Roles | Qué hace | Errores |
|---|---|---|---|---|---|
| GET | / | listarUsuarios | SEDE,LAB,DIST,FARM | Lista empleados de la empresa | 400,401,403 |
| GET | /{id} | obtenerUsuario | autenticado | Detalle | 400,401,404 |
| POST | / | crearUsuario | SEDE,LAB,DIST,FARM | Crea empleado/paciente según rol | 400,401,403,409 |

### Circuitos = EnlaceCuit (`/api/circuitos`)
| M | Ruta | operationId | Roles | Qué hace | Errores |
|---|---|---|---|---|---|
| GET | / | listarCircuitos | SEDE,INSP,LAB,DIST,FARM | Lista (scope por rol; `?estado=`) | 400,401,403 |
| GET | /pendientes-aceptacion | listarCircuitosPendientesDeAceptacion | DIST,FARM | Los que esperan mi aceptación | 400,401,403 |
| GET | /sin-inspector | listarCircuitosSinInspector | SEDE | PENDIENTE_INSPECTOR sin inspector en la provincia | 400,401,403 |
| GET | /bandeja | bandejaCircuitos | INSP | Bandeja del inspector | 400,401,403 |
| GET | /{id} | obtenerCircuito | SEDE,INSP,LAB,DIST,FARM | Detalle | 400,401,403,404 |
| POST | / | proponerCircuito | LAB | El DT propone el circuito por CUIT (R5) | 400,401,403,409 |
| POST | /{id}/aceptar | aceptarCircuito | DIST,FARM | Acepta su parte | 400,401,403,404,409 |
| POST | /{id}/rechazar-empresa | rechazarCircuitoPorEmpresa | DIST,FARM | Rechaza su parte | 400,401,403,404,409 |
| POST | /{id}/tomar | tomarCircuito | INSP | El inspector lo toma | 400,401,403,404,409 |
| POST | /{id}/asignar | asignarCircuito | SEDE | La Sede lo asigna | 400,401,403,404,409 |
| POST | /{id}/aprobar | aprobarCircuito | INSP | Aprueba (R5) | 400,401,403,404,409 |
| POST | /{id}/rechazar | rechazarCircuito | INSP | Rechaza (definitivo) | 400,401,403,404,409 |
| POST | /{id}/suspender | suspenderCircuito | INSP,SEDE | Suspende | 400,401,403,404,409 |
| POST | /{id}/rehabilitar | rehabilitarCircuito | INSP,SEDE | Rehabilita | 400,401,403,404,409 |

### Medicamentos (`/api/medicamentos`)
| M | Ruta | operationId | Roles | Qué hace | Errores |
|---|---|---|---|---|---|
| GET | / | listarMedicamentos | SEDE,INSP,LAB,DIST,FARM | Lista el catálogo (**ver §16: hoy sin filtro por empresa**) | 400,401,403 |
| GET | /{id} | obtenerMedicamento | SEDE,INSP,LAB,DIST,FARM | Detalle (sin chequeo de empresa) | 400,401,403,404 |
| POST | / | registrarMedicamento | LAB | Registra un medicamento del laboratorio | 400,401,403,409 |

### Lotes (`/api/lotes`)
| M | Ruta | operationId | Roles | Qué hace | Errores |
|---|---|---|---|---|---|
| GET | / | listarLotes | SEDE,INSP,LAB,DIST,FARM | Lista (scope por rol; `?estado=`, `?codigo=`) | 400,401,403 |
| GET | /bandeja-liberacion | bandejaLiberacionLotes | INSP | Biológicos PENDIENTE_LIBERACION de su provincia | 400,401,403 |
| GET | /{id} | obtenerLote | SEDE,INSP,LAB,DIST,FARM | Detalle (con indicador R10) | 400,401,403,404 |
| GET | /{id}/unidades | listarCajasDelLote | SEDE,INSP,LAB | Cajas del lote | 400,401,403,404 |
| POST | / | registrarLote | LAB | Registra el lote con todas sus series (R3) | 400,401,403,404,409 |
| POST | /{id}/liberar | liberarLote | LAB,INSP | Libera (R4: DT común / inspector biológico) | 400,401,403,404,409 |

### Unidades trazables = cajas (`/api/unidades-trazables`)
| M | Ruta | operationId | Roles | Qué hace | Errores |
|---|---|---|---|---|---|
| GET | / | listarCajas | SEDE,INSP,LAB,DIST,FARM | Lista (scope por rol; `?estado=`, `?serie=`) | 400,401,403 |
| GET | /{id} | obtenerCaja | SEDE,INSP,LAB,DIST,FARM | Detalle (con indicador R10) | 400,401,403,404 |
| POST | /devolucion | devolverCaja | FARM | Devolución de una caja EN_STOCK | 400,401,403,404,409 |

### Bultos (`/api/bultos`)
| M | Ruta | operationId | Roles | Qué hace | Errores |
|---|---|---|---|---|---|
| GET | / | listarBultos | SEDE,INSP,LAB,DIST,FARM | Lista (scope por rol; `?estado=`, `?codigo=`) | 400,401,403 |
| GET | /{id} | obtenerBulto | SEDE,INSP,LAB,DIST,FARM | Detalle (con indicador R10) | 400,401,403,404 |
| POST | / | armarBulto | LAB | Arma un bulto cerrado (R6) | 400,401,403,404,409 |
| POST | /{id}/desarmar | desarmarBulto | LAB | Desarma un bulto ARMADO sin viaje | 400,401,403,404,409 |

### Viajes = DespachoLogistico (`/api/viajes`)
| M | Ruta | operationId | Roles | Qué hace | Errores |
|---|---|---|---|---|---|
| GET | / | listarViajes | SEDE,INSP,LAB,DIST,FARM | Lista (scope por rol; `?estado=`) | 400,401,403 |
| GET | /{id} | obtenerViaje | SEDE,INSP,LAB,DIST,FARM | Detalle | 400,401,403,404 |
| GET | /{id}/telemetria-temperatura | listarTemperaturasDelViaje | SEDE,INSP,LAB,DIST,FARM | Temperaturas del viaje (páginas de 100) | 400,401,403,404 |
| POST | / | crearViaje | LAB,DIST | Crea el viaje con sus bultos (R7) | 400,401,403,404,409 |
| POST | /{id}/salida | registrarSalidaViaje | LAB,DIST | Registra la salida (→ EN_TRANSITO) | 400,401,403,404,409 |
| POST | /{id}/cancelar | cancelarViaje | LAB,DIST | Cancela (PROGRAMADO) | 400,401,403,404,409 |
| POST | /{id}/robo | reportarRoboViaje | LAB,DIST | Robo/extravío (R14) | 400,401,403,404,409 |

### Telemetría de temperatura (`/api/telemetria-temperatura`) y GPS (`/api/telemetria-gps`)
| M | Ruta | operationId | Roles | Qué hace | Errores |
|---|---|---|---|---|---|
| GET | / | listarLecturasTemperatura | SEDE,INSP,LAB,DIST,FARM | Lista lecturas | 400,401,403 |
| GET | /{id} | obtenerLecturaTemperatura | SEDE,INSP,LAB,DIST,FARM | Detalle | 400,401,403,404 |
| POST | / | registrarLecturaTemperatura | LAB,DIST | Reporta lectura (R9) | 400,401,403,404,409 |
| GET | / | listarLecturasGps | SEDE,INSP,LAB,DIST | Lista lecturas GPS | 400,401,403 |
| GET | /{id} | obtenerLecturaGps | SEDE,INSP,LAB,DIST | Detalle | 400,401,403,404 |
| POST | / | registrarLecturaGps | LAB,DIST | Reporta lectura GPS | 400,401,403,404,409 |

### Recepciones (`/api/recepciones`)
| M | Ruta | operationId | Roles | Qué hace | Errores |
|---|---|---|---|---|---|
| GET | / | listarRecepciones | SEDE,INSP,LAB,DIST,FARM | Lista | 400,401,403 |
| GET | /{id} | obtenerRecepcion | SEDE,INSP,LAB,DIST,FARM | Detalle | 400,401,403,404 |
| POST | / | recibirBulto | DIST,FARM | Recibe escaneando el código (R8) | 400,401,403,404,409 |

### Dispensaciones (`/api/dispensaciones`)
| M | Ruta | operationId | Roles | Qué hace | Errores |
|---|---|---|---|---|---|
| GET | / | listarDispensaciones | SEDE,INSP,FARM | Lista | 400,401,403 |
| GET | /{id} | obtenerDispensacion | SEDE,INSP,FARM | Detalle | 400,401,403,404 |
| POST | / | dispensarCaja | FARM | Dispensa una caja (R11, R13) | 400,401,403,404,409 |
| POST | /{id}/anular | anularDispensacion | FARM | Anula ≤2 h (R11) | 400,401,403,404,409 |

### Cuarentenas (`/api/cuarentenas`)
| M | Ruta | operationId | Roles | Qué hace | Errores |
|---|---|---|---|---|---|
| GET | / | listarCuarentenas | SEDE,INSP,LAB,DIST,FARM | Lista (`?estado=`) | 400,401,403 |
| GET | /{id} | obtenerCuarentena | SEDE,INSP,LAB,DIST,FARM | Detalle | 400,401,403,404 |
| GET | /bandeja | bandejaCuarentenas | INSP | Bandeja del inspector | 400,401,403 |
| POST | / | abrirCuarentena | INSP | Cuarentena manual de LOTE (R12) | 400,401,403,404,409 |
| POST | /{id}/tomar | tomarCuarentena | INSP | La toma para dictaminar | 400,401,403,404,409 |
| POST | /{id}/levantar | levantarCuarentena | INSP | Levanta (R8/R9/R14 lo impiden) | 400,401,403,404,409 |
| POST | /{id}/recall | convertirCuarentenaEnRecall | INSP | La convierte en recall (R12) | 400,401,403,404,409 |

### Reportes ciudadanos (`/api/reportes-ciudadanos`)
| M | Ruta | operationId | Roles | Qué hace | Errores |
|---|---|---|---|---|---|
| GET | / | listarReportes | SEDE,INSP,PAC | Lista (`?estado=`; el paciente ve solo los suyos) | 400,401,403 |
| GET | /bandeja | bandejaReportes | INSP | Bandeja del inspector | 400,401,403 |
| GET | /{id} | obtenerReporte | SEDE,INSP,PAC | Detalle | 400,401,403,404 |
| POST | / | reportarCaja | PAC | El paciente reporta una caja | 400,401,403,409 |
| POST | /{id}/tomar | tomarReporte | INSP | Lo toma (ABIERTO→EN_INVESTIGACION) | 400,401,403,404,409 |
| POST | /{id}/cerrar | cerrarReporte | INSP | Lo cierra con conclusión (R12) | 400,401,403,404,409 |

### Verificación pública (`/api/verificacion`)
| M | Ruta | operationId | Roles | Qué hace | Errores |
|---|---|---|---|---|---|
| GET | / | verificarCaja | Púb | Verifica una caja por GTIN+serie | 400 |

### Eventos de trazabilidad (`/api/eventos-trazabilidad`)
| M | Ruta | operationId | Roles | Qué hace | Errores |
|---|---|---|---|---|---|
| GET | /verificacion | verificarCadena | SEDE,INSP | Recalcula la cadena y la compara con la blockchain | 401,403 |
| GET | / | listarEventos | SEDE,INSP | Lista eventos (`?entidadId=`) | 400,401,403 |
| GET | /recorrido | buscarRecorridoPorCodigo | SEDE,INSP,LAB,DIST,FARM | Recorrido por `?lote=`/`?bulto=`/`?gtin=&serie=` | 400,401,403,404 |
| GET | /{id} | obtenerEvento | SEDE,INSP | Detalle de un evento | 400,401,403,404 |

### Registros blockchain (`/api/registros-blockchain`)
| M | Ruta | operationId | Roles | Qué hace | Errores |
|---|---|---|---|---|---|
| GET | / | listarAnclajes | SEDE,INSP | Lista anclajes | 400,401,403 |
| GET | /estado | obtenerEstadoAnclaje | SEDE,INSP | Estado del anclaje (saldo, gas, frenos) | 401,403 |
| POST | /anclar | anclarAhora | SEDE | "Anclar ya" (202) | 401,403,409,503 |
| GET | /{id} | obtenerAnclaje | SEDE,INSP | Detalle de un anclaje | 400,401,403,404 |

### Tablero (`/api/tablero`)
| M | Ruta | operationId | Roles | Qué hace | Errores |
|---|---|---|---|---|---|
| GET | /resumen | obtenerResumenTablero | SEDE | Totales del sistema | 401,403 |
| GET | /bandeja-inspector | obtenerBandejaInspector | INSP | Pendientes de cada bandeja | 401,403 |

## 7. Reglas de negocio R1–R15 (texto y dónde se aplica)

Fallo → `ReglaNegocioException(regla, mensaje)` → **409** con el código en `regla`, **salvo R12 y R13** (ver abajo).

| Regla | Qué exige | Dónde se aplica (clase · método) |
|---|---|---|
| **R1** | Solo la Sede crea/da de baja inspectores. | `UsuarioService` (impide crear SEDE_CENTRAL/INSPECTOR por /usuarios); alta real en `InspectorAnmatService` |
| **R2** | Solo operan empresas HABILITADA; la habilita un inspector de su provincia (sin inspectores, la Sede asigna). | `utils/seguridad/VerificadorEmpresa.exigirHabilitada`; `EmpresaService` (asignar); `UsuarioService` |
| **R3** | GTIN GS1 válido; serie alfanumérica ≤20, que no empiece con 779, única por GTIN; lote atómico (máx 10.000); inválida/repetida/existente → nada se crea + INTENTO_SERIE_INVALIDA (REQUIRES_NEW). | `LoteService.registrar`/`validarSeries`; choque de carrera `GlobalExceptionHandler` (`ux_unidad_gtin_serie`) |
| **R4** | LIBERADO solo por DT del laboratorio (común) o inspector de la provincia del lab (biológico). | `LoteService.liberar` |
| **R5** | Circuito: 3 empresas HABILITADA del tipo correcto; propone el DT por CUIT, aceptan DIST y FARM, aprueba un inspector de la provincia de la farmacia; un solo VIGENTE por par lab–farmacia. | `EnlaceCuit` (dominio) + `EnlaceCuitService` (proponer/aceptar/aprobar/asignar) |
| **R6** | Bulto con cajas de un solo lote LIBERADO y circuito APROBADO; despachado no cambia. | `BultoService.armar`; `UnidadTrazable` (dominio) |
| **R7** | Tramo 1 lab→dist; tramo 2 dist→farmacias; un viaje, varias paradas; finaliza al recibir/rechazar todos. | `DespachoLogisticoService.crear`; `Bulto` (dominio) |
| **R8** | Recepción escaneando; servidor decide conformidad (precinto, cantidad, temperatura); rechazo → cuarentena BULTO; levantar solo si fue solo PRECINTO_ROTO. | `RecepcionService`; `CuarentenaService` (levantar) |
| **R9** | Lectura fuera de rango → RUPTURA_FRIO + cuarentena DESPACHO sobre los bultos afectados; tras ruptura solo recall. | `TelemetriaTemperaturaService`; `Cuarentena.levantar` (bloquea) |
| **R10** | Caja bloqueada si su lote/viaje/bulto tiene medida activa o recall, o el lote venció. Se calcula, no se guarda. | `cuarentena/EvaluadorBloqueo` (único lugar); lo usan armar bulto, crear viaje, salida, recepción, dispensación, verificación pública |
| **R11** | Una dispensación no anulada por caja; misma farmacia anula ≤2 h → EN_STOCK. | `DispensacionService`; `Dispensacion.anular` |
| **R12** | Solo un inspector dictamina, de la provincia de la medida/reporte (D1) y tras tomarla; cuarentena manual solo de LOTE. | `CuarentenaService`/`ReporteCiudadanoService` — **no lanza código "R12"**: provincia ajena → **404**; dictaminar sin tomar → `TRANSICION_INVALIDA` |
| **R13** | DNI opcional guardado solo enmascarado; ningún dato de paciente/dispensación en eventos, blockchain ni verificación pública. | `utils/validacion/DniUtil.enmascarar` (en `DispensacionService`) + lista blanca de `DatosEventos` — **no es un 409**, es una garantía de datos |
| **R14** | Robo: viaje/bultos/cajas ROBADO/ROBADA + cuarentena DESPACHO que no se levanta; devuelta no se redispensa. | `DespachoLogisticoService` (robo); `Cuarentena.levantar` (bloquea); `DispensacionService` |
| **R15** | Cada acción encadena un evento; cada 5 min se ancla el último hash; no se ancla sobre una cadena que no coincide con el contrato. | `RegistradorEventos`; `ProcesoAnclaje` (lanza "R15" en "anclar ya") |

## 8. Seguridad

- **Login** (`AuthController`/`AuthService`/`JwtService`): `POST /api/auth/login` (público) con email+password; password con **BCrypt** (`PasswordConfig`). Devuelve `LoginResponseDTO`: `token`, `rol`, `empresaId`, `esAdminEmpresa`, `esDirectorTecnico`, `provincia` (solo inspector) y `expiraEn` (`Instant` UTC con `Z`), para que el frontend no decodifique el token.
- **JWT** (`JwtService`): claims `sub`=usuarioId, `email`, `rol`, `empresaId` (si tiene), `provincia` (solo si es inspector). **Dura 8 horas** (`jwt.expiracion-horas=8`). Firma HMAC con `JWT_SECRET`.
- **Cuenta desactivada:** en cada request con token, `JwtAuthenticationFilter` confirma contra la base que la cuenta siga activa (`UsuarioRepository.existsByIdAndActivoTrue`); un token vigente de una cuenta dada de baja/desactivada → **401**.
- **Rutas públicas** (`SecurityConfig`): `POST /api/auth/login`; `POST /api/registro/empresas` y `/api/registro/pacientes`; `GET /api/verificacion` y `/api/verificacion/**`; y `/swagger-ui/**`, `/swagger-ui.html`, `/api-docs/**`. El resto exige token.
- **@PreAuthorize + validación en el Service:** `@PreAuthorize` en el controller filtra por **rol** (403 si no corresponde). El **Service** vuelve a verificar contra la base lo que el token no garantiza: empresa HABILITADA (R2), marcas DT/admin, provincia del inspector (D1), pertenencia del recurso a la empresa (si no, **404**, no 403). Las marcas del JWT son informativas; la decisión real es contra la base en cada acción.
- **`GET /api/usuarios/{id}` (`isAuthenticated()`) está aislado en el Service, no por rol:** `UsuarioService.getById` devuelve el usuario solo si quien pregunta es SEDE_CENTRAL, es el mismo usuario, o es de la **misma empresa**; en cualquier otro caso **404** (ver §8-bis en la tabla de verificación abajo). `UsuarioResponseDTO` **nunca incluye `passwordHash`** (lo garantizan el DTO y `UsuarioMapper`), y como el listado y el detalle usan el mismo `toResponseDTO`, la protección cubre ambos.
- **CORS:** `SecurityConfig` **no configura CORS** (no hay `.cors(...)`, ni `CorsConfiguration`, ni `WebMvcConfigurer`); `csrf` deshabilitado y sesión `STATELESS`. Confirmado: ningún origen cruzado habilitado en el backend (el frontend usa un proxy de desarrollo).
- 401/403 se devuelven como `ErrorResponseDTO` JSON (`RestAuthenticationEntryPoint`/`RestAccessDeniedHandler`).

**§8-bis — qué devuelve `GET /api/usuarios/{id}` por caso** (`UsuarioService.getById`):

| Quién pregunta | Por el id de… | Resultado |
|---|---|---|
| FARMACIA | un usuario de LABORATORIO (otra empresa) | **404** |
| PACIENTE (sin empresa) | un usuario de empresa | **404** |
| Cualquiera | un INSPECTOR o la SEDE (sin empresa) | **404** (salvo que sea SEDE quien pregunta, o uno mismo) |
| SEDE_CENTRAL | cualquiera | 200 |
| Usuario | su propio id | 200 |
| Usuario de empresa | otro de su misma empresa | 200 |

(Matiz: el Javadoc dice "el administrador de la misma empresa", pero el código habilita a **cualquier** usuario de la misma empresa, no solo al admin. No es una fuga entre empresas, pero conviene alinear Javadoc y código.)

## 9. Manejo de errores (`utils/ErrorResponseDTO`, `utils/GlobalExceptionHandler`)

**Cuerpo de todo error** (`ErrorResponseDTO`): `status` (int), `message` (String), `regla` (String, **solo en 409**; null en el resto), `errors` (lista de `{field, message}`, para validaciones 400).

| HTTP | Cuándo | Excepciones que lo producen |
|---|---|---|
| **400** | Datos mal formados: body, path, query, paginación, multipart, PDF, orden inválido | `BindException`, `HandlerMethodValidationException`, `ConstraintViolationException`, `MissingServletRequestParameter`, `ConsultaInvalidaException`, `DocumentoInvalidoException`, `MaxUploadSizeExceeded`, `MissingServletRequestPart`, `HttpMessageNotReadable`, `MethodArgumentTypeMismatch`, `PropertyReferenceException`, `InvalidDataAccessApiUsage` |
| **401** | Credenciales inválidas / sin token | `CredencialesInvalidasException` (+ entry point) |
| **403** | Rol sin permiso | `AccessDeniedException` |
| **404** | Recurso inexistente o de otra empresa; ruta inexistente | `ResourceNotFoundException`, `NoResourceFoundException` |
| **405** | Método no soportado | `HttpRequestMethodNotSupportedException` |
| **409** | Regla de negocio / violación de unicidad / edición concurrente | `ReglaNegocioException`, `DataIntegrityViolationException` (traducida por nombre de restricción), `OptimisticLockingFailureException` (→ CONFLICTO_VERSION) |
| **503** | Sepolia no responde en un endpoint de anclaje | `ErrorBlockchainException` (mensaje fijo) |
| **500** | No previsto | handler genérico |

**Códigos de `regla` en un 409:** las reglas **R1–R15** (menos R12/R13, que no son 409) y **14 códigos no-R distintos**, según de dónde salen (verificado con `grep`):

- **6 solo desde los services** (`new ReglaNegocioException(...)`, nunca desde el handler): `ANCLAJE_DESHABILITADO`, `ANCLAJE_EN_CURSO`, `ROL_NO_PERMITIDO`, `SALDO_INSUFICIENTE`, `SIN_EVENTOS_NUEVOS`, `TRANSICION_INVALIDA`.
- **5 por doble vía** (chequeo previo en el service *y* red de la base: el handler traduce la violación de la restricción única): `EMPRESA_DUPLICADA`, `INSPECTOR_DUPLICADO`, `LOTE_DUPLICADO`, `MEDICAMENTO_DUPLICADO`, `REGISTRO_NO_COMPLETADO`.
- **3 solo desde `GlobalExceptionHandler`** (los services nunca los lanzan): `DATO_DUPLICADO`, `RESTRICCION_DE_DATOS` (tabla de traducciones) y `CONFLICTO_VERSION` (handler de `OptimisticLockingFailureException`).

Aclaración sobre el "11" de una versión previa de este informe: **son 11 códigos no-R *distintos* lanzados directamente con `new ReglaNegocioException(...)`** (los 6 "solo services" + los 5 "doble vía"), repartidos en **39 lugares** del código (`TRANSICION_INVALIDA` solo aparece en 24; `ROL_NO_PERMITIDO`, `EMPRESA_DUPLICADA`, `INSPECTOR_DUPLICADO`, `LOTE_DUPLICADO`, `REGISTRO_NO_COMPLETADO` en 2 cada uno; el resto en 1). Eran códigos distintos, no lugares. Sumando los 3 que solo produce el handler, el total de códigos no-R posibles en un 409 es **14**.

## 10. Cadena de hashes (`modules/trazabilidad`)

- **Una sola cadena** para todo el sistema. `hash_n = SHA-256(UTF-8(json_canónico_n))`, con `hashAnterior` = `hash_{n-1}` (el primero usa `GENESIS`). Hash en 64 hex minúscula, sin `0x`.
- **Numeración y carreras** (`RegistradorEventos`, `propagation = MANDATORY`): dentro de la transacción de negocio, bloquea la fila única `cadena_estado` con `SELECT … FOR UPDATE`, toma `numero = último + 1`, fija `fechaHora` después del bloqueo, guarda el evento y avanza el estado. Rollback del negocio = rollback del evento. Así no hay huecos ni dos eventos con el mismo número bajo concurrencia (lo cubre `CadenaConcurrenteIT`).
- **JSON canónico** (`JsonCanonico`, sin Jackson): claves ordenadas, sin espacios, nulls omitidos; `Instant` en UTC microsegundos `yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'`; `LocalDate` `yyyy-MM-dd`; `BigDecimal` `stripTrailingZeros().toPlainString()`; UUID minúscula; enums por nombre. Campos hasheados: actorEmpresaId, actorUsuarioId, datos, entidadId, entidadTipo, fechaHora, hashAnterior, numero, tipo. El `datos_json` se guarda **congelado** como texto y la verificación lo reinserta crudo (`JsonCrudo`), por eso eventos viejos siguen verificando aunque cambie el formato.
- **Datos por evento:** lista blanca por tipo en `DatosEventos` (nunca la entidad entera; prohibido DNI, obra social, afiliado, receta, patente, chofer, textos libres).

**47 `TipoEvento`** y qué guarda cada uno (resumen; detalle en `DatosEventos`):

| Grupo | Tipos | Datos (lista blanca) |
|---|---|---|
| Inspectores | ALTA_INSPECTOR, BAJA_INSPECTOR, REACTIVACION_INSPECTOR | legajo, provincia (sin DNI) |
| Habilitación | SOLICITUD_HABILITACION, SOLICITUD_TOMADA, SOLICITUD_ASIGNADA, HABILITACION_APROBADA, HABILITACION_RECHAZADA, EMPRESA_SUSPENDIDA, EMPRESA_REHABILITADA | cuit, gln, provincia, tipo, documentoHash |
| Circuitos | CIRCUITO_PROPUESTO, _ACEPTADO, _RECHAZADO, _TOMADO, _ASIGNADO, _APROBADO, _SUSPENDIDO, _REHABILITADO | código, CUIT de las 3 empresas |
| Medicamento/lote | MEDICAMENTO_REGISTRADO, LOTE_REGISTRADO (cantidadSeries + seriesHash), LOTE_LIBERADO, INTENTO_SERIE_INVALIDA | gtin, código, rangos; **nunca la lista de series** |
| Bultos/viajes | BULTO_ARMADO, BULTO_DESARMADO, VIAJE_CREADO (fechaEstimadaEntrega), VIAJE_SALIDA, VIAJE_CANCELADO, RUPTURA_FRIO (fechaHoraLectura), VIAJE_FINALIZADO (resumen de temperatura), ROBO_EXTRAVIO | códigos, tramo, paradas |
| Recepción | BULTO_RECIBIDO, BULTO_RECHAZADO, BULTO_INEXISTENTE, BULTO_DUPLICADO | código, motivos, cantidad |
| Dispensación | DISPENSACION, ANULACION_DISPENSA, DEVOLUCION, INTENTO_DUPLICADO, SERIE_INEXISTENTE, SERIE_ROBADA | gtin, serie (**sin datos del paciente**) |
| Medidas | CUARENTENA, CUARENTENA_TOMADA, CUARENTENA_LEVANTADA, RECALL | alcance, motivo, hash de fundamento/conclusión |
| Reportes | REPORTE_CIUDADANO, REPORTE_TOMADO, REPORTE_CERRADO | código, gtin, serie, motivo, provincia, cajaExiste (**sin paciente ni descripción**) |

- **Verificación:** `GET /api/eventos-trazabilidad/verificacion` (`VerificadorCadena`) recalcula toda la cadena en bloques de 500 y la compara con `cadena_estado`; además `VerificadorAnclajes` la compara con lo anclado en el contrato (VERIFICADA / ALTERADA / NO_CONSULTADA / NO_DISPONIBLE).

## 11. Anclaje en Sepolia (`modules/registroblockchain`, `contracts/`)

- **Contrato** `MediChainAnchor.sol` (Solidity 0.8.37, optimizer 200): solo el dueño (quien lo desplegó, `immutable`) puede `anclar(bytes32 hash, uint64 hastaNumero)`; `hastaNumero` estrictamente creciente (nada se reescribe); guarda el hash por número y la lista de números anclados. Sin borrar ni reiniciar. La ABI se arma a mano (`ContratoAnchor`, sin clases generadas).
- **Scheduler** (`TareaAnclaje`, solo con `ANCLAJE_HABILITADO=true`): dos tareas con `fixedDelay` — **cada 5 min** (`PT5M`, demora inicial `PT1M`) ancla el hash del último evento si hubo nuevos; **cada 15 s** (`PT15S`) sigue el anclaje en curso. El negocio nunca espera a la blockchain; las llamadas a la red nunca ocurren dentro de una transacción de base.
- **Confirmaciones:** `medichain.anclaje.confirmaciones=3` → 3 bloques encima = CONFIRMADO. Reintentos 5 (`max-intentos`), reemplazo si no se incluye en `PT3M`. Gas = `eth_estimateGas` + 30 % dentro de [100.000; 500.000]; si no entra, FALLIDO sin enviar. Tope de comisión 20 gwei.
- **"Anclar ya"** (`POST /api/registros-blockchain/anclar`, solo SEDE): ancla sin respetar los frenos de la tarea automática; 202 con el anclaje ENVIADO; 409 `ANCLAJE_DESHABILITADO`/`ANCLAJE_EN_CURSO`/`SIN_EVENTOS_NUEVOS`/`R15`; 503 si Sepolia no responde; exige saldo para un anclaje (`SALDO_INSUFICIENTE`).
- **Por qué un reset obliga a un contrato nuevo (y el incidente del 17/10):** el hash de cada evento incluye su `fechaHora` (el instante de creación), así que una base recreada produce hashes distintos para todos los eventos. Para el contrato viejo, una cadena que vuelve a empezar en #1 es como "eventos alterados/borrados". Hay **dos causas** que explican por qué, tras un reset con el anclaje habilitado, pudieron salir transacciones que revirtieron **gastando gas** (una transacción que revierte igual consume el gas usado hasta el `revert`):

  1. **La condición del contrato (`contracts/MediChainAnchor.sol:52-53`):** `anclar(...)` exige `hastaNumero` **estrictamente creciente** — `if (hastaNumero <= ultimo) revert NumeroNoCreciente(hastaNumero, ultimo);`. Tras un reset, la cadena nueva vuelve a números bajos, que son `<=` al último ya anclado por el contrato viejo, así que **el contrato revierte** cada intento. Nada se puede reescribir ni repetir: es a propósito.

  2. **El freno del lado de la app:** hoy el código tiene tres defensas que deberían impedir *firmar* en ese caso — al arrancar `VerificacionInicialAnclaje` (si el contrato ancló más eventos que la base, la app **no arranca**; si el último anclaje no coincide con el evento local, arranca pero con ERROR y el anclaje **queda detenido**); por cada tanda `ProcesoAnclaje.controlarUltimoAnclaje()` lanza **R15** si el evento del contrato no existe o su hash no coincide con el local; y antes de firmar, `intentarEnvio` hace `simularAnclaje` (`eth_call`) + `estimarGasAnclaje`, y si revertiría marca **FALLIDO REVERT sin gastar**. Si esas defensas no están activas o se saltean (p. ej. el RPC no responde al arrancar y la app "arranca con aviso"), o si la versión que corrió el 17/10 todavía **enviaba con un límite de gas fijo** en vez de simular/estimar (CLAUDE.md: "así fallaron 6 anclajes con un límite fijo de 300.000"; post-Glamsterdam el primer anclaje de un contrato pide ~353.000 de gas), las transacciones se firman y se mandan igual, y recién revierten en la red (causa 1), gastando gas. Por eso ahora el gas **nunca es fijo** (`PoliticaGas`: estimación + 30 % dentro de [100.000; 500.000], y si no entra NO se envía) y existe el control R15 por tanda. `reset-demo.sh` avisa y `nuevo-contrato.sh` deja un contrato nuevo (ver §18: el detalle exacto del incidente del 17/10 no se puede confirmar solo con el código).

## 12. Base de datos (`src/main/resources/db/migration/`, `application.properties`)

- **Solo Flyway crea/cambia el esquema.** Hibernate solo **valida** al arrancar (`spring.jpa.hibernate.ddl-auto=validate`): nunca crea ni modifica tablas. `baseline-on-migrate=false`, `validate-on-migrate=true`, `clean-disabled=true`.

| Migración | Qué hace |
|---|---|
| **V1** `esquema_inicial` | 21 tablas (19 entidades + `despacho_bulto` y `cuarentena_bulto`), secuencias de códigos (`circuito/bulto/viaje/reporte_codigo_seq`), índices únicos parciales (`ux_circuito_par_vigente`, `ux_anclaje_en_curso`, `ux_unidad_gtin_serie`, `ux_lote_laboratorio_codigo`) y la fila inicial de `cadena_estado` (0, GENESIS) |
| **V2** `fechas_auditoria_utc` | Las 2 fechas de auditoría de `BaseEntity` en las 19 tablas: `timestamp` → `timestamptz` (UTC) |
| **V3** `indices_claves_foraneas` | 29 índices sobre columnas FK por las que la app busca/hace JOIN (de 45 FK); cada uno comenta qué consulta lo usa |
| **V4** `fechas_negocio_utc` | Las 25 fechas de negocio: `timestamp` → `timestamptz` (UTC). No toca `lotes.fecha_fabricacion/_vencimiento` (`date`) ni `eventos_trazabilidad` |
| **V5** `indice_eventos_por_entidad` | `ix_eventos_trazabilidad_entidad_id_numero` para la trazabilidad por código y el filtro `?entidadId=` (entidad_id no es FK) |

- **Nombres de restricciones:** `pk_<tabla>`, `uk_<tabla>_<columna>`, `ck_<tabla>_<columna>`, `fk_<tabla>_<columna>`, `ix_<tabla>_<columna>`; los únicos compuestos/parciales conservan su nombre `ux_` porque los usa `RestriccionUnica` para traducir choques a su código 409.
- Una migración ya en Git **no se edita** (Flyway compara checksums). `MigracionesIT` verifica nombres/versiones, aplicar limpio, no-adopción sin historial, validación de Hibernate y la **deriva** entidades↔migraciones.

## 13. Tests

Números de una **corrida real** de `./mvnw verify` (2026-10-24, commit `6f0a7fe`, BUILD SUCCESS):

| Fase | Plugin | Tests run | Failures | Errors | Skipped |
|---|---|---|---|---|---|
| Unitarios | Surefire | **463** | 0 | 0 | 0 |
| Integración | Failsafe | **74** | 0 | 0 | 0 |

- **Unitarios (463):** 54 clases `*Test`. Sin Docker, sin base, sin variables de entorno. Cubren services (mocks), entidades (transiciones), utilidades (GS1, serie, CUIT, DNI, JSON canónico, hash), seguridad y el handler. El número de Surefire (463) es mayor que el de anotaciones `@Test` contadas por grep (423) porque **cada `@ParameterizedTest`/`@RepeatedTest` cuenta por invocación**, no por método; por eso no sirve contar por grep.
- **Integración (74):** 19 clases `*IT`. Failsafe, contra PostgreSQL real (`postgres:16.15-alpine`, Testcontainers). Un contenedor y un contexto para toda la suite; `LimpiadorBase` vacía la base antes de cada test. Cubren el flujo completo, concurrencia de la cadena, filtros, bloqueo R10, errores sin datos personales, migraciones, contrato OpenAPI, nombres en DTO, tablero, trazabilidad por código, fechas UTC, verificación de cadena, transacciones y restricciones.
- **`EscenarioIntegracion.java` no aporta tests:** es un helper anotado con `@TestComponent` (no tiene ningún `@Test`). Surefire y Failsafe solo ejecutan clases `*Test` y `*IT`, así que aunque tuviera un `@Test` no correría. (El "`@Test` colado" de una versión previa de este informe era un falso positivo: `grep "@Test"` matchea el prefijo de `@TestComponent`.)

**Comandos:**
```bash
./mvnw test        # unitarios (sin Docker)
./mvnw verify      # unitarios + integración (necesita Docker)
./mvnw verify -DskipITs                               # omitir integración
./mvnw verify -Dit.test=ContratoOpenApiIT -Dmedichain.contrato.actualizar=true   # regenerar docs/openapi.json
```

## 14. Scripts y operación (`scripts/`, raíz)

| Script | Para qué | Cuándo |
|---|---|---|
| `levantar.sh` (raíz) | `docker start postgres-pruebas` + `source .env` + `./mvnw spring-boot:run` | Arrancar la app en local (8080) |
| `reset-demo.sh` (raíz) | Borra el schema `public` (incluye historial Flyway) y los PDF huérfanos; pide escribir `BORRAR`; solo si `DB_URL` es localhost | Recargar la demo desde cero |
| `scripts/prueba-e2e.py` | Prueba e2e de punta a punta por la API (secciones por paso) | Tras resetear y levantar; valida el recorrido completo |
| `scripts/medichain_api.py` | Helper HTTP compartido por los scripts Python | (librería) |
| `scripts/simular-sensor.py` | Reporta lecturas de temperatura reales a un viaje | Demostrar telemetría / ruptura de frío |
| `scripts/simular-ataque.py` | Altera un evento anclado para mostrar que la verificación lo detecta; pide `ATACAR`; solo base local | Demostrar la detección de alteraciones |
| `scripts/nuevo-contrato.sh` | Despliega un contrato `MediChainAnchor` nuevo (misma billetera) y actualiza **solo** `ANCHOR_CONTRACT_ADDRESS` del `.env` (`--simular` no gasta; `--desplegar` pide `DESPLEGAR`) | Después de un reset con anclaje habilitado |
| `scripts/desplegar-contrato.sh` | Despliega contratos **descartables** de prueba (usa `nuevo-contrato.sh`) | Pruebas de anclaje |
| `scripts/anclar-prueba.sh` | Un anclaje de prueba en un contrato descartable (mismo camino real) | Probar el flujo de gas/firma |
| `scripts/actualizar_env.py` (+ `test_actualizar_env.py`) | Cambia una sola línea del `.env` con copia de seguridad y validación | Lo usa `nuevo-contrato.sh` |

**Orden típico en desarrollo:** `./reset-demo.sh` → `./levantar.sh` → (esperar "Datos de demo cargados") → `python3 scripts/prueba-e2e.py`.

> Nota de seguridad: un agente de código **nunca** debe desplegar ni enviar transacciones que gasten ETH sin confirmación explícita del autor (`--simular`/`--saldo` sí).

## 15. Variables de entorno (solo nombres; los valores van en `.env`, nunca a Git)

| Variable | Para qué |
|---|---|
| `DB_URL` | URL JDBC de PostgreSQL |
| `DB_USER` | Usuario de la base |
| `DB_PASSWORD` | Contraseña de la base (obligatoria, sin default) |
| `JWT_SECRET` | Clave de firma del JWT (obligatoria, sin default) |
| `ADMIN_EMAIL` | Email del usuario SEDE_CENTRAL inicial |
| `ADMIN_PASSWORD` | Contraseña de la Sede inicial |
| `SPRING_PROFILES_ACTIVE` | Perfil activo (`demo` para cargar datos de demostración) |
| `DEMO_PASSWORD` | Contraseña común de las cuentas de demo |
| `SWAGGER_HABILITADO` | Publica `/api-docs` y Swagger UI (default false) |
| `DOCUMENTOS_DIR` | Carpeta (fuera del repo) donde se guardan los PDF de habilitación |
| `ANCLAJE_HABILITADO` | Enciende el anclaje en Sepolia (default false) |
| `SEPOLIA_RPC_URL` | Endpoint RPC de Sepolia (lleva API key; secreto) |
| `WALLET_PRIVATE_KEY` | Clave privada de la billetera que ancla (secreto) |
| `ANCHOR_CONTRACT_ADDRESS` | Dirección del contrato `MediChainAnchor` desplegado |

`application.properties` solo referencia `${VAR}` (obligatorios DB_PASSWORD, JWT_SECRET) o `${VAR:}` (opcionales), nunca con default para un secreto.

## 16. Estado actual: hecho, pendiente y deuda técnica

**Terminado** (según la bitácora y el código): pasos 1–10 del backend (dominio, reglas, seguridad, cadena de eventos, anclaje en Sepolia con contrato, migraciones Flyway V1–V3) y el bloque B11–B12 para el frontend: contrato OpenAPI versionado (`docs/openapi.json`, 97 operaciones), filtros `?estado=`, indicador de bloqueo R10 en DTO, fechas a UTC con calendario de Argentina (V4), tablero, búsqueda por `?codigo=`/`?serie=`, trazabilidad por código (`/recorrido`, V5) y nombres junto al id en los DTO. Tests en verde en el último corte de la bitácora.

**Pendiente / deuda técnica (verificado en código y notas de CLAUDE.md):**
- **Aislamiento de medicamentos (fuga de lectura, sin corregir):** `MedicamentoService.getAll` hace `repository.findAll(...)` y `getById` no chequea empresa (`modules/medicamento/MedicamentoService.java`), así que un laboratorio ve y abre medicamentos de otros. La **escritura** ya está aislada: `LoteService.registrar` filtra el medicamento por dueño → 404. Falta decidir qué ven DIST/FARM (no son dueños) antes de cerrar la lectura.
- **Fuga de alcance en viajes de tramo 2:** una farmacia con una parada ve todos los bultos y las demás paradas del viaje (`bultoIds`, `paradaIds`, `paradaRazonesSociales`), aunque "cada farmacia recibe solo sus bultos". Habría que recortar el DTO por rol.
- **`regla` del `ErrorResponseDTO` es `String`,** no un enum `CodigoRegla` (≈83 lugares); queda para más adelante.
- **Pendientes del frontend:** filtros/búsqueda de dispensaciones y más de 100 lecturas de temperatura por viaje (hoy de a 100).
- **Rate limiting** (login, registro, verificación pública, intentos de lote inválidos), **captcha** y **verificación de email** en el registro: pendientes.
- **Asignación por la Sede** de cuarentenas, reportes y lotes biológicos de provincias sin inspectores: pendiente (`// TODO` en `CuarentenaService` y `ReporteCiudadanoService`).
- **Simplificación del sensor:** usa la cuenta de un usuario de la empresa origen, no una credencial de dispositivo.
- No se detectaron `@SuppressWarnings` ni `FIXME`. Se corrió `./mvnw verify` (2026-10-24): **BUILD SUCCESS**, 463 unitarios + 74 de integración, 0 fallos (§13).

## 17. Glosario

- **GTIN:** código global del producto (14 dígitos, con dígito verificador GS1). Identifica al medicamento.
- **Serie:** número de serie único por GTIN que identifica a una caja individual. GTIN + serie es lo que codifica el DataMatrix `(01)GTIN(21)serie`.
- **Caja (UnidadTrazable):** una unidad física vendible, identificada por GTIN + serie.
- **Lote:** conjunto de cajas fabricadas juntas; código único por laboratorio (`L2026-0001`).
- **Bulto:** caja de cartón que agrupa cajas de un mismo lote con destino a un circuito; código `BUL-0001`.
- **Circuito (EnlaceCuit):** acuerdo aprobado laboratorio → distribuidora → farmacia; código `CIR-0001`. Un solo vigente por par lab–farmacia.
- **Tramo:** etapa del transporte. Tramo 1 = laboratorio → distribuidora; tramo 2 = distribuidora → farmacias.
- **Viaje (DespachoLogistico):** un camión con varios bultos y paradas; código `VJ-0001`.
- **Recepción:** acto de recibir un bulto escaneando su código; el servidor decide si es conforme.
- **Cuarentena:** medida sanitaria que bloquea cajas. Tres alcances: **LOTE** (todas las cajas del lote), **DESPACHO** (bultos de un viaje: ruptura de frío o robo), **BULTO** (un bulto rechazado).
- **Recall / retiro del mercado:** una cuarentena convertida en retiro definitivo; las cajas quedan bloqueadas para siempre.
- **Ruptura de frío:** lectura de temperatura fuera del rango del medicamento durante el viaje.
- **Bloqueado (R10):** estado calculado (no guardado): una caja no puede viajar/recibirse/dispensarse si su lote/viaje/bulto tiene medida activa o recall, o el lote venció.
- **Evento de trazabilidad:** asiento inmutable y numerado de la cadena; cada acción genera uno.
- **Cadena / hash:** cada evento encadena con el hash SHA-256 del anterior; alterar uno rompe la cadena.
- **Anclaje:** registrar el último hash de la cadena en el contrato de Sepolia, como sello de tiempo verificable.
- **Dictaminar:** acción del inspector sobre una medida o reporte (levantar, recall, cerrar), tras tomarla.
- **DT (director técnico) / admin de empresa:** marcas del usuario de empresa que habilitan liberar lotes comunes / aceptar circuitos y crear empleados.
- **Sede:** usuario SEDE_CENTRAL; administra inspectores y asigna lo que no tiene inspector en su provincia.

## 18. Dudas y cosas sin confirmar

- **`GET /api/verificacion` devuelve 200 con `NO_EXISTE` (no 404) a propósito:** así un atacante no puede enumerar qué series existen por el código HTTP (todas responden 200). Se complementa con el tope de `IntentoVerificacion` (como mucho un evento SERIE_INEXISTENTE/ROBADA por (tipo, gtin, serie) por día UTC, y un tope diario global de 500 SERIE_INEXISTENTE). **No es una irregularidad**, es una decisión de diseño.
- **El incidente del anclaje del 17/10 (6 transacciones revertidas) no se puede confirmar solo con el código:** su rastro estaría en las filas `FALLIDO` de `registros_blockchain` y en los logs de esa fecha, no en el fuente. §11 explica el mecanismo (las dos causas) con el código actual; el conteo exacto y por qué las defensas no frenaron ese día son historia de ejecución que no leí (no toqué la base 5433 ni `.env`).
- Los **campos exactos** de cada DTO de respuesta (y cuáles son `required`/`nullable`) no se listan uno por uno; para el detalle fino conviene `docs/openapi.json`. Las relaciones y estados de las entidades sí se verificaron en código.
- La descripción de "qué guarda cada tipo de evento" (§10) la agrupé por familia; el detalle campo por campo está en `DatosEventos.java` (un método por tipo) y no lo transcribí entero.
- La lista de endpoints y sus códigos de error sale de `@PreAuthorize` y `@RespuestasError` en los controllers; no verifiqué uno por uno que cada 404/409 declarado se dispare en todos los caminos (lo cubre `ContratoOpenApiIT`, que pasó en la corrida de §13).
- No abrí `.env` (por regla): los nombres de variables salen de `application.properties` y `.env.example`, no de valores reales.
