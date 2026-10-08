# MediChain · Guía para agentes de código
## Principios del proyecto (siempre)

- Pensá como un desarrollador senior en cada decisión.
- Código explícito: nada implícito ni "mágico". Anotaciones completas (@Table, @Column, @JoinColumn con todos sus atributos), getters y setters escritos, constructores visibles, inyección por constructor con @Autowired, @Transactional en cada método.
- Código explicado: Javadoc en cada clase y método público; comentarios breves donde la lógica no sea obvia.
- Código ordenado: respetá la estructura por módulos y los nombres del proyecto.
- Paso a paso: un tema por vez. Antes de programar un tema nuevo, explicá en pocas líneas qué vas a hacer y por qué. Al terminar, compilá, mostrá el resumen y esperá confirmación.
- Si algo no está claro, preguntá antes de inventar.

Backend de trazabilidad de medicamentos para Argentina (ANMAT). Registra cada movimiento de un medicamento, del laboratorio al paciente, en una cadena de eventos con hash SHA-256 que se ancla en Ethereum Sepolia.

Fuente de verdad del negocio: documento "MediChain: requerimientos del backend" y diagrama de clases `docs/medichain-clases-v4.puml`. Si algo de este archivo contradice el diagrama, avisá antes de cambiar código.

## Cómo trabajar

- Pensá como desarrollador senior: código explícito, legible y consistente.
- Trabajá por etapas. Al terminar cada una: `./mvnw clean compile`, `./mvnw verify` (unitarios + integración, necesita Docker), resumen de archivos creados, modificados y borrados, y esperá confirmación.
- Al terminar cada paso, extender `scripts/prueba-e2e.py` con sus verificaciones (una función `seccion_<paso>` en `SECCIONES`) y correrlo contra una base recién reseteada (o contra una instancia descartable) hasta que dé todo ✔.
- Si algo es ambiguo, preguntá. No inventes reglas, campos ni estados.
- No hagas `git push`. `.env` nunca a Git (plantilla: `.env.example`, con valores falsos); `target/` tampoco.
- `application.properties` SÍ va a Git, solo con `${VARIABLE}` y sin defaults para secretos: obligatorios `${VAR}` (DB_PASSWORD, JWT_SECRET); opcionales `${VAR:}` vacío (ADMIN_PASSWORD, DEMO_PASSWORD, SEPOLIA_RPC_URL, WALLET_PRIVATE_KEY). Nunca `${VAR:algo}` en un secreto. Revisá su diff antes de cada commit.
- Secretos (base de datos, JWT, clave de la wallet) solo en variables de entorno.

## Stack

Java 21 · Spring Boot 4.1.1 · PostgreSQL · Spring Data JPA · Spring Security · springdoc (Swagger en `/swagger-ui.html`) · web3j 6.0.0 (Jackson 3, Java 21; sin el módulo KMS de AWS) · Solidity 0.8.37 (`contracts/`) · Tests: JUnit 5 + Mockito, Testcontainers 2 (PostgreSQL 16 en Docker) · paquete base `com.medichain`.

## Tests

- `./mvnw test` → Surefire: unitarios (`*Test`), sin Docker, sin base y sin variables de entorno.
- `./mvnw verify` → además Failsafe: integración (`*IT`) contra PostgreSQL real (`postgres:16.15-alpine`, Testcontainers). Sin Docker falla con un mensaje claro; para omitirlos: `./mvnw verify -DskipITs`. Corre igual en un clon limpio sin `.env`.
- Los IT van en `src/test/java/com/medichain/integracion/` y extienden `IntegracionBase`: UN contenedor y UN contexto de Spring para toda la suite (no agregar `@MockitoBean` ni propiedades propias en un IT: crearía otro contexto); perfil `test` (`src/test/resources/application-test.properties`, valores falsos) y anclaje apagado; `@ServiceConnection` le gana a `DB_URL`, así un IT nunca toca otra base.
- Antes de cada IT, `LimpiadorBase` vacía la base (TRUNCATE de todas las tablas, cadena en GENESIS, secuencias en 1) y se recrea la Sede. NUNCA `@Transactional` en un IT: el rollback ocultaría REQUIRES_NEW, la concurrencia y el bloqueo de la cadena.
- Datos con `EscenarioIntegracion` (services reales como cada usuario, igual que DatosDemo; contraseña `EscenarioIntegracion.CLAVE`). Carreras con `EnParalelo`; las de índices únicos, deterministas (la primera transacción queda abierta hasta que la segunda se bloquea en PostgreSQL).
- Al tocar persistencia, transacciones, concurrencia o seguridad, agregar su IT además del unitario.

## Arquitectura (conservar siempre)

