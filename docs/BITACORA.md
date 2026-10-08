# Bitácora del desarrollo de MediChain

**MediChain** es el backend de un sistema de trazabilidad de medicamentos para Argentina, pensado para la ANMAT con un piloto en Mendoza. Registra cada movimiento de un medicamento, del laboratorio al paciente, en una cadena de eventos encadenados por hash (SHA-256). Cada 5 minutos ancla el último hash en la blockchain pública **Ethereum Sepolia**. Así ni siquiera quien administra la base de datos puede alterar la historia sin que se note.

- **Stack:** Java 21 · Spring Boot 4.1.1 · Spring Security (JWT, BCrypt) · Spring Data JPA · PostgreSQL · springdoc (Swagger) · web3j 6.0.0 · Solidity 0.8.37 (contrato `MediChainAnchor`) · Ethereum Sepolia · Flyway (migraciones) · Tests: JUnit 5, Mockito y Testcontainers.
- **Objetivo:** que cada caja (identificada por GTIN + número de serie, lo que codifica el DataMatrix) tenga un recorrido verificable. Que un paciente pueda comprobar con su caja si es auténtica, si fue retirada del mercado o si ya se dispensó, sin exponer datos personales (Ley 25.326). Que la integridad del registro sea demostrable frente a terceros.
- **Alcance de esta bitácora:** del 27/09/2026 al 08/10/2026. Las fechas son de Argentina. Lo marcado **(aprox.)** o **(reconstruido del código)** no tiene un registro exacto de fecha o contenido.

## Resumen

| Fecha | Paso | Qué se logró | Tests |
|---|---|---|---|
| Antes del 27/09 | Versión inicial | Backend con 14 entidades y CRUD, sin seguridad real | — |
| 27/09 | Modelado | Modelo de base (DBML), evaluación del proyecto, diagramas de clases v1 a v4, exposición | — |
| 28/09 | Relevamiento técnico | Diagnóstico: ~35-40 % de un backend productivo, 5 problemas graves | 0 tests reales |
| 29/09 | Nuevo modelo de dominio | 14 → 17 entidades en 4 etapas (A-D); 19 tablas; Swagger con 17 tags y 34 endpoints | — |
| 30/09 | Paginación | `@ParameterObject` en 17 listados; 400 claro ante un orden (`sort`) inválido | — |
| 30/09 – 02/10 (aprox.) | Pasos 1 a 5a | Secretos a variables de entorno, BCrypt, JWT, roles, manejo de errores (reconstruido del código) | — |
| 02/10 | Paso 5b | Autorización por rol en 49 endpoints y filtrado por empresa; recurso ajeno → 404 | 103 |
| 02/10 | Paso 6 | Cadena de eventos con hash SHA-256 reproducible y sin condiciones de carrera | 130 |
| 02/10 | Paso 7a | Auto-registro de empresas (con PDF) y pacientes; revisión por inspectores ANMAT de cada provincia | 176 |
| 02-03/10 | Paso 7b | Circuitos laboratorio → distribuidora → farmacia | 216 |
| 03/10 | Paso 7c | Lotes y series GS1 (GTIN + serie) | 242 |
| 03/10 | Paso 7d | Bultos, viajes, simulador de sensor, ruptura de cadena de frío y cuarentena automática | 275 |
| 05/10 | Paso 7e | Recepción, dispensación y verificación pública | 316 · e2e 57/57 |
| 05/10 | Paso 7f | Cuarentenas manuales (dictamen) y reportes ciudadanos | 340 · e2e 86/86 |
| 05/10 | Paso 8 | Anclaje en Ethereum Sepolia y verificación contra la blockchain | 399 · e2e 92/92 y 94/94 (`--sepolia`) |
| 06/10 | Paso 8 (corrección) | Gas después de la actualización Glamsterdam de Sepolia, frenos y verificación del bytecode | 432 · e2e 92/92 |
| 06/10 | Repositorio | Primer commit y publicación en GitHub (ramas `main` y `develop`) | — |
| 08/10 | Paso 9 | Tests de integración con PostgreSQL real (Testcontainers); 3 problemas encontrados y corregidos | 441 + 37 de integración · e2e 99/99 |
| 08/10 | Paso 10 | Esquema con migraciones Flyway (V1 a V3); Hibernate solo valida; fechas de auditoría en UTC | 441 + 44 de integración · e2e 99/99 |
| 08/10 | Paso B11a | Contrato OpenAPI para el frontend (operationId, errores, enums, required, páginas estables, docs/openapi.json) y login completo | 443 + 47 de integración · e2e 106/106 |

"Tests" es la cantidad de tests automáticos (JUnit) al cerrar cada paso; desde el paso 9 se suman los de integración, que corren contra PostgreSQL real. "e2e" es la prueba de punta a punta por la API real (`scripts/prueba-e2e.py`): verificaciones correctas / total.

---

## Antes del 27/09 · Versión inicial

- **Fecha:** anterior al 27/09/2026 (aprox.).
- **Qué había:** un backend Spring Boot con 14 entidades y operaciones CRUD (crear, leer, modificar, borrar) por API, sin seguridad real.
- **Resultado:** punto de partida. El relevamiento del 28/09 midió cuánto le faltaba para ser un sistema productivo.

## 27/09 · Modelado y evaluación

- **Objetivo:** fijar el dominio antes de seguir programando.
- **Qué se hizo:**
  - modelado de la base de datos en DBML;
  - evaluación del proyecto;
  - diagramas de clases en cuatro versiones (v1 a v4);
  - presentación de la exposición.
- **Resultado:** el diagrama de clases v4 pasó a ser, junto con el documento de requerimientos del backend, la fuente de verdad del negocio (lo cita `CLAUDE.md` como `docs/medichain-clases-v4.puml`).

## 28/09 · Relevamiento técnico

- **Objetivo:** medir el estado real del backend contra lo que exige un sistema productivo.
- **Resultado:** aproximadamente **35-40 %** de un backend productivo.
- **Cinco problemas graves:**
  1. `/api/**` era público: no exigía token (JWT) ni roles.
  2. Las contraseñas se guardaban en texto plano.
  3. No había tests reales.
  4. No había migraciones de base: el esquema lo generaba Hibernate (`ddl-auto=update`).
  5. La "blockchain" era una tabla editable, con PUT y DELETE por API: no garantizaba nada.
- **Consecuencia:** se decidió reemplazar el modelo de dominio y avanzar por pasos chicos, con pruebas en cada uno.

