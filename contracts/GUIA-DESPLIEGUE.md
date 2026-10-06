# Despliegue del contrato MediChainAnchor (Remix + MetaMask, red Sepolia)

`MediChainAnchor.sol` es el registro público de MediChain en Ethereum. Cada 5 minutos, si hubo eventos nuevos, el backend guarda ahí el hash del último evento de su cadena. Como cada evento incluye el hash del anterior, ese único hash protege a todos los eventos hasta ese número. Una vez escrito en la blockchain, nadie puede reescribirlo, ni siquiera MediChain.

Esta guía despliega el contrato **principal** (el de la demo) a mano, con Remix y MetaMask. Para las pruebas automáticas hay otro camino: `scripts/desplegar-contrato.sh`, que despliega contratos descartables.

## Antes de empezar

- **MetaMask, red y cuenta:** MetaMask en la red **Sepolia**, con la **misma cuenta** cuya clave está en `WALLET_PRIVATE_KEY`. El contrato solo acepta anclajes de quien lo desplegó.
  - Para ver qué dirección es, corré `set -a; source .env; set +a; scripts/desplegar-contrato.sh --saldo`. Muestra la dirección pública y el saldo, nunca la clave.
- **Saldo:** desde Glamsterdam (ver "Costos") el despliegue cuesta unos **0.0035 ETH** con el optimizador (0.006 sin él) y cada anclaje unos **0.0002 ETH** (el primero, 0.0004). La tarea automática necesita saldo para al menos 10 anclajes.
- **Base y contrato:** **una base nueva necesita un contrato nuevo.** Si reseteaste la base (`./reset-demo.sh`), desplegá otro contrato con esta guía.

## 1. Cargar el código en Remix

1. Entrá a <https://remix.ethereum.org>.
2. En **File explorer**, creá un archivo en la **raíz** del workspace llamado `MediChainAnchor.sol`.
3. Pegá el contenido de `contracts/MediChainAnchor.sol` de este repositorio.

## 2. Compilar

En la pestaña **Solidity Compiler**:

| Opción | Valor exacto |
|---|---|
| Compiler | `0.8.37+commit.f401782d` (la última versión, sin bugs conocidos) |
| Advanced Configurations → Language | `Solidity` |
| Advanced Configurations → EVM Version | `default` (osaka; Sepolia la soporta. De shanghai a osaka el código sale idéntico) |
| Advanced Configurations → **Enable optimization** | **TILDADO**, runs **200** |

⚠️ **En Remix la casilla "Enable optimization" viene DESTILDADA por defecto.** Tildala, poné 200 en runs y **volvé a compilar**.

Antes de desplegar, comprobá que quedó tildada: en **Compilation Details → METADATA** tiene que figurar `"optimizer":{"enabled":true,"runs":200}`.

Si no la tildás, el contrato **funciona igual**: el backend acepta las dos variantes del mismo fuente. Pero el despliegue cuesta un **70 % más** (4,97 M de gas en lugar de 2,94 M). Es lo que pasó con `0xB126…` el 06/10/2026.

Presioná **Compile MediChainAnchor.sol**. Tiene que quedar el tilde verde, sin errores ni warnings.

Anotá estos valores: Etherscan los pide al publicar el código (paso 6).

Opcional: en **Compilation Details → BYTECODE → object** el bytecode coincide con `contracts/MediChainAnchor.bin`, que es con el que se hicieron las pruebas. Los últimos bytes son el hash de metadatos y pueden variar si cambia la ruta del archivo.

## 3. Desplegar

1. En la pestaña **Deploy & Run Transactions**, en **Environment** elegí **Injected Provider – MetaMask** y aceptá la conexión en MetaMask.
2. Controlá dos cosas:
   - que debajo diga **Sepolia (11155111) network**;
   - que **Account** sea tu cuenta.
3. En **Contract** elegí `MediChainAnchor - MediChainAnchor.sol`. No lleva parámetros.
4. Presioná **Deploy**.
5. En la ventana de MetaMask revisá la red (Sepolia) y la comisión, y presioná **Confirmar**.
6. Esperá el tilde verde en la consola de Remix. En el detalle de la transacción quedan:
   - `contract address`;
   - `transaction hash`;
   - el gas usado.

Referencia actual, después de Glamsterdam: **~2,94 M de gas** con el optimizador (≈ 0.0035 ETH a 1,2 gwei) y **~4,97 M** sin él. MetaMask muestra la estimación antes de confirmar. Antes de Glamsterdam eran 431.270.

## 4. Qué copiar

- **Dirección del contrato:** en **Deployed Contracts**, botón de copiar. Ponela en tu `.env`:

  ```
  ANCHOR_CONTRACT_ADDRESS=0x...la dirección...
  ```

- **ABI:** ya está en el repo (`contracts/MediChainAnchor.abi.json`), generado por el mismo compilador. Si querés compararlo, en **Solidity Compiler** está el botón **ABI**. El backend no lo lee: arma las llamadas en `ContratoAnchor.java`, y el test `ContratoAnchorAbiTest` comprueba que coinciden con ese archivo.
- **Hash de la transacción de despliegue:** es útil para mostrarla en Etherscan durante la presentación.

## 5. Probar solo las lecturas

En **Deployed Contracts**, desplegá el contrato y presioná:

| Función | Tiene que devolver |
|---|---|
| `duenio` | tu cuenta |
| `ultimoNumero` | `0` |
| `cantidadAnclajes` | `0` |