- Por funcionalidad: `com.medichain.modules.<modulo>/` contiene Entidad, `<Entidad>RequestDTO`, `<Entidad>ResponseDTO`, `<Entidad>Mapper`, `<Entidad>Repository`, `<Entidad>Service`, `<Entidad>Controller` y los enums del módulo.
- Compartido: `utils/` (BaseEntity, ErrorResponseDTO, GlobalExceptionHandler, `enums/Provincia`), `exceptions/` (ResourceNotFoundException → 404, ReglaNegocioException(regla, mensaje) → 409), `config/`.
- 17 módulos: empresa, usuario, inspectoranmat, enlacecuit, medicamento, lote, unidadtrazable, bulto, despachologistico, telemetriatemperatura, telemetriagps, recepcion, dispensacion, cuarentena, reporteciudadano, trazabilidad, registroblockchain.

## Estilo de código (obligatorio)

- Sin Lombok, sin MapStruct, sin records. Getters y setters escritos.
- Entidades: constructor vacío `protected` + constructor con campos obligatorios. `@Table` y `@Column(name, nullable, length, unique)` explícitos en todo.
- `@Enumerated(EnumType.STRING)` siempre.
- Relaciones: `@ManyToOne(fetch = FetchType.LAZY)` + `@JoinColumn`. Nunca EAGER. Sin `@OneToMany`.
- `@ManyToMany` solo en `DespachoLogistico.bultos` (`despacho_bulto`) y `Cuarentena.bultos` (`cuarentena_bulto`).
- Inyección por constructor con campos `private final` y `@Autowired` en el constructor.
- `@Transactional(readOnly = true)` en lecturas y `@Transactional` en escrituras, método por método.
- Controllers: solo DTOs, `ResponseEntity` con status explícito, `@Valid`, `@Tag` y `@Operation`.
- El Service resuelve las relaciones con `repository.findById(...).orElseThrow(...)`. El Mapper no accede a la base.
- Javadoc breve en clases y métodos públicos.

## Identificadores

- `id` técnico: UUID (en BaseEntity, con `@Version`).
- `codigo` legible para personas: `L2026-0415` (lote), `BUL-0001` (bulto), `CIR-0001` (circuito), `VJ-0001` (viaje), `REP-0001` (reporte). CIR, BUL, VJ y REP salen de secuencias de PostgreSQL (`InicializadorBaseDatos`).
- Caja: se identifica por **GTIN + serie** (lo que codifica el DataMatrix: `(01) GTIN (21) serie`). La serie es única POR GTIN, no en todo el sistema (restricción `(gtin, serie)` en `unidades_trazables`, con el GTIN desnormalizado en la caja).
- Código de lote: lo escribe el laboratorio, hasta 12 caracteres (letras, dígitos, guion), único POR LABORATORIO.
- Serie generada por el servidor: código del lote sin guiones + `S` + 6 dígitos, por ejemplo `L2026-0002` → `L20260002S000001`. El laboratorio también puede enviar su propia lista de series.
- `EventoTrazabilidad.numero`: Long correlativo, sin huecos.

## Endpoints

- Los estados cambian solo por endpoints de acción: `POST /api/<recurso>/{id}/<accion>` (aprobar, aceptar, liberar, salida, levantar, anular…). Nunca con PUT genérico.
- No hay DELETE de negocio. `EventoTrazabilidad` y `RegistroBlockchain` solo tienen GET.
- Listados paginados con `Page<T>` (máximo 100 por página).
- Recurso de otra empresa → 404, no 403.

## Roles

| Rol | Hace |
|---|---|
| SEDE_CENTRAL | Alta y baja de inspectores; asigna solicitudes de provincias sin inspector; suspende empresas; "anclar ya" (demo) y estado del anclaje |
| INSPECTOR | Habilita empresas y aprueba circuitos de su provincia; libera biológicos; dictamina cuarentenas y recalls; investiga reportes; consulta el estado del anclaje y verifica la cadena |
| LABORATORIO | Medicamentos, lotes, bultos; propone circuitos (DT); viajes del tramo 1 |
| DISTRIBUIDOR | Acepta circuitos (admin); recibe bultos en su depósito; viajes del tramo 2 |
| FARMACIA | Acepta circuitos (admin); recibe bultos; dispensa; anula dentro de 2 h; devoluciones |
| Empresa origen del viaje (LAB en tramo 1, DIST en tramo 2) | Registra salida, cancela (PROGRAMADO), reporta telemetría y robo/extravío |
| PACIENTE | Verifica sus cajas por GTIN + serie; reporta una caja (`/api/reportes-ciudadanos`: GTIN + serie, motivo de lista fija, provincia donde la consiguió); ve solo sus reportes (estado y fechas, no la conclusión) |
| Público | Verifica una caja por GTIN + serie: `GET /api/verificacion?gtin=…&serie=…` (estado APTA, YA_DISPENSADA, BLOQUEADA, ROBADA, EN_DISTRIBUCION o NO_EXISTE, recorrido resumido, anclaje en Sepolia con enlace a Etherscan, sin datos de paciente ni de dispensación); se registra: `POST /api/registro/empresas` (multipart: empresa + admin inicial + PDF) y `POST /api/registro/pacientes` |