## 29/09 · Nuevo modelo de dominio (14 → 17 entidades)

- **Objetivo:** que el código refleje el diagrama de clases v4.
- **Qué se hizo:** reemplazo del modelo en cuatro etapas (A a D). Quedaron 17 entidades y 19 tablas, y Swagger con 17 tags y 34 endpoints.
- **Decisiones y por qué:**
  - **Relaciones `LAZY`, nunca `EAGER`:** se carga solo lo que se usa; evita traer medio grafo de objetos en cada consulta.
  - **Solo dos `@ManyToMany`** (los bultos de un viaje y los bultos de una cuarentena): las demás relaciones son `@ManyToOne` explícitas.
  - **Claves foráneas resueltas en el Service con `findById(...)`:** el Mapper nunca accede a la base. Las entidades tienen constructores `protected` (los exige JPA) más un constructor con los campos obligatorios.
  - **`@Version` en todas las entidades:** bloqueo optimista; dos ediciones simultáneas no se pisan en silencio.
- **Resultado:** base para todo lo siguiente. Hoy hay 19 entidades (reconstruido del código): las 17 del dominio, más `CadenaEstado` (paso 6) e `IntentoVerificacion` (paso 7e).

## 30/09 · Paginación

- **Qué se hizo:** paginación con `@ParameterObject` en los 17 listados, con máximo de 100 por página.
- **Problema encontrado y solución:** un `sort` por un campo inexistente terminaba en error 500. Ahora responde **400** con un mensaje claro. Detalle técnico: en Spring Data 4 la excepción `PropertyReferenceException` se movió al paquete `org.springframework.data.core`, y el manejador de errores tuvo que apuntar al nuevo.

## 30/09 – 02/10 (aprox.) · Pasos 1 a 5a: seguridad y base técnica

> Las fechas exactas y la división en pasos no quedaron registradas. El contenido está **reconstruido del código** tal como quedó.

- **Objetivo:** resolver los problemas 1 y 2 del relevamiento y dejar una base segura.
- **Qué se hizo (reconstruido del código):**
  - **Secretos fuera del código:** `application.properties` solo lee variables de entorno (base de datos, secreto del JWT, usuario inicial de la Sede, contraseña de la demo). Se versiona solamente una plantilla (`application-example.properties`); el archivo real y `.env` están excluidos de Git.
  - **Contraseñas con BCrypt**, con un salt distinto por contraseña.
  - **Autenticación con JWT:**
    - librería jjwt 0.13.0, firma HMAC con una clave de al menos 256 bits;
    - vence a las 8 horas;
    - lleva el usuario, el rol, la empresa y la provincia;
    - API sin sesión (stateless).
  - **Rutas públicas en esta etapa:** el login y la documentación de Swagger. Después se sumaron el registro de empresas y pacientes (paso 7a) y la verificación pública de una caja (paso 7e). Todo lo demás exige token.
  - **Seis roles:** SEDE_CENTRAL, INSPECTOR, LABORATORIO, DISTRIBUIDOR, FARMACIA y PACIENTE.
  - **Manejo de errores centralizado** (`GlobalExceptionHandler`), con un formato único (`ErrorResponseDTO`):
    - 400 en errores de validación;
    - 401 / 403 en JSON;
    - 404;
    - 409 con el código de la regla de negocio incumplida;
    - 500 sin detalles internos.
  - **Datos al arrancar:**
    - el primer usuario de la Sede Central se crea desde variables de entorno;
    - los datos de demo se cargan solo con el perfil `demo`.

## 02/10 · Paso 5b: autorización por rol

- **Objetivo:** que cada usuario haga solo lo que su rol permite, y solo sobre los datos de su empresa.
- **Qué se hizo:**
  - `@PreAuthorize` en **49 endpoints**;
  - filtrado por empresa dentro de cada Service.
- **Decisión:** pedir un recurso de **otra empresa responde 404, no 403**, para no revelar que ese recurso existe.
- **Problema encontrado y solución:**
  - Un paciente recibía 401. La causa era un token mal pegado en Swagger, no un error del sistema.
  - Para que algo así se diagnostique rápido, se agregó un log con el **motivo** de cada token rechazado (nunca el token) y un test del filtro JWT real (`FiltroJwtRealTest`).
- **Resultado:** 103 tests.

## 02/10 · Paso 6: cadena de eventos con hash

- **Objetivo:** que cada acción quede en una cadena inalterable (regla R15). Cada evento incluye el hash del anterior, así que cambiar uno rompe todos los siguientes.
- **Problemas del código previo:**
  - la numeración de eventos tenía una **condición de carrera**: dos transacciones simultáneas podían tomar el mismo número;
  - el hash **no era reproducible**, porque se calculaba sobre `toString()` y fechas `LocalDateTime` con nanosegundos que la base no conserva;
  - el primer evento encadenaba con `null`.
- **Solución:**
  - **Fila única `cadena_estado` con bloqueo `PESSIMISTIC_WRITE`** (`SELECT … FOR UPDATE`): el número es siempre el último + 1, sin huecos ni duplicados.
  - **JSON canónico propio, sin Jackson:**
    - claves ordenadas;
    - sin espacios ni nulos;
    - fechas en UTC con microsegundos;
    - decimales normalizados.
    
    Así el mismo dato produce siempre el mismo texto, y por lo tanto el mismo hash.
  - **SHA-256** sobre ese JSON; el primer evento encadena con `GENESIS`.
  - **El evento se registra dentro de la misma transacción del negocio** (`propagation = MANDATORY`): si la operación falla, el evento tampoco queda.
  - **Datos del evento por lista blanca, por tipo:** nunca la entidad entera, y nunca datos personales.
  - **Verificación de toda la cadena** (`GET /api/eventos-trazabilidad/verificacion`): recalcula todos los hashes en bloques de 500.
  - **Script `reset-demo.sh`:** vacía la base local de demo. Se niega si la base no es local y pide confirmación escrita.
- **Resultado:** 130 tests.

## 02/10 · Paso 7a: registro de empresas y pacientes, revisión por inspectores

- **Objetivo:** que las empresas se den de alta solas y que un inspector ANMAT de su provincia las habilite.
- **Qué se hizo:**
  - **Registro público de empresas** con CUIT, GLN, provincia, administrador inicial y el PDF de habilitación. Queda PENDIENTE en la bandeja de los inspectores de esa provincia.
  - **Registro público de pacientes.**
  - **Acciones del inspector:** tomar, habilitar, rechazar, suspender y rehabilitar.
  - **Acciones de la Sede:** dar de baja y reactivar inspectores.
