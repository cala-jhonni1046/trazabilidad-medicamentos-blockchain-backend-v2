# Bitácora del desarrollo de MediChain

**MediChain** es el backend de un sistema de trazabilidad de medicamentos para Argentina, pensado para la ANMAT con un piloto en Mendoza. Registra cada movimiento de un medicamento, del laboratorio al paciente, en una cadena de eventos encadenados por hash (SHA-256). Cada 5 minutos ancla el último hash en la blockchain pública **Ethereum Sepolia**. Así ni siquiera quien administra la base de datos puede alterar la historia sin que se note.

- **Stack:** Java 21 · Spring Boot 4.1.1 · Spring Security (JWT, BCrypt) · Spring Data JPA · PostgreSQL · springdoc (Swagger) · web3j 6.0.0 · Solidity 0.8.37 (contrato `MediChainAnchor`) · Ethereum Sepolia.
- **Objetivo:** que cada caja (identificada por GTIN + número de serie, lo que codifica el DataMatrix) tenga un recorrido verificable. Que un paciente pueda comprobar con su caja si es auténtica, si fue retirada del mercado o si ya se dispensó, sin exponer datos personales (Ley 25.326). Que la integridad del registro sea demostrable frente a terceros.
- **Alcance de esta bitácora:** del 27/09/2026 al 06/10/2026. Las fechas son de Argentina. Lo marcado **(aprox.)** o **(reconstruido del código)** no tiene un registro exacto de fecha o contenido.

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

"Tests" es la cantidad de tests automáticos (JUnit) al cerrar cada paso. "e2e" es la prueba de punta a punta por la API real (`scripts/prueba-e2e.py`): verificaciones correctas / total.

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

---

## Estado al 06/10 (reconstruido del código)

| Métrica | Valor |
|---|---|
| Entidades / tablas | 19 (17 del dominio + `CadenaEstado` + `IntentoVerificacion`) |
| Controllers / endpoints | 21 / 91. Ningún PUT ni DELETE: los estados cambian solo con acciones POST |
| Roles | 6 |
| Reglas de negocio | 15 (R1 a R15) |
| Tipos de evento | 47 |
| Tests automáticos | 432 (en 50 clases de test) |
| Prueba e2e | 92 verificaciones; 94 con anclaje real |

## Metodología y uso de IA

- **Diseño del autor:**
  - el dominio, las reglas de negocio **R1 a R15**, los roles y el flujo del sistema;
  - las decisiones en cada punto de duda: por ejemplo, el recall según el alcance, qué datos no van nunca a la cadena, el techo de gas o qué contrato usar.
- **Implementación asistida con Claude Code** (asistente de programación de Anthropic). Cada paso siguió el mismo ciclo:
  1. **Diseño (fase 1):** el asistente lee el código y propone el diseño sin modificar archivos, con una lista de dudas.
  2. **Revisión:** el autor responde las dudas y aprueba o corrige el diseño.
  3. **Implementación (fase 2):** código, tests automáticos y actualización de `CLAUDE.md`, el archivo de reglas del proyecto. Ese archivo fija el estilo (código explícito, sin Lombok, Javadoc en todo), la arquitectura por módulos, los códigos de error y las reglas de seguridad.
  4. **Pruebas:**
     - la suite completa (`./mvnw test`);
     - la prueba e2e completa (`scripts/prueba-e2e.py`) contra una instancia descartable (base y app aparte), extendida con la sección del paso;
     - pruebas manuales del autor en Swagger.
- **Reglas de trabajo con el asistente:**
  - los secretos solo en variables de entorno, sin leerlos ni imprimirlos;
  - nunca hacer push (lo hace el autor);
  - no inventar reglas, campos ni estados: ante una ambigüedad, preguntar;
  - ante un error en producción, diagnosticar con consultas de solo lectura y reportar la causa antes de corregir (como en el incidente de Glamsterdam).

## Pendiente

- **Paso 9:** tests de integración con Testcontainers (PostgreSQL real en Docker).
- **Paso 10:** migración inicial con Flyway, que reemplaza el SQL de arranque (`InicializadorBaseDatos`) y `ddl-auto=update` (problema 4 del relevamiento del 28/09).
- **Paso 11:** Actuator, Docker (imagen + docker-compose) y README.
- **Pendientes ya anotados en `CLAUDE.md`, sin paso asignado:**
  - rate limiting en login, registro, verificación pública e intentos de lote inválidos;
  - captcha y verificación de email en el registro;
  - que la Sede asigne cuarentenas, reportes y lotes biológicos de provincias sin inspectores;
  - credencial propia para el sensor de temperatura.
- **Frontend en React.**
- **Guion de la demo** para la exposición y el hackathon.
- **Diagrama de clases en `docs/`:** `CLAUDE.md` lo cita (`docs/medichain-clases-v4.puml`), pero el archivo todavía no está en el repositorio.