Marcas del Usuario de empresa: `esAdminEmpresa` (crea empleados, acepta circuitos) y `esDirectorTecnico` (libera lotes comunes, propone circuitos). Toda acción de empresa exige empresa `HABILITADA`.

## Flujo del negocio

1. Cada empresa se registra con CUIT, GLN, provincia y PDF de habilitación → `PENDIENTE` en la bandeja de los inspectores de su provincia (si la provincia no tiene inspectores, la Sede la asigna).
2. Un inspector de esa provincia la toma y la habilita.
3. El laboratorio propone un circuito (laboratorio → distribuidora → farmacia) buscando por CUIT; aceptan la distribuidora y la farmacia; lo toma y aprueba un inspector de la provincia de la farmacia (la Sede lo asigna si esa provincia no tiene inspectores). Un solo circuito VIGENTE por par laboratorio–farmacia; una farmacia puede tener circuitos con varios laboratorios. Endpoints en `/api/circuitos` (la entidad es EnlaceCuit).
4. El laboratorio registra el lote (una serie por caja), lo libera y arma bultos cerrados; cada bulto tiene un circuito aprobado (su farmacia de destino).
5. Tramo 1: el laboratorio despacha bultos al depósito de la distribuidora, que los recibe escaneando el código.
6. Tramo 2: la distribuidora arma viajes (un camión, varias paradas); cada farmacia recibe solo sus bultos.
7. La farmacia dispensa caja por caja. El paciente verifica la serie.
Cada paso genera un `EventoTrazabilidad`.

## Reglas de negocio (fallo → ReglaNegocioException(regla, mensaje) → 409)

| Regla | Qué exige |
|---|---|
| R1 | Solo la Sede crea y da de baja inspectores. |
| R2 | Solo operan empresas HABILITADA. La habilita un inspector de la provincia de la empresa; sin inspectores, la Sede asigna uno. |
| R3 | GTIN con dígito verificador GS1 válido. Serie alfanumérica de hasta 20 caracteres, que no empiece con 779, única POR GTIN. El lote se registra con todas sus series en una operación atómica (máximo 10.000): si alguna es inválida, está repetida en la lista o ya existe para el mismo GTIN, no se crea nada y se registra INTENTO_SERIE_INVALIDA en una transacción aparte (REQUIRES_NEW). |
| R4 | Un lote pasa a LIBERADO solo por el DT de su laboratorio (común) o por un inspector de la provincia del laboratorio (biológico). |
| R5 | Circuito: tres empresas HABILITADA del tipo correcto (se vuelve a verificar al aceptar y al aprobar); lo propone el DT del laboratorio por CUIT, aceptan distribuidora y farmacia (admin), toma y aprueba un inspector de la provincia de la farmacia. Un solo circuito VIGENTE (PENDIENTE_EMPRESAS, PENDIENTE_INSPECTOR, APROBADO o SUSPENDIDO) por par laboratorio–farmacia; RECHAZADO no cuenta. |
| R6 | Un bulto tiene cajas de un solo lote LIBERADO y un circuito APROBADO. Una vez despachado no cambia contenido ni destino. |
| R7 | Tramo 1: laboratorio → distribuidora del circuito del bulto. Tramo 2: distribuidora → farmacias de los circuitos. Un viaje puede tener varias paradas. Finaliza cuando todos sus bultos se recibieron o rechazaron. |
| R8 | Se recibe escaneando el código del bulto. Al dictaminar la cuarentena BULTO de un rechazo, solo se puede levantar (aceptación por dictamen) si el rechazo fue ÚNICAMENTE por PRECINTO_ROTO; con TEMPERATURA_FUERA_DE_RANGO o CANTIDAD_DISTINTA → 409 R8 (solo cabe recall). Solo la empresa destino del bulto (`Bulto.destinoActual()`), con el viaje EN_TRANSITO. El servidor decide si es conforme: precinto roto, cantidad distinta o temperatura de llegada fuera del rango del medicamento → bulto RECHAZADO, cajas RECHAZADA + cuarentena de alcance BULTO. Un bulto bloqueado (R10) se registra y se rechaza automáticamente (motivo BULTO_BLOQUEADO, sin cuarentena nueva). Código inexistente → BULTO_INEXISTENTE; bulto ya recibido → BULTO_DUPLICADO (ambos en transacción aparte). |
| R9 | Cada bulto del viaje se evalúa contra el rango de SU medicamento. Lectura fuera de rango → RUPTURA_FRIO + cuarentena automática de alcance DESPACHO, motivo RUPTURA_FRIO, solo sobre los bultos afectados que no tengan ya una medida vigente (lecturas repetidas sin bultos nuevos: solo telemetría, sin evento). Tras ruptura de frío solo cabe recall. |
| R10 | Una caja está bloqueada si su lote, su viaje o su bulto tiene cuarentena activa o recall, o si el lote venció. Bloqueada no viaja, no se recibe, no se dispensa. "Bloqueado" se calcula; no es un estado guardado. |
| R11 | Cada caja se dispensa una sola vez (una sola dispensación no anulada). La misma farmacia puede anular dentro de 2 h y la caja vuelve a EN_STOCK. |
| R12 | Solo un inspector dictamina (levanta, recall, cierra reportes), de la provincia de la medida o del reporte (D1) y después de tomarla. Cuarentena manual: solo de LOTE, sobre un lote LIBERADO (pasa a CUARENTENA), motivo PREVENTIVA o DEFECTO_CALIDAD, la abre un inspector de la provincia del laboratorio (queda como revisor; puede nacer de un reporte que investiga). Recall de LOTE → el lote pasa a RECALL; recall de DESPACHO o BULTO → solo esos bultos (el lote no cambia). No hay LIBERADO → RECALL directo. Fundamento y conclusión obligatorios (al evento, solo su hash). Nada se edita hacia atrás: una corrección es un evento nuevo. |
| R13 | Dispensación: obra social y afiliado (o "particular"); DNI opcional (7 u 8 dígitos) que se guarda SOLO enmascarado (`*****006`, `DniUtil`): el completo nunca se persiste, se loguea ni se devuelve. Ningún dato de paciente ni de la dispensación en eventos, blockchain ni verificación pública (Ley 25.326). |
| R14 | Robo: viaje ROBADO, sus bultos ROBADO, cajas ROBADA y cuarentena DESPACHO, que no se levanta (409 R14; solo cabe recall). Caja devuelta: DEVUELTA, no vuelve a dispensarse. |
| R15 | Cada acción genera un evento encadenado por hash. Cada 5 minutos, si hubo eventos nuevos, el último hash se ancla en Sepolia. No se ancla sobre una cadena que ya no coincide con el último anclaje del contrato (base alterada o reseteada): "anclar ya" → 409 R15 y la tarea se detiene con un ERROR en el log. |