- **Decisiones y por qué:**
  - **D1: el inspector se valida contra la base**, no solo contra el token. Ver una empresa de otra provincia → 404.
  - **El PDF se guarda fuera del repositorio,** en la carpeta `DOCUMENTOS_DIR`, con su hash SHA-256 como nombre:
    - tiene que ser un PDF real (firma `%PDF-`) de hasta 5 MB;
    - el hash va también al evento SOLICITUD_HABILITACION.
  - **Un CUIT o GLN repetido** responde un error claro (son datos públicos).
  - **Un email repetido** responde un mensaje genérico, para no revelar qué cuentas existen.
- **Problemas encontrados y solución:**
  - Swagger mostraba un **campo fantasma** que provenía de un `@AssertTrue`. Se reemplazó por una validación a nivel de clase que reporta el error sobre el campo real.
  - Había **enums guardados como número de orden** (ordinal). Se pasaron a texto (`EnumType.STRING`), para que agregar un valor no cambie el significado de los datos guardados.
  - Las **rutas inexistentes o los métodos HTTP no soportados** daban 500. Ahora responden 404 y 405.
- **Resultado:** 176 tests.

## 02-03/10 · Paso 7b: circuitos laboratorio → distribuidora → farmacia

- **Objetivo:** que la mercadería solo circule por circuitos autorizados (regla R5). Endpoints `/api/circuitos`; la entidad es `EnlaceCuit`.
- **Qué se hizo:**
  1. El director técnico del laboratorio propone el circuito buscando a las otras empresas por CUIT.
  2. Aceptan la distribuidora y la farmacia.
  3. Lo toma y aprueba un inspector de la provincia de la farmacia (o la Sede se lo asigna a uno).
  
  Además, suspender una empresa suspende sus circuitos aprobados, y al rehabilitarla vuelven solo esos.
- **Decisiones y por qué:**
  - **Un solo circuito vigente por par laboratorio–farmacia.** Además de la validación en el Service, lo garantiza un **índice único parcial** en PostgreSQL, por si llegan dos propuestas al mismo tiempo.
  - **Los códigos legibles** (CIR-0001) salen de secuencias de PostgreSQL, que son atómicas.
- **Resultado:** 216 tests.

## 03/10 · Paso 7c: lotes y series GS1

- **Objetivo:** identificar cada caja como lo hace el DataMatrix (**GTIN + número de serie**), regla R3.
- **Qué se hizo:**
  - **Alta de un lote con todas sus series en una sola operación atómica**, hasta 10.000. Las series las envía el laboratorio o las genera el servidor (por ejemplo `L2026-0002` → `L20260002S000001`).
  - **Liberación del lote (R4):**
    - si es común, la hace el director técnico de su laboratorio;
    - si es biológico, un inspector de la provincia del laboratorio.
- **Decisiones y por qué:**
  - **Validación GS1:**
    - el dígito verificador del GTIN se valida;
    - la serie es alfanumérica, de hasta 20 caracteres, y no puede empezar con 779;
    - la serie es única **por GTIN**, no en todo el sistema, como en el estándar.
  - **Si una sola serie es inválida, no se crea nada.** El intento queda registrado (INTENTO_SERIE_INVALIDA) en una transacción aparte (`REQUIRES_NEW`), para que el rollback no lo borre.
  - **Al evento LOTE_REGISTRADO va la cantidad de series y un hash de la lista**, nunca la lista completa.
  - **El código de lote es único por laboratorio** (LOTE_DUPLICADO si se repite).
- **Resultado:** 242 tests.

## 03/10 · Paso 7d: bultos, viajes y cadena de frío

- **Objetivo:** mover la mercadería en bultos y viajes, y detectar rupturas de la cadena de frío (R6, R7, R9).
- **Qué se hizo:**
  - **Bultos:** se arman con cajas de un solo lote liberado y un circuito aprobado; se pueden desarmar mientras no viajen.
  - **Viajes en dos tramos:** laboratorio → distribuidora y distribuidora → farmacias, con varias paradas. Admiten salida, cancelación y denuncia de robo.
  - **Telemetría de temperatura:** cada bulto se evalúa contra el rango de **su** medicamento.
  - **Ruptura de frío:** una lectura fuera de rango genera RUPTURA_FRIO y una **cuarentena automática** solo sobre los bultos afectados.
  - **Script `scripts/simular-sensor.py`:** simula el sensor del camión usando los endpoints reales.
- **Decisiones y por qué:**
  - **Lecturas repetidas fuera de rango no generan eventos ni cuarentenas duplicadas.**
  - **"Bloqueado" (R10) no es un estado guardado:** se calcula en un solo lugar (`EvaluadorBloqueo`), y lo usan armar bultos, crear viajes, la salida, la recepción y la dispensación.
- **Herramienta de pruebas:** después de este paso (aprox. 03-05/10) se creó `scripts/prueba-e2e.py`, que recorre el flujo completo por la API real y verifica cada respuesta. Desde ahí, cada paso suma su sección y se corre completo antes de darlo por terminado.
- **Resultado:** 275 tests.

## 05/10 · Paso 7e: recepción, dispensación y verificación pública

- **Objetivo:** cerrar el recorrido hasta el paciente (R8, R11, R13, R14).
- **Qué se hizo:**
  - **Recepción escaneando el código del bulto (R8).** El servidor decide si es conforme: precinto, cantidad y temperatura de llegada. Si no lo es, rechaza el bulto y abre una cuarentena.
  - **Intentos sospechosos que quedan registrados:** códigos inexistentes y bultos ya recibidos.
  - **Resumen de temperatura del viaje:** va dentro del evento VIAJE_FINALIZADO.
  - **Dispensación caja por caja (R11):** cada caja se dispensa una sola vez; se puede anular dentro de las 2 horas.
  - **Devoluciones.**
  - **Verificación pública sin login** (`GET /api/verificacion?gtin=…&serie=…`):
    - estados APTA, YA_DISPENSADA, BLOQUEADA, ROBADA, EN_DISTRIBUCION y NO_EXISTE;
    - recorrido resumido.
  - **Protección contra abuso de la verificación pública:** como mucho un evento por caja y por día, y un tope diario global.
- **Decisiones y por qué:**
  - **R13 (Ley 25.326):** ni la verificación pública ni la cadena de eventos muestran datos del paciente ni de la dispensación.