⚠️ **No llames `anclar` a mano con valores de prueba.** Ese anclaje quedaría grabado para siempre: el backend no anclaría números menores y la verificación lo marcaría como alteración. Si querés practicar, desplegá un contrato aparte.

## 6. (Recomendado) Publicar el código en Etherscan

Así cualquiera, el jurado incluido, puede leer el código y consultar el contrato sin confiar en MediChain.

1. Abrí `https://sepolia.etherscan.io/address/<dirección del contrato>`.
2. Andá a la pestaña **Contract** y luego **Verify and Publish**.
3. Completá:
   - Compiler Type: **Solidity (Single file)**;
   - Compiler Version: **v0.8.37+commit.f401782d**;
   - License: **MIT**.
4. Siguiente pantalla:
   - pegá el código;
   - Optimization: **Yes**, runs **200**;
   - EVM Version: **default (osaka)**.
5. Verificá.

Después, en **Read Contract** se pueden llamar `hashAnclado`, `verificar`, `ultimoNumero` y `anclajes`.

También sirve el plugin **Contract Verification** de Remix.

## 7. Habilitar el anclaje en MediChain

En `.env` (nunca en Git):

```
ANCLAJE_HABILITADO=true
ANCHOR_CONTRACT_ADDRESS=0x...la dirección...
# SEPOLIA_RPC_URL y WALLET_PRIVATE_KEY ya estaban
```

Levantá la app con `./levantar.sh`. En el log tiene que aparecer:

```
Anclaje en sepolia habilitado: contrato verificado (dueño = billetera configurada), anclado hasta el evento #0 de N, saldo ... ETH
```

Si algo está mal, la app no arranca y el mensaje dice qué variable revisar, sin mostrar su valor. Los casos son:

- otra red;
- no hay contrato en esa dirección;
- la billetera no es la dueña;
- el contrato ya tiene anclajes de una base anterior.

Si el código desplegado no es `MediChainAnchor.sol` (ni con ni sin optimizador), arranca igual pero avisa con un **WARN** en el log. Si es la variante sin optimizador, lo informa: `Código del contrato verificado: MediChainAnchor.sol compilado sin optimizador`.

Antes de anclar, revisá `GET /api/registros-blockchain/estado`: muestra el gas real del próximo anclaje, el límite que se usaría, cuántos anclajes alcanzan con el saldo y si la tarea automática está frenada.

## Costos (Sepolia)

El **06/10/2026 a las 13:53:36 UTC (bloque 11.856.337)** Sepolia activó la actualización **Glamsterdam**, que encareció mucho guardar datos nuevos en la blockchain. Por eso los costos se multiplicaron.

| Operación | Antes (05/10, medido) | Después (06/10) |
|---|---|---|
| Despliegue con optimizador | 431.270 de gas | **~2.937.992** (estimado) |
| Despliegue sin optimizador | — | **~4.966.241** (estimado; `0xB126…` usó 4.925.979) |
| Primer anclaje de un contrato (crea la lista) | 90.651 | **~353.084** (estimado) |
| Anclajes siguientes | 56.946 | **~154.507** (estimado); medido el 06/10: **153.316**, 0.00016616 ETH |

A ~1,1–1,2 gwei: el primer anclaje cuesta unos **0.0004 ETH** y los siguientes unos **0.00018 ETH**. Con 0.035 SepoliaETH alcanzan unos 190 anclajes. `/estado` lo calcula con la red del momento.

**Cómo protege el backend el saldo:**

- **Límite de gas.** Usa la estimación real + 30 %, entre **100.000 y 500.000** (`medichain.anclaje.gas-minimo` / `gas-maximo`). Si no entra en el máximo, **no envía**: queda FALLIDO con el motivo, sin gastar. Nunca recorta el límite para enviar igual: eso fue lo que hizo fallar los 6 anclajes del 06/10, con un límite fijo de 300.000 cuando el primer anclaje necesitaba ~353.000.
- **Simulación previa.** Antes de cada envío simula la transacción. Si el contrato la rechazaría, tampoco envía.
- **Freno tras un fallo determinístico.** Si un anclaje falla por revert, por quedarse sin gas o por gas sobre el máximo, la tarea automática deja de reintentar sola hasta que la Sede ancle a mano (`POST /api/registros-blockchain/anclar`). Los errores de red sí se reintentan.
- **Freno por saldo.** La tarea automática no ancla si el saldo no alcanza para 10 anclajes (`medichain.anclaje.saldo-minimo-anclajes`). El anclaje manual sí, si alcanza para uno.
- **Tope de comisión.** Nunca paga más de 20 gwei por unidad de gas (`medichain.anclaje.tope-gwei`). Si la red está más cara, espera.

## Cómo explicarlo

- **Qué se guarda:** solo un hash de 32 bytes por anclaje. En la blockchain no hay datos de medicamentos ni de pacientes (R13).
- **Quién puede escribir y qué no se puede:** solo la billetera de MediChain (`duenio`, fijado al desplegar y `immutable`). Ni ella puede reescribir: `hastaNumero` tiene que ser estrictamente creciente, y no hay funciones para borrar ni reiniciar.
- **Quién puede leer:** cualquiera, sin cuenta ni costo (`hashAnclado`, `verificar`, `anclajes`).
- **Qué ataque detecta:** alguien con acceso a la base altera un evento y recalcula todos los hashes. La cadena local queda coherente consigo misma, pero ya no coincide con el hash anclado. Se demuestra con `scripts/simular-ataque.py`.