## Códigos de error

Todo `ReglaNegocioException(regla, mensaje)` responde 409 y devuelve el código en el campo `regla` del `ErrorResponseDTO`.

- **R1–R15**: la regla de negocio de la tabla anterior que se incumplió (por ejemplo `R2` empresa no habilitada, `R11` caja ya dispensada).
- **Códigos no-R** (casos que no son una regla del negocio pero tampoco un error de formato):

| Código | Cuándo se usa |
|---|---|
| ROL_NO_PERMITIDO | Alta de usuario con un rol que el creador no puede asignar: la Sede crea algo distinto de SEDE_CENTRAL, o el admin de empresa crea un rol distinto al del tipo de su empresa. |
| TRANSICION_INVALIDA | La acción no corresponde al estado actual de la entidad (ver "Transiciones de estado permitidas"), o falta un paso previo (por ejemplo habilitar sin haber tomado la solicitud). Lo lanza el método de dominio de la entidad. |
| EMPRESA_DUPLICADA | Registro público con un CUIT o GLN que ya existe (son datos públicos: el mensaje lo dice). |
| LOTE_DUPLICADO | Registro de un lote con un código que el mismo laboratorio ya usó (el código es único por laboratorio). |
| REGISTRO_NO_COMPLETADO | Registro público con un email que ya tiene cuenta. Mensaje genérico, para no revelar qué cuentas existen. |
| ANCLAJE_DESHABILITADO | "Anclar ya" con `ANCLAJE_HABILITADO=false`. |
| ANCLAJE_EN_CURSO | "Anclar ya" mientras hay un anclaje PENDIENTE o ENVIADO (uno solo a la vez). |
| SIN_EVENTOS_NUEVOS | "Anclar ya" sin eventos nuevos desde el último anclaje. |
| SALDO_INSUFICIENTE | "Anclar ya" sin saldo para un anclaje (límite de gas × comisión máxima, lo que el nodo exige). |

Regla: no inventar códigos nuevos sin agregarlos a esta sección. Los errores de formato o campos obligatorios van por Bean Validation (400), no con un código. Si Sepolia no responde en un endpoint de anclaje → 503 con mensaje fijo (`ErrorBlockchainException`; el detalle saneado va solo al log).

## Transiciones de estado permitidas