- **Problemas encontrados y solución:**
  - **El DNI del paciente se guardaba completo.** Ahora se guarda **solo enmascarado** (`*****006`): el número completo nunca se persiste, se loguea ni se devuelve. Hay tests que lo verifican en la entidad, en la respuesta y en el evento.
  - **Los viajes del tramo 1 nunca finalizaban:** al recibirse, sus bultos quedan EN_DEPOSITO y no RECIBIDO. Se cambió el criterio: un viaje finaliza cuando ninguno de sus bultos sigue en curso.
- **Resultado:** 316 tests; prueba e2e 57/57.

## 05/10 · Paso 7f: dictamen de cuarentenas y reportes ciudadanos

- **Objetivo:** que un inspector dictamine sobre las cuarentenas, y que los pacientes puedan reportar cajas sospechosas (R8, R9, R12, R14).
- **Qué se hizo:**
  - **Cuarentenas manuales de LOTE** (preventiva o por defecto de calidad), abiertas por un inspector de la provincia del laboratorio.
  - **Dictamen:** primero se toma la cuarentena (evento CUARENTENA_TOMADA); después se levanta o se convierte en **recall**.
  - **Alcance del recall:** el de lote retira el lote completo; el de despacho o bulto, solo esos bultos.
  - **Reportes ciudadanos** (REP-0001…): el paciente declara la provincia y el motivo; el inspector lo toma y lo cierra con una conclusión.
- **Decisiones y por qué:**
  - **Restricciones para levantar una cuarentena:**
    - un bulto rechazado solo se acepta por dictamen si el rechazo fue **únicamente** por precinto roto; con temperatura o cantidad distinta solo cabe recall;
    - una ruptura de frío no se levanta (R9);
    - un robo tampoco (R14).
  - **En la verificación pública, una caja en recall aparece BLOQUEADA**, con el mensaje de retiro del mercado.
  - **Privacidad de los reportes:**
    - ni el paciente ni la descripción del reporte (ni siquiera su hash) van a la cadena;
    - el paciente ve el estado de su reporte, pero no la conclusión interna.
- **Problema encontrado y solución:** la cuarentena manual de LOTE no bloqueaba el lote. Se corrigió: abrirla pasa el lote a CUARENTENA y bloquea todas sus cajas, estén donde estén.
- **Resultado:** 340 tests; prueba e2e 86/86.

## 05/10 · Paso 8: anclaje en Ethereum Sepolia

- **Objetivo:** cumplir la segunda mitad de R15. Que la integridad de la cadena se pueda demostrar **sin confiar en la base de datos**, guardando periódicamente el último hash en una blockchain pública.
- **Qué se hizo:**
  - **Contrato `MediChainAnchor`** (Solidity 0.8.37, `contracts/MediChainAnchor.sol`, comentado línea por línea):
    - `anclar(hash, hastaNumero)` solo la puede llamar el dueño (quien lo desplegó);
    - el número tiene que ser estrictamente creciente, así que ningún anclaje se puede reescribir;
    - guarda el hash por número y la lista de anclajes;
    - no tiene funciones para borrar ni reiniciar.
  - **Integración con web3j 6.0.0.** Es compatible con Java 21 y con Jackson 3, que usa Spring Boot 4; se verificó antes de adoptarla.
  - **Tarea programada:** cada 5 minutos, si hubo eventos nuevos, ancla el hash del último. Otra tarea sigue el envío hasta **3 confirmaciones**. Tiene reintentos con espera y reemplazo de una transacción trabada (mismo nonce, más comisión).
  - **Verificación contra la blockchain:** se agregó a la verificación de la cadena. Responde VERIFICADA, ALTERADA (con el rango alterado), NO_CONSULTADA o NO_DISPONIBLE.
  - **La verificación pública de una caja muestra el anclaje** que cubre su recorrido, con enlace a Etherscan.
  - **Endpoints:**
    - "anclar ya" (solo Sede), para la demo;
    - un tablero de estado del anclaje.
  - **Herramientas:**
    - `scripts/desplegar-contrato.sh` despliega contratos descartables para pruebas;
    - `scripts/simular-ataque.py` demuestra el ataque descripto abajo;
    - `contracts/GUIA-DESPLIEGUE.md` explica el despliegue manual con Remix y MetaMask.
- **Decisiones y por qué:**
  - **La lista de anclajes se guarda en el contrato** (cuesta algo más de gas): el verificador lee los anclajes **de la blockchain** y no de la tabla local. Borrar o editar filas de la base no oculta nada.
  - **El anclaje no genera un evento en la cadena:** sería recursivo, porque cada anclaje crearía un evento nuevo que habría que anclar.
  - **La verificación pública no usa la dispensación** para elegir el anclaje que muestra (R13): el momento del anclaje revelaría cuándo se dispensó la caja.
  - **No se ancla sobre una cadena que ya no coincide con el último anclaje** del contrato ("anclar ya" responde 409 R15).
  - **Una base nueva necesita un contrato nuevo.**
  - **Seguridad de los secretos:**
    - la URL del nodo (lleva una API key) y la clave de la billetera nunca se imprimen ni se loguean;
    - todo error de red pasa por un ocultador de secretos;
    - se usa un cliente HTTP propio sin log de pedidos, porque web3j en modo DEBUG loguearía la URL.
  - **El negocio nunca espera a la blockchain:** las llamadas a la red ocurren fuera de las transacciones de la base, y la cadena se lee sin bloqueos.
- **El ataque que detecta:** alguien con acceso a la base altera un evento y recalcula todos los hashes siguientes (el algoritmo es público). La cadena local queda coherente consigo misma, pero el hash anclado en Sepolia ya no coincide.
- **Entorno de prueba:**
  - billetera MetaMask de prueba;
  - nodo RPC de Alchemy;
  - fondos del faucet de Google Cloud;
  - contrato de prueba `0x9d71E5a18B374894120a9c57aA946992AF750A33`, con anclajes hasta los eventos #83 y #84.
- **Costos medidos ese día (antes de Glamsterdam, comisión ≈ 1,1 gwei):**

  | Operación | Gas | Costo |
  |---|---|---|
  | Despliegue | 431.270 | 0,00047527 ETH |
  | Primer anclaje | 90.651 | 0,00009515 ETH |
  | Segundo anclaje | 56.946 | 0,00005863 ETH |
- **Problema encontrado y solución:** en la primera corrida, `simular-ataque.py` abortó sin modificar nada. Su control de concurrencia comparaba contra un hash que el mismo script ya había recalculado. Se corrigió, y la demostración dio cadena local ÍNTEGRA y Sepolia ALTERADA.
- **Resultado:** 399 tests; e2e 92/92 sin blockchain y 94/94 con anclaje real (`--sepolia`).

## 06/10 · Paso 8 (corrección): contrato propio y gas después de Glamsterdam

- **Contexto:** el autor desplegó su contrato desde Remix: `0xB126804cAce2E2EB0cA4614488ebFC8a11361fb3`.
- **Problema:** **6 anclajes fallaron por falta de gas (out of gas)**, uno tras otro.
- **Diagnóstico** (solo consultas de lectura, sin enviar transacciones):
  - **La red había cambiado.** El 06/10 a las 10:53 (hora de Argentina), en el **bloque 11.856.337**, Sepolia activó la actualización **Glamsterdam**. Se confirmó porque los bloques empezaron a traer campos nuevos (`blockAccessListHash`, `slotNumber`).
  - **El almacenamiento se encareció mucho.** El primer anclaje de un contrato pasó de **~91.000 a ~353.000** de gas.
  - **El código recortaba el límite de gas a 300.000 y enviaba igual.** Cada envío fallaba seguro y cobraba todo el límite.
  - **No había freno después de un fallo,** así que la tarea automática reintentaba cada 5 minutos.
  - **El contrato del autor estaba compilado sin optimizador** (Remix lo trae destildado). Se verificó recompilando byte por byte. No era la causa: cambia unos 539 de gas por anclaje. Sí encarece el despliegue: ~4,97 M de gas contra ~2,94 M con optimizador.
- **Solución:**
  - **Antes de cada envío, simulación (`eth_call`) y estimación de gas.**
  - **Límite = estimación + 30 %**, dentro de **[100.000; 500.000]** (configurable). Si no entra, **no se envía**: queda FALLIDO con el motivo, sin gastar. Nunca se recorta el límite para enviar igual.
  - **Freno tras un fallo determinístico** (rechazo del contrato, sin gas o gas sobre el máximo). La tarea automática deja de reintentar sola hasta que la Sede ancle a mano. Los errores de red sí se reintentan.
  - **Freno por saldo bajo:** la tarea automática no ancla si el saldo no alcanza para 10 anclajes.
  - **Al arrancar, se verifica el bytecode del contrato** contra el fuente. Se aceptan las dos variantes válidas (con y sin optimizador); si no coincide con ninguna, avisa.
  - **El tablero de estado muestra el gas real del próximo anclaje,** su costo, cuántos anclajes alcanzan y si la tarea está frenada.
  - **La guía de despliegue** se actualizó con la configuración exacta de Remix y los costos nuevos.
