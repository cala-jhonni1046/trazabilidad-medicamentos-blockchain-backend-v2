# scripts/

## simular-sensor.py

Simula el sensor de temperatura (y opcionalmente GPS) de un camión durante un
viaje EN_TRANSITO, para la demo en vivo. Usa los endpoints reales del backend:
no hay endpoints especiales de demo.

- Solo biblioteca estándar de Python 3 (no hay que instalar nada).
- Inicia sesión como un usuario de la **empresa origen** del viaje (la única que
  puede reportar telemetría). La contraseña va en la variable de entorno
  `SENSOR_PASSWORD`, nunca como argumento.
- `--romper-en N` hace que la lectura número N salga fuera de rango
  (`--temperatura-rotura`, 35 °C por defecto): el backend emite RUPTURA_FRIO y
  pone en cuarentena los bultos afectados.

```bash
# Tres lecturas, la tercera a 35 °C, para el viaje VJ-0001 (después de registrar su salida):
SENSOR_PASSWORD='tu DEMO_PASSWORD' python3 scripts/simular-sensor.py \
    --email admin@laboratorio-andino.demo --viaje VJ-0001 --lecturas 3 --intervalo 2 --romper-en 3
```

Simplificación: en la realidad el sensor tiene su propia credencial de
dispositivo; acá usa la cuenta de un usuario de la empresa origen.

## prueba-e2e.py

Prueba de punta a punta: recorre el flujo real por la API (5b, 6, 7a a 7f, 8 y
la verificación final de la cadena) y verifica cada resultado. Imprime una
línea por verificación (✔ / ✘, paso, descripción, HTTP esperado y obtenido) y
al final "N de M verificaciones OK".

- Necesita una base **recién reseteada con la demo**. Si detecta que no lo está
  (por ejemplo VJ-0001 ya no está PROGRAMADO), avisa y no hace nada.
- Contraseñas solo desde variables de entorno: `DEMO_PASSWORD`, `ADMIN_EMAIL`,
  `ADMIN_PASSWORD` (las mismas del `.env`).
- Código de salida: `0` todo OK, `1` alguna verificación falló, `2` base no
  reseteada.
- Para un paso nuevo: agregar una función `seccion_XX(ctx, demo, admin)` y
  sumarla a la lista `SECCIONES`.

Cómo correrlo contra tu base:

1. Frená la app con Ctrl+C.
2. `./reset-demo.sh` (escribí `BORRAR`).
3. `./levantar.sh` y esperá "Datos de demo cargados".
4. En otra terminal, desde la raíz del repo:

```bash
set -a; source .env; set +a
python3 scripts/prueba-e2e.py
```

Contra otra URL: `python3 scripts/prueba-e2e.py --url http://localhost:8099`.

Por defecto la sección 8 prueba el anclaje **deshabilitado** (sin blockchain).
Con `--sepolia` ancla de verdad en Sepolia: la app tiene que estar levantada con
`ANCLAJE_HABILITADO=true` y un contrato **recién desplegado para esta base**
(`scripts/desplegar-contrato.sh`). Gasta un anclaje (~0.0001 SepoliaETH) y espera
hasta 5 minutos la confirmación.

## desplegar-contrato.sh

Despliega en Sepolia un contrato `MediChainAnchor` **descartable** (con el
bytecode de `contracts/MediChainAnchor.bin`) para las pruebas `--sepolia`. El
contrato principal de la demo se despliega a mano con Remix
(`contracts/GUIA-DESPLIEGUE.md`).

```bash
set -a; source .env; set +a
scripts/desplegar-contrato.sh --saldo                  # solo billetera y saldo
scripts/desplegar-contrato.sh --max-eth 0.002 --tope-gwei 5
```

- Lee `SEPOLIA_RPC_URL` y `WALLET_PRIVATE_KEY` del entorno y **nunca** los imprime.
- Se niega si el costo máximo posible supera `--max-eth` o la comisión supera
  `--tope-gwei`. Informa gas real, costo, saldo antes y después, enlaces a
  Etherscan y la línea `ANCHOR_CONTRACT_ADDRESS=...` para el `.env`.
- Es Java (`src/test/java/.../herramientas/DesplegadorContrato.java`) y no
  Python: firmar una transacción necesita keccak-256 y secp256k1, que Python no
  trae, y web3j ya es dependencia del proyecto. No forma parte de la aplicación.

## anclar-prueba.sh

Hace **UN** anclaje de prueba en un contrato `MediChainAnchor` **descartable**
(nunca en el principal de la demo: le agregaría un anclaje de prueba), por el
mismo camino que el backend antes de enviar: simulación con `gas-maximo`,
estimación + 30 % dentro de [`gas-minimo`; `gas-maximo`] y verificación del
código del contrato (con o sin optimizador).

```bash
set -a; source .env; set +a
scripts/anclar-prueba.sh --contrato 0x... --max-eth 0.002
```

Se niega si el código no es `MediChainAnchor.sol`, si la billetera no es la
dueña o si el costo máximo posible supera `--max-eth`. Informa estimación,
límite, gas usado, costo, saldo antes y después y el enlace a Etherscan.

## simular-ataque.py

Demo del **ataque con recálculo**: altera un evento ya anclado en la base,
recalcula los hashes de ese evento y de todos los siguientes (el algoritmo es
público) y actualiza `cadena_estado`. Después pide la verificación como la Sede:
la cadena local da **ÍNTEGRA**, la comparación con Sepolia da **ALTERADA**.

```bash
set -a; source .env; set +a
python3 scripts/simular-ataque.py                 # elige un evento anclado (RUPTURA_FRIO o LOTE_REGISTRADO)
python3 scripts/simular-ataque.py --numero 12     # o el que digas
```

- Solo si `DB_URL` apunta a localhost, y pide escribir `ATACAR`.
- Antes de tocar nada recalcula toda la cadena en Python y exige que coincida
  con la del backend; escribe todo en una sola transacción y se detiene si la
  app agregó un evento mientras tanto.
- Usa `psql` dentro del contenedor Docker de PostgreSQL (el que publica el
  puerto de `DB_URL`, o `--contenedor`), como `reset-demo.sh`.
- **Cómo deshacerlo:** no hay vuelta atrás limpia, como en un ataque real:
  `./reset-demo.sh`, desplegar un contrato nuevo (guía) y actualizar
  `ANCHOR_CONTRACT_ADDRESS`. Si reiniciás la app sin hacerlo, arranca pero
  avisa en ERROR y no ancla sobre la cadena alterada (R15).

## medichain_api.py

Funciones HTTP comunes de los scripts (login, requests JSON y multipart,
buscar un viaje por código). Solo biblioteca estándar.