- Empresa: PENDIENTE → HABILITADA | RECHAZADA · HABILITADA ↔ SUSPENDIDA. Habilitar o rechazar exige que el inspector haya tomado (o tenga asignada) la solicitud. Suspender una empresa suspende sus circuitos APROBADO (`suspendidoPorEmpresa`); al rehabilitarla vuelven a APROBADO solo esos, si sus tres empresas están HABILITADA.
- InspectorAnmat: ACTIVO ↔ BAJA. La baja deja inactiva su cuenta y devuelve a la bandeja sus solicitudes tomadas; la reactivación no se las devuelve.
- EnlaceCuit: PENDIENTE_EMPRESAS → PENDIENTE_INSPECTOR (aceptaron las dos) → APROBADO | RECHAZADO (exige que el inspector lo haya tomado o tenga asignado) · PENDIENTE_EMPRESAS → RECHAZADO · APROBADO ↔ SUSPENDIDO (manual, por inspector de la provincia de la farmacia o Sede; o en cascada por una empresa: vuelve solo al rehabilitarla). RECHAZADO es definitivo: se vuelve a proponer.
- Lote: PENDIENTE_LIBERACION → LIBERADO ↔ CUARENTENA (por una medida de LOTE; un PENDIENTE_LIBERACION no se pone en cuarentena) → RECALL (la medida de LOTE se convierte en recall). Sin LIBERADO → RECALL directo.
- Bulto: ARMADO → EN_TRANSITO → EN_DEPOSITO (recepción conforme tramo 1) → EN_TRANSITO → RECIBIDO (recepción conforme tramo 2) · EN_TRANSITO → RECHAZADO | ROBADO · ARMADO (sin viaje) → DESARMADO (sus cajas quedan libres) · RECHAZADO → EN_DEPOSITO (tramo 1) | RECIBIDO (tramo 2) SOLO por aceptación por dictamen: se levantó su cuarentena BULTO y el rechazo había sido únicamente por PRECINTO_ROTO.
- UnidadTrazable: EN_LABORATORIO → EN_TRANSITO → EN_DEPOSITO → EN_TRANSITO → EN_STOCK → DISPENSADA · DISPENSADA → EN_STOCK (anulación ≤ 2 h, misma farmacia, con motivo) · EN_STOCK → DEVUELTA (motivo DANADA / VENCIDA / RETIRO_DEL_MERCADO / OTRO) · EN_TRANSITO → ROBADA · EN_TRANSITO → RECHAZADA (su bulto fue rechazado; queda en la receptora) · RECHAZADA → EN_DEPOSITO (tramo 1) | EN_STOCK (tramo 2) SOLO por aceptación por dictamen (mismas condiciones que el bulto). Si la medida se convierte en recall, quedan RECHAZADA y bloqueadas para siempre (su destrucción es un proceso fuera del sistema).
- DespachoLogistico (viaje, `/api/viajes`): PROGRAMADO → EN_TRANSITO → FINALIZADO (cuando ninguno de sus bultos sigue en curso: todos recibidos o rechazados) · EN_TRANSITO → ROBADO · PROGRAMADO → CANCELADO (sus bultos quedan sin viaje). Los bultos se fijan al crearlo: no se agregan ni se sacan.
- Cuarentena: ACTIVA (un inspector la toma: CUARENTENA_TOMADA) → LEVANTADA (no si RUPTURA_FRIO → 409 R9, ni ROBO → 409 R14, ni rechazo de recepción con temperatura o cantidad → 409 R8) | CONVERTIDA_EN_RECALL. Dictaminar exige haberla tomado.
- ReporteCiudadano: ABIERTO → EN_INVESTIGACION (lo toma un inspector de la provincia declarada) → CERRADO (con conclusión, solo quien lo tomó).
- RegistroBlockchain: PENDIENTE → ENVIADO (transmitida) → CONFIRMADO (3 confirmaciones = bloques encima) · ENVIADO → ENVIADO (reemplazo: sin incluir a los 3 min, mismo nonce y +25 % de comisión) · PENDIENTE → FALLIDO (5 intentos de envío: ERROR_DE_RED; o no se envió porque fallaría seguro: REVERT simulado, GAS_SOBRE_EL_MAXIMO) · ENVIADO → FALLIDO (minada con error: SIN_GAS si usó todo el límite, si no REVERT; o 5 intentos sin incluirse: SIN_INCLUIR). Todo FALLIDO guarda `causaFallo`. Un FALLIDO no deja huecos: el siguiente anclaje cubre desde el último exitoso + 1. Sin setters: solo métodos de dominio.
Cualquier otra transición → 409.

## Cuarentena: tres alcances

| Alcance | Cuándo | Abre | Bloquea |
|---|---|---|---|
| LOTE | Defecto de calidad o preventiva (puede nacer de un reporte ciudadano) | Inspector de la provincia del laboratorio | Todas las cajas del lote, estén donde estén |
| DESPACHO | Ruptura de frío (bultos afectados) o robo (todos) en un viaje | Sistema | Esos bultos del viaje |
| BULTO | Recepción rechazada | Sistema | Ese bulto |

## Cadena de eventos y blockchain