- **Prueba en vivo de la corrección:** un anclaje (#85) en el contrato de prueba.

  | Estimación | Límite | Gas usado | Costo |
  |---|---|---|---|
  | 154.507 | 200.860 | 153.316 | 0,00016616 ETH |
- **Resultado:**
  - 432 tests; e2e 92/92.
  - **Primer anclaje real del contrato propio,** confirmado en el bloque **11.857.481** (transacción `0x87b485ca6ece239e48d5346979697926a831f89ca03e45bacdfe5bb53c99876f`). La verificación pública mostró el anclaje CONFIRMADO.
  - **Ataque simulado sobre ese contrato:**
    - se alteró el evento #18 (LOTE_REGISTRADO): `cantidadSeries` pasó de 10 a 15, para "meter" 5 cajas falsificadas en el lote;
    - se recalcularon todos los hashes siguientes;
    - resultado: **cadena local ÍNTEGRA, Sepolia ALTERADA**.

## 06/10 · Primer commit y publicación

- **Primer commit local.**
  - **Repositorio propio:** el repositorio Git existente tenía su raíz en la carpeta personal del usuario, así que se creó uno dentro del proyecto.
  - **`.gitignore` ampliado:** además de `.env`, `application.properties` y `target/`, ahora excluye claves, certificados y cachés de Python.
  - **Revisión de lo staged antes del commit:** se buscaron en lo preparado los valores reales de los secretos, sin imprimirlos, y aparecieron **0 veces**.
- **Publicación en GitHub,** con las ramas `main` y `develop`.

## 08/10 · Paso 9: tests de integración con PostgreSQL real (Testcontainers)

- **Objetivo:** probar contra una base PostgreSQL real lo que los tests con simulaciones (mocks) no pueden ver: concurrencia, transacciones, bloqueos, restricciones de la base y la cadena de seguridad completa. Además, que el proyecto se pruebe recién clonado, sin `.env` ni variables de entorno.
- **Qué se hizo:**
  - **Dos niveles de prueba:**
    - `./mvnw test`: los tests unitarios, sin Docker, sin base y sin variables de entorno;
    - `./mvnw verify`: además, los tests de integración (`*IT`) contra PostgreSQL 16 en Docker, con Testcontainers. Sin Docker fallan con un mensaje claro que indica cómo omitirlos (`-DskipITs`).
  - **`application.properties` pasó a versionarse:** solo lee variables de entorno, sin valores por defecto para los secretos. La plantilla `.env.example` (con valores falsos) reemplazó a `application-example.properties`. `.env` sigue sin ir a Git.
  - **9 clases de tests de integración (37 tests):**
    - `MedichainApplicationIT`: la aplicación completa arranca contra el contenedor y crea sus secuencias e índices; la base nunca es la del usuario; prueba también la conversión de la provincia del inspector (problema 2);
    - `CadenaConcurrenteIT`: 16 hilos registran 400 eventos a la vez y quedan numerados del 1 al 400, sin huecos y con la cadena íntegra; un rollback se lleva su evento sin dejar hueco;
    - `VerificacionCadenaIT`: la verificación detecta alteraciones hechas con SQL directo (contenido cambiado, fecha corrida un microsegundo, evento borrado);
    - `TransaccionesIT`: los intentos que deben quedar aunque la operación falle (series inválidas, bulto inexistente o ya recibido, caja ya dispensada) quedan registrados, sin bloqueo mutuo (deadlock);
    - `RestriccionesBaseIT`: índices únicos, secuencias y altas simultáneas;
    - `ConcurrenciaNegocioIT`: cuatro personas hacen a la vez la misma acción. Liberar un lote o dispensar una caja ocurre una sola vez; al armar bultos del mismo lote, ninguna caja queda en dos bultos;
    - `SeguridadIT`: login real, tokens ausentes o alterados, roles, recurso de otra empresa (404), empresa de otra provincia (D1) e inspector dado de baja;
    - `DatosSensiblesIT`: los enums se guardan como texto; el DNI completo del paciente no queda en ninguna tabla, ni en la respuesta, ni en el log;
    - `FlujoCompletoIT`: del registro de una farmacia por la API hasta la verificación pública de una caja dispensada.
  - **Prueba e2e:** sección 9 nueva (cuentas desactivadas con token vigente).
- **Decisiones y por qué:**
  - **Un solo contenedor y un solo contexto de Spring para toda la suite,** con la base vaciada antes de cada test. Así la suite completa (`./mvnw clean verify`) tarda alrededor de 1 min 30 s.
  - **Nunca `@Transactional` en un test de integración:** el rollback automático ocultaría justo lo que se prueba (transacciones aparte, concurrencia, bloqueo de la cadena).
  - **Las carreras se provocan de forma determinista:** la primera operación deja su transacción abierta hasta que la segunda queda bloqueada en PostgreSQL, esperando el índice único; recién entonces se confirma la primera. El test reproduce la carrera siempre, no "a veces".
  - **Los datos de prueba se arman con los services reales,** actuando como cada usuario (igual que los datos de demo): se respetan las reglas y cada paso deja su evento.
  - **Imagen fijada: `postgres:16.15-alpine`,** la misma versión mayor que usa el proyecto.
- **Problemas encontrados y solución:**
  1. **Inspector dado de baja con su token todavía vigente.**
     - El filtro JWT solo leía el token y no consultaba la base. Un inspector dado de baja podía seguir usando su token hasta que venciera (8 h) en las rutas que solo miran el rol, por ejemplo listados o la verificación de la cadena. Sus acciones sí se rechazaban, porque D1 lo valida contra la base.
     - Era un pendiente conocido: el test lo confirmó (200 en lugar de 401).
     - **Solución:** en cada request, el filtro confirma en la base que la cuenta siga activa; si no, 401. Vale también para empleados desactivados.
  2. **Provincia del inspector guardada como número.**
     - `InspectorAnmat.provincia` no tenía `@Enumerated(EnumType.STRING)` y se guardaba por su posición en el enum. Era la única columna así en todo el esquema: se había escapado de la corrección del paso 7a.
     - Funcionaba, pero reordenar el enum habría cambiado el significado de los datos guardados sin ningún aviso.
     - **Solución:** la anotación, más una conversión única al arrancar: en una base creada antes, la columna pasa a texto sin perder datos y con la misma restricción (CHECK) que crea Hibernate en una base nueva. En el paso 10 pasa a Flyway.
  3. **Altas simultáneas con un error genérico.**
     - Dos propuestas del mismo circuito, o dos lotes con el mismo código o con series repetidas, hechas al mismo tiempo, pasaban las dos el control del Service. La base dejaba entrar a una y la otra recibía "El dato ya existe o viola una restricción", sin el código de su regla.
     - Se detectó al diseñar los tests, y los tests de integración lo reproducen.
     - **Solución:** la violación se traduce por el nombre de la restricción: R5, LOTE_DUPLICADO y R3, con su INTENTO_SERIE_INVALIDA registrado aparte. Con la traducción desactivada, los 3 tests de carrera fallan: prueba de que la detectan.
- **Verificaciones finales:**
  - **Clon limpio:** con el contenido exacto del commit en una carpeta temporal, sin `.env` y con el entorno vacío, `./mvnw verify` pasó completo.
  - **Sin Docker** (dentro de un contenedor sin acceso a Docker): mensaje claro y build fallido; con `-DskipITs` pasan los tests unitarios.
  - **Revisión del commit:** se revisó el contenido de `application.properties` en el diff y se buscaron los valores reales de los secretos en lo preparado, sin imprimirlos: ninguno quedó en el commit.
- **Resultado:**

  | | Antes | Después |
  |---|---|---|
  | Tests unitarios (`./mvnw test`) | 432: 431 más uno de arranque que necesitaba base y variables de entorno | 441, sin Docker ni variables |
  | Tests de integración (`./mvnw verify`) | 0 | 37, en 9 clases |
  | Prueba e2e | 92/92 | 99/99 |

## 08/10 · Paso 10: esquema con migraciones Flyway

- **Objetivo:** que el esquema de la base lo creen migraciones versionadas (Flyway) y no Hibernate. Resuelve el problema 4 del relevamiento del 28/09.
- **Antes de diseñar, se midió** el esquema real que creaba Hibernate, en una base descartable:
  - 21 tablas, 45 claves foráneas, 18 restricciones UNIQUE y 26 CHECK de enums;
  - **ningún índice sobre las 45 claves foráneas** (PostgreSQL no los crea solo);
  - 63 columnas de fecha sin zona horaria (`LocalDateTime`) y una sola con zona: `eventos_trazabilidad.fecha_hora`, la que entra al hash.
- **Dos experimentos:**
  - con `ddl-auto=update`, un CHECK de enum desactualizado **no se corrige** al arrancar;
  - `ddl-auto=validate` detecta una columna faltante, pero **no detecta** un CHECK desactualizado, un largo cambiado ni un `NOT NULL` perdido.
- **Decisiones del autor:**
  - **Bases existentes: reset.** La base local del autor tenía el anclaje apagado; el contrato `0xB126…` queda como histórico y para la demo se despliega uno nuevo, compilado con optimizador.
  - **Fechas en una migración aparte (V2), solo las de auditoría** (`fecha_creacion` y `fecha_actualizacion`). Las otras 25 fechas del negocio quedan pendientes, con su propio diseño.
  - **Nombres de restricciones legibles** (`pk_`, `uk_`, `ck_`, `fk_`, `ix_`); los `ux_` se mantienen porque los usa el código.
  - **Índices sobre las claves foráneas que se consultan,** en una migración propia (V3).
  - **Los CHECK de enums se mantienen** en la base.
  - **`InicializadorBaseDatos` se borra entero,** con la conversión de provincia del paso 9.
- **Qué se hizo:**
  - **Flyway 12** (gestionado por Spring Boot) y `ddl-auto=validate`: Hibernate solo verifica el esquema al arrancar, nunca crea ni modifica tablas.
  - **`V1__esquema_inicial`:** el esquema que creaba Hibernate, más el SQL de arranque (4 secuencias, 2 índices únicos parciales y la fila inicial de la cadena). Se escribió a partir del script de Hibernate y se revisó a mano: columnas en el orden de cada entidad, un comentario por tabla y nombres legibles.
  - **`V2__fechas_auditoria_utc`:** las 38 columnas de auditoría pasan a `timestamptz` (`Instant`, UTC, microsegundos). La API devuelve esas fechas en UTC con `Z`. No toca la cadena de hashes: esas fechas no entran al hash, y `fecha_hora` de los eventos ya tenía zona.
  - **`V3__indices_claves_foraneas`:** 29 índices, cada uno con la consulta que lo usa. Las 16 claves foráneas sin índice están explicadas en el encabezado (ya cubiertas por otro índice, o nunca se busca por ellas).
  - **`MigracionesIT`** (8 tests): nombres y versiones de los archivos, historial completo, aplicar las migraciones en una base vacía, que una base con tablas y sin historial no se adopte, validación de Hibernate, la **deriva** entre entidades y migraciones, que V2 conserva el instante exacto y los hashes, que V3 está sobre claves foráneas y que la API devuelve las fechas con `Z`.
  - **Test de deriva:** Hibernate crea en otra base el esquema que deducen las entidades, y se compara con el de las migraciones: columnas (tipo, largo, `NOT NULL`), PK, UNIQUE, FK y los valores de cada CHECK, sin mirar los nombres. Cubre justo lo que `validate` no ve.
  - **Reglas nuevas en `CLAUDE.md`:** nunca editar una migración ya aplicada; cada cambio de esquema es una V nueva en el mismo commit que la entidad; un valor nuevo en un enum guardado exige reemplazar su CHECK; formato y nombres de los archivos.
  - **`reset-demo.sh`** sigue igual (borra el esquema entero, incluido el historial de Flyway); solo cambiaron sus mensajes.
- **Problemas encontrados y solución:**
  1. **`validate` no alcanza.** Un enum con un valor nuevo sin su migración arrancaría igual y fallaría recién al insertar ese valor. **Solución:** el test de deriva. Se comprobó agregando a propósito un tipo de evento sin migración y cambiando el largo de una columna: el test falló en los dos casos, mientras la validación de Hibernate pasaba. Después se restauró el código.
  2. **El orden de las columnas de dos claves primarias.** En las tablas de unión (`despacho_bulto`, `cuarentena_bulto`), el script de Hibernate ordenaba las columnas alfabéticamente, pero la base que creaba Hibernate al arrancar las tenía al revés. Lo detectó la comparación de V1 contra el esquema anterior. **Solución:** V1 sigue a la base real, y el índice de V3 en esas tablas va sobre `bulto_id`, la columna que la clave primaria no cubre.
  3. **Ninguna clave foránea tenía índice.** **Solución:** V3, con 29 índices sobre las columnas que usan las consultas.
- **Verificaciones finales:**
  - **V1 contra el esquema anterior,** comparando los catálogos de las dos bases: 279 columnas, 21 PK, 18 UNIQUE, 26 CHECK con los mismos valores, 45 FK, 2 índices parciales, 4 secuencias y la fila inicial. Iguales salvo los nombres.
  - **Copia limpia:** `git clone` en una carpeta temporal y `./mvnw verify` sin `.env` y con el entorno vacío: pasó completo.
  - **Prueba e2e sobre una base creada solo por Flyway:** Flyway aplicó V1, V2 y V3 en 1,1 s y la e2e dio 99/99, con la cadena íntegra (86 eventos).
  - **Revisión del commit:** se buscaron en lo preparado los valores reales de los secretos, sin imprimirlos: 0 apariciones.
- **Resultado:**

  | | Antes | Después |
  |---|---|---|
  | Tests unitarios (`./mvnw test`) | 441 | 441 |
  | Tests de integración (`./mvnw verify`) | 37 (9 clases) | 44 (10 clases): +8 de migraciones, −1 de la conversión de provincia que se borró |
  | Prueba e2e | 99/99 (base creada por Hibernate) | 99/99 (base creada por Flyway) |
  | Esquema | `ddl-auto=update` + SQL de arranque en Java | 3 migraciones Flyway; Hibernate solo valida |

## 08/10 · Paso B11a: contrato OpenAPI para el frontend y login completo

- **Contexto:** el frontend en Angular (repositorio aparte) leyó el contrato real (`/api-docs`) para generar su cliente y pidió ajustes. El paso B11 se dividió en cuatro sub-pasos (B11a a B11d); este es el primero, el que destraba al frontend.
- **Cómo estaba el contrato** (medido en una instancia descartable): 78 rutas y 91 operaciones; 47 operationId con sufijo automático (`asignar_1`, `bandeja_2`); las 91 documentaban solo `200` (aunque 15 altas responden `201` y `/anclar` `202`) y ningún error; `ErrorResponseDTO` no figuraba; 38 enums en línea; ningún campo obligatorio en las respuestas; tags sin orden; páginas con el formato interno de Spring (`pageable`, `sort`, `first`…), que el propio Spring advierte que no es estable.
- **Decisiones del autor:** formato de página estable ya; errores declarados por operación con una anotación propia; `enumAsRef` explícito en cada enum; ruta oficial `/api-docs`, publicada solo con `SWAGGER_HABILITADO=true`; contrato versionado en `docs/openapi.json` con un test que falla si queda desactualizado; en el login, `provincia` solo para el inspector y `expiraEn` en UTC con `Z`.
- **Qué se hizo:**
  - **operationId explícito** en las 91 operaciones (`listarBultos`, `armarBulto`, `recibirBulto`…), en español.
  - **Respuestas reales:** `201` y `202` donde corresponde, y los errores de cada operación con la anotación `@RespuestasError({...})`, que `OpenApiConfig` convierte en respuestas con `ErrorResponseDTO`. Criterios fijos (400 si recibe datos, 401 si no es pública, 403 si restringe por rol, 404 con `{id}` o con referencia a otro recurso, 409 en escrituras, 503 solo en `/anclar`).
  - **`ErrorResponseDTO` y `ErrorCampoDTO`** como esquemas con nombre (el JSON de los errores no cambió).
  - **25 enums** como esquemas con nombre, con su descripción.
  - **Campos obligatorios:** en las respuestas, cada campo marcado como obligatorio (siempre viene) o como que puede venir null, derivado de la nulabilidad de cada columna y relación; también en las páginas.
  - **Swagger para personas:** cada descripción termina con los roles (o "Público") y las reglas que aplica; ejemplos en los DTO del flujo principal; tags en el orden del flujo.
  - **Páginas estables:** `{content, page{size, number, totalElements, totalPages}}`.
  - **Login completo:** suma `esAdminEmpresa`, `esDirectorTecnico` y `provincia`; `expiraEn` pasa a `Instant` (UTC con `Z`).
  - **Operaciones públicas** marcadas sin seguridad en el contrato (antes pedían token en el cliente generado).
  - **`SWAGGER_HABILITADO`** (por defecto apagado): sin la variable, `/api-docs` y Swagger UI responden 404.
  - **`ContratoOpenApiIT`** (3 tests): las reglas del contrato; que `docs/openapi.json` esté al día; y un recorrido completo cuyas respuestas reales se validan contra su esquema (`ValidadorContrato`).
  - **Prueba e2e:** sección B11a (7 verificaciones).
- **Problemas encontrados y solución:**
  1. **Los campos obligatorios no se podían deducir del mapper a ciegas.** Varios mappers copian las relaciones de forma defensiva (`x != null ? x.getId() : null`) aunque la columna sea `NOT NULL`; la primera pasada marcaba como "puede venir null" campos que siempre vienen. **Solución:** decidir por la nulabilidad real de la relación en la entidad, y comprobarlo con respuestas reales. Prueba del validador: marcar a propósito como obligatorio un campo que viene null hizo fallar el test en el listado y en el detalle.
  2. **springdoc perdía el orden de los tags** y publicaba las respuestas como `*/*`. **Solución:** un ajuste en `OpenApiConfig` que los ordena según el flujo, y `application/json` como tipo por defecto.
  3. **Las páginas no marcaban `content`, `page` ni sus totales como obligatorios.** **Solución:** se marcan en el contrato.
  4. **Ya existía un enum `MotivoBloqueo`** (el motivo de una medida sanitaria): los códigos del indicador de bloqueo de B11b van a necesitar otro nombre de tipo.
- **Verificaciones finales:**
  - **Interruptor de Swagger,** en una instancia real: sin `SWAGGER_HABILITADO`, `/api-docs` y Swagger UI → 404 (verificado por la e2e); con `SWAGGER_HABILITADO=true`, `/api-docs` → 200 y **el contrato publicado es idéntico a `docs/openapi.json`**.
  - **Prueba e2e:** 106/106 (99 + 7 de B11a).
- **Resultado:**

  | | Antes | Después |
  |---|---|---|
  | Tests unitarios | 441 | 443 |
  | Tests de integración | 44 | 47 |
  | Prueba e2e | 99/99 | 106/106 |
  | operationId con sufijo automático | 47 | 0 |
  | Respuestas de error documentadas | 0 | 363 |
  | Enums en línea / con nombre | 38 / 0 | 0 / 25 |

---

## Estado al 08/10 (reconstruido del código)

| Métrica | Valor |
|---|---|
| Entidades / tablas | 19 entidades (17 del dominio + `CadenaEstado` + `IntentoVerificacion`) en 21 tablas (más 2 de unión) |
| Esquema | 3 migraciones Flyway (V1 a V3); Hibernate solo valida |
| Controllers / endpoints | 21 / 91. Ningún PUT ni DELETE: los estados cambian solo con acciones POST |
| Roles | 6 |
| Reglas de negocio | 15 (R1 a R15) |
| Tipos de evento | 47 |
| Tests automáticos | 443 unitarios (en 52 clases) + 47 de integración contra PostgreSQL real (en 11 clases) |
| Contrato OpenAPI | `docs/openapi.json` (91 operaciones), publicado solo con `SWAGGER_HABILITADO=true` |
| Prueba e2e | 106 verificaciones. La variante con anclaje real (`--sepolia`) dio 94 el 05/10, antes de la sección 9 |

## Metodología y uso de IA

- **Diseño del autor:**
  - el dominio, las reglas de negocio **R1 a R15**, los roles y el flujo del sistema;
  - las decisiones en cada punto de duda: por ejemplo, el recall según el alcance, qué datos no van nunca a la cadena, el techo de gas o qué contrato usar.
- **Implementación asistida con Claude Code** (asistente de programación de Anthropic). Cada paso siguió el mismo ciclo:
  1. **Diseño (fase 1):** el asistente lee el código y propone el diseño sin modificar archivos, con una lista de dudas.
  2. **Revisión:** el autor responde las dudas y aprueba o corrige el diseño.
  3. **Implementación (fase 2):** código, tests automáticos y actualización de `CLAUDE.md`, el archivo de reglas del proyecto. Ese archivo fija el estilo (código explícito, sin Lombok, Javadoc en todo), la arquitectura por módulos, los códigos de error y las reglas de seguridad.
  4. **Pruebas:**
     - la suite completa: `./mvnw test` y, desde el paso 9, `./mvnw verify` (unitarios + integración con PostgreSQL real en Docker);
     - la prueba e2e completa (`scripts/prueba-e2e.py`) contra una instancia descartable (base y app aparte), extendida con la sección del paso;
     - pruebas manuales del autor en Swagger.
- **Reglas de trabajo con el asistente:**
  - los secretos solo en variables de entorno, sin leerlos ni imprimirlos;
  - nunca hacer push (lo hace el autor);
  - no inventar reglas, campos ni estados: ante una ambigüedad, preguntar;
  - ante un error en producción, diagnosticar con consultas de solo lectura y reportar la causa antes de corregir (como en el incidente de Glamsterdam).

## Pendiente

- **Paso B11 (resto):** B11b (filtros por estado, telemetría por viaje, indicador de bloqueo R10 y códigos de error en todo 409), B11c (pendientes sin inspector para la Sede) y B11d (fechas del negocio a UTC).
- **Paso 11:** Actuator, Docker (imagen + docker-compose) y README.
- **Pendientes ya anotados en `CLAUDE.md`, sin paso asignado:**
  - pasar a `Instant`/UTC las 25 fechas del negocio que siguen sin zona horaria, con su propio diseño (dos entran a eventos nuevos y una la escribe el usuario sin zona);
  - rate limiting en login, registro, verificación pública e intentos de lote inválidos;
  - captcha y verificación de email en el registro;
  - que la Sede asigne cuarentenas, reportes y lotes biológicos de provincias sin inspectores;
  - credencial propia para el sensor de temperatura.
- **Frontend en React.**
- **Guion de la demo** para la exposición y el hackathon.
- **Diagrama de clases en `docs/`:** `CLAUDE.md` lo cita (`docs/medichain-clases-v4.puml`), pero el archivo todavía no está en el repositorio.