- Una sola cadena para todo el sistema. `hash_n = SHA-256(UTF-8(json_canónico_n))`, donde el JSON incluye `hashAnterior` (= `hash_{n-1}`; el primero usa `GENESIS`). Hash en 64 hex minúscula, sin `0x` (lo agrega el anclaje).
- JSON canónico (`JsonCanonico`, sin Jackson): claves ordenadas, sin espacios, nulls omitidos; `Instant` UTC en microsegundos `yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'`; `LocalDate` `yyyy-MM-dd`; `BigDecimal` como string `stripTrailingZeros().toPlainString()`; UUID minúscula; enums por nombre. Campos hasheados: actorEmpresaId, actorUsuarioId, datos, entidadId, entidadTipo, fechaHora, hashAnterior, numero, tipo.
- Alta solo por `RegistradorEventos`, DENTRO de la transacción de negocio (`propagation = MANDATORY`): bloquea la fila única `cadena_estado` (`SELECT … FOR UPDATE`), numero = último + 1, `fechaHora` tomada después del bloqueo, guarda y avanza el estado. Rollback del negocio = rollback del evento. `cadena_estado` (0, GENESIS) se crea al arrancar si falta.
- Datos del evento: lista blanca por tipo en `DatosEventos`, nunca la entidad entera. Prohibido: DNI, obra social, afiliado, receta, datos del paciente, patente, chofer, textos libres.
- Actor: `actorUsuarioId` + `actorEmpresaId`; se omiten si el actor es PACIENTE o es el sistema. Los usuarios (altas de empleados/pacientes) no generan eventos.
- Telemetría: solo la empresa origen y con el viaje EN_TRANSITO; solo `RUPTURA_FRIO` entra a la cadena; el resumen de temperatura del viaje (lecturas, mínima, máxima, fuera de rango) va dentro de `VIAJE_FINALIZADO`.
- `LOTE_REGISTRADO` lleva `cantidadSeries` y `seriesHash` = SHA-256 de las series ordenadas unidas por `\n` (`HashUtil.seriesHash`), nunca la lista de series.
- Verificación: `GET /api/eventos-trazabilidad/verificacion` (SEDE, INSPECTOR) recalcula toda la cadena en bloques de 500.
- Anclaje por tandas (paso 8, módulo `registroblockchain`): tarea `@Scheduled` (`TareaAnclaje`, solo con `ANCLAJE_HABILITADO=true`) cada 5 min ancla el hash del último evento en el contrato `MediChainAnchor.anclar(bytes32 hash, uint64 hastaNumero)`; otra cada 15 s sigue el anclaje en curso. `RegistroBlockchain` guarda `desdeNumero`, `hastaNumero`, `hashAnclado`, `transactionHash`, `nonce`, comisiones, `bloque`, `confirmaciones`, `gasUsado`, `estado`. 3 confirmaciones → CONFIRMADO. Reintentos con espera (30 s, 1, 2, 4 min); el negocio nunca espera a la blockchain.
- Contrato `contracts/MediChainAnchor.sol` (Solidity 0.8.37, optimizer 200, EVM osaka): solo el dueño (quien lo desplegó, `immutable`) ancla; `hastaNumero` estrictamente creciente (nada se reescribe); guarda el hash por número y la LISTA de números anclados (`anclajes(desde, cantidad)`: se audita todo desde la blockchain, sin confiar en la base). Sin función de borrar ni reiniciar. Bytecode y ABI compilados en `contracts/`; `ContratoAnchor` arma la ABI a mano (sin clases generadas) y `ContratoAnchorAbiTest` la compara con el ABI. El contrato PRINCIPAL lo despliega el usuario con Remix (`contracts/GUIA-DESPLIEGUE.md`); `scripts/desplegar-contrato.sh` despliega contratos DESCARTABLES para pruebas. Una base nueva (reset) necesita un contrato nuevo.
- Configuración: `ANCLAJE_HABILITADO` (por defecto false: app y tests sin blockchain), `SEPOLIA_RPC_URL` y `WALLET_PRIVATE_KEY` (secretos), `ANCHOR_CONTRACT_ADDRESS`; tope de comisión 20 gwei. Habilitado con algo faltante o mal formado → no arranca (mensaje sin valores). Al arrancar (`VerificacionInicialAnclaje`): otra red, sin contrato, la billetera no es la dueña, o el contrato ancló más eventos que la base → no arranca; RPC caído → arranca con aviso; último anclaje que no coincide con la base → arranca con ERROR y el anclaje queda detenido.
- Proceso (`ProcesoAnclaje` + `PasosAnclaje`): las llamadas a la red NUNCA ocurren dentro de una transacción de base; `cadena_estado` se lee sin bloqueo. Un solo anclaje en curso (cerrojo en la JVM + índice único parcial `ux_anclaje_en_curso`). Nonce de la red (LATEST); la transacción se firma, se GUARDA (con su hash) y recién después se transmite: tras un reinicio se reconoce en la red en lugar de duplicarse. `eth_estimateGas` antes de firmar: si el contrato la rechazaría → FALLIDO sin gastar.
- Endpoints: `POST /api/registros-blockchain/anclar` (SEDE, 202 con el anclaje ENVIADO; 409 ANCLAJE_DESHABILITADO / ANCLAJE_EN_CURSO / SIN_EVENTOS_NUEVOS / R15; 503 si Sepolia no responde), `GET /api/registros-blockchain/estado` (SEDE, INSPECTOR: contrato, billetera, saldo, comisión, anclajes que alcanzan, último anclado), `GET /api/registros-blockchain` y `/{id}`.
- Verificación contra la blockchain (`VerificadorAnclajes`, dentro de `GET /api/eventos-trazabilidad/verificacion`): lee los anclajes DEL CONTRATO (páginas de 500) y compara cada hash con el evento local de ese número; `blockchain.estado` VERIFICADA / ALTERADA (con el rango) / NO_CONSULTADA (la red no respondió; no es alterada) / NO_DISPONIBLE (deshabilitado), más `eventosSinAnclar` y un `resumen`. Detecta el ataque con recálculo (evento alterado y hashes recalculados: la cadena local da íntegra), eventos borrados y filas de `registros_blockchain` adulteradas. `scripts/simular-ataque.py` lo demuestra (solo base local, pide ATACAR; se deshace con reset + contrato nuevo).
- Glamsterdam (Sepolia, 06/10/2026 13:53:36 UTC, bloque 11.856.337) encareció la creación de almacenamiento: primer anclaje de un contrato ~353.000 de gas, los siguientes ~154.000, despliegue ~2,94 M (con optimizador) / ~4,97 M (sin). Por eso el gas NUNCA es fijo: límite = `eth_estimateGas` + 30 % dentro de [`gas-minimo` 100.000; `gas-maximo` 500.000] (`PoliticaGas`). Si no entra en el techo → FALLIDO GAS_SOBRE_EL_MAXIMO SIN enviar; nunca recortar el límite y enviar igual (así fallaron 6 anclajes con un límite fijo de 300.000). Antes de firmar: `eth_call` con gas-maximo (`simularAnclaje`) + `eth_estimateGas`; si revierte → FALLIDO REVERT con el error decodificado, sin gastar.
- Frenos de la tarea automática (el "anclar ya" manual no los respeta, y un anclaje manual CONFIRMADO destraba la tarea): (1) el último anclaje terminado es FALLIDO por REVERT, SIN_GAS o GAS_SOBRE_EL_MAXIMO, o sin causa (anteriores a la columna `causa_fallo`) → WARN y no reintenta sola; ERROR_DE_RED y SIN_INCLUIR no frenan; (2) el saldo no alcanza para `saldo-minimo-anclajes` (10) anclajes al costo estimado → WARN. El manual exige saldo para uno (409 SALDO_INSUFICIENTE). `/estado` muestra los frenos en `mensaje`, `anclajeAutomaticoFrenado`, y el gas REAL del próximo anclaje (`gasPorAnclaje`, `primerAnclaje`, `limiteGas`, costo, anclajes que alcanzan, saldo mínimo).
- Código del contrato: al arrancar se compara `eth_getCode` con `src/main/resources/contrato/MediChainAnchor-codigo.json` (`CodigoEsperadoContrato`): dos variantes válidas del mismo fuente, con optimizador (el `.bin`) y sin optimizador (Remix por defecto), ignorando metadatos CBOR e immutables; si no coincide con ninguna → WARN. `scripts/anclar-prueba.sh` hace UN anclaje de prueba en un contrato DESCARTABLE por el mismo camino (simulación, estimación, PoliticaGas).
- El anclaje NO genera un evento en la cadena: sería recursivo (cada anclaje crearía un evento nuevo que anclar) y `RegistroBlockchain` + la propia blockchain ya son su registro.

## TipoEvento (lista única)

ALTA_INSPECTOR, BAJA_INSPECTOR, REACTIVACION_INSPECTOR, SOLICITUD_HABILITACION, SOLICITUD_TOMADA, SOLICITUD_ASIGNADA, HABILITACION_APROBADA, HABILITACION_RECHAZADA, EMPRESA_SUSPENDIDA, EMPRESA_REHABILITADA, CIRCUITO_PROPUESTO, CIRCUITO_ACEPTADO, CIRCUITO_RECHAZADO, CIRCUITO_TOMADO, CIRCUITO_ASIGNADO, CIRCUITO_APROBADO, CIRCUITO_SUSPENDIDO, CIRCUITO_REHABILITADO, MEDICAMENTO_REGISTRADO, LOTE_REGISTRADO, LOTE_LIBERADO, INTENTO_SERIE_INVALIDA, BULTO_ARMADO, BULTO_DESARMADO, VIAJE_CREADO, VIAJE_SALIDA, VIAJE_CANCELADO, RUPTURA_FRIO, VIAJE_FINALIZADO, ROBO_EXTRAVIO, BULTO_RECIBIDO, BULTO_RECHAZADO, BULTO_INEXISTENTE, BULTO_DUPLICADO, CUARENTENA, CUARENTENA_TOMADA, CUARENTENA_LEVANTADA, RECALL, DISPENSACION, ANULACION_DISPENSA, DEVOLUCION, INTENTO_DUPLICADO, SERIE_INEXISTENTE, SERIE_ROBADA, REPORTE_CIUDADANO, REPORTE_TOMADO, REPORTE_CERRADO.

## Seguridad y datos

- Reportes ciudadanos (R13): ni el paciente ni la descripción (ni su hash) van a la cadena; REPORTE_CIUDADANO lleva código, GTIN, serie, motivo, provincia y si la caja existe. Pendiente: la Sede asigna cuarentenas y reportes de provincias sin inspectores.
- Pendiente de rate limiting (junto a login y registro): intentos de lote con series inválidas (INTENTO_SERIE_INVALIDA). Pendiente para más adelante: la Sede asigna lotes biológicos de provincias sin inspectores.
- Registro público: 201 con identificador legible y estado (sin ids internos). PDF de habilitación: PDF real (firma `%PDF-`), hasta 5 MB, guardado en `DOCUMENTOS_DIR` (fuera del repo) como `<sha256>.pdf`; el hash va en `Empresa.documentoHash` y en SOLICITUD_HABILITACION. Pendiente: rate limiting, captcha, verificación de email.
- D1: el inspector se verifica contra la base (InspectorAnmat ACTIVO), no contra el token. Empresa de otra provincia que no tiene asignada → 404.
- R10 se calcula en un solo lugar: `cuarentena/EvaluadorBloqueo` (lote vencido, CUARENTENA/RECALL o medida vigente de alcance LOTE; bulto en una medida vigente de alcance DESPACHO o BULTO). Lo usan armar bulto, crear viaje, salida, recepción, dispensación y verificación pública.
- Anclaje y secretos: la URL del RPC (lleva la API key) y la clave nunca se imprimen ni se loguean: `OcultadorSecretos` sanea todo error de red antes de loguearlo, guardarlo en `ultimoError` o responderlo; cliente HTTP propio sin interceptor de log y `logging.level.org.web3j=INFO` (web3j en DEBUG loguea URL y cuerpo); sin `@Validated` en `AnclajeProperties` (Spring Boot imprime el valor al fallar). La billetera es exclusiva de MediChain mientras la app corre (nonce). La verificación pública muestra el anclaje (estado, transacción, bloque, enlace a Etherscan) que cubre el último hito del recorrido de la caja, SIN la dispensación (R13), y no consulta la red en vivo.
- Simulador de sensor: `scripts/simular-sensor.py` (usa los endpoints reales). Simplificación pendiente: el sensor usa la cuenta de un usuario de la empresa origen; en la realidad tiene credencial propia de dispositivo.
- Verificación pública: una caja en recall (lote RECALL o bulto en medida CONVERTIDA_EN_RECALL) se muestra BLOQUEADA con el mensaje de retiro del mercado. SERIE_INEXISTENTE solo si el GTIN es de un medicamento registrado; como mucho un evento SERIE_INEXISTENTE / SERIE_ROBADA por (tipo, GTIN, serie) por día UTC (tabla `intento_verificacion`) y tope diario global de 500 SERIE_INEXISTENTE. Pendiente: rate limiting por IP (la IP no se guarda).
- Intentos que deben quedar aunque la operación falle (BULTO_INEXISTENTE, BULTO_DUPLICADO, INTENTO_DUPLICADO, SERIE_ROBADA): `RegistradorEventosAparte` (REQUIRES_NEW), llamado antes de cualquier evento propio de la transacción.
- SQL nativo de arranque (secuencias `circuito_codigo_seq`, `bulto_codigo_seq`, `viaje_codigo_seq`, `reporte_codigo_seq`, índice único parcial del par vigente, índice único parcial `ux_anclaje_en_curso`, fila inicial de `cadena_estado`, conversión única de `inspectores_anmat.provincia` de smallint (posición, faltaba `@Enumerated`) a texto si una base anterior al paso 9 todavía la tiene así): solo en `config/InicializadorBaseDatos`. En el paso 10 se reemplaza por la migración inicial de Flyway.
- Carreras contra índices únicos: el Service valida antes, pero dos altas simultáneas pueden pasar las dos; la base deja entrar a una. Se guarda con `saveAndFlush` / `saveAllAndFlush` y la `DataIntegrityViolationException` se traduce por el nombre de la restricción (`utils/RestriccionUnica`): `ux_circuito_par_vigente` → 409 R5; `ux_lote_laboratorio_codigo` → 409 LOTE_DUPLICADO; `ux_unidad_gtin_serie` → 409 R3 + INTENTO_SERIE_INVALIDA (`RegistroIntentos.registrarChoqueDeSeries`, REQUIRES_NEW, antes de cualquier evento propio). Otra restricción se propaga (409 genérico). Las ediciones concurrentes de una misma fila las frena `@Version` (409).
- Contraseñas con BCrypt. JWT de 8 h con `sub`, `rol`, `empresaId`, `provincia`. En cada request con token, `JwtAuthenticationFilter` confirma en la base que la cuenta siga activa (`UsuarioRepository.existsByIdAndActivoTrue`): el token todavía vigente de una cuenta desactivada (inspector dado de baja, empleado desactivado) → 401.
- Los Services filtran por empresa (empleados) y por provincia (inspectores).
- Errores sin detalles internos. Fechas guardadas en UTC.