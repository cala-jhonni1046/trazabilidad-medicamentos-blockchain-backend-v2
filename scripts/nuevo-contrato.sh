#!/bin/bash
# nuevo-contrato.sh: deja un contrato MediChainAnchor NUEVO para una base nueva (después de ./reset-demo.sh).
#
# Por qué: el contrato guarda los hashes de la cadena anterior (#1…N). Una base reseteada vuelve a empezar en
# #1 con otros hashes, así que con el contrato viejo el anclaje se frena ("el evento #N no coincide con lo
# anclado") o la app no arranca. Un contrato nuevo parte de cero y NO debilita nada: la detección de
# alteraciones sigue comparando contra lo que hay en la blockchain, y la dirección vigente vive en el .env (y
# en la bitácora), nunca en la base.
#
# Uso (desde la raíz del repo):
#   scripts/nuevo-contrato.sh                  # = --simular
#   scripts/nuevo-contrato.sh --simular        # NO gasta nada: red, saldo, estimación de gas, costo y qué cambiaría en el .env
#   scripts/nuevo-contrato.sh --desplegar      # simula, pide escribir DESPLEGAR y recién ahí despliega (gasta SepoliaETH)
#   Opciones: --max-eth 0.006 (se niega si el costo máximo posible lo supera) y --tope-gwei 20.
#
# Qué despliega: el MISMO contrato que la guía de Remix (contracts/GUIA-DESPLIEGUE.md): contracts/MediChainAnchor.bin
# (el fuente MediChainAnchor.sol, solc 0.8.37, optimizador 200, EVM osaka: la variante recomendada), desde la cuenta de
# WALLET_PRIVATE_KEY, que queda como dueña (la app exige que la billetera configurada sea la dueña).
#
# Qué toca del .env: SOLO la línea ANCHOR_CONTRACT_ADDRESS, con una copia de seguridad (.env.bak-FECHA, permisos 600) y
# mostrando la dirección vieja y la nueva (scripts/actualizar_env.py). Nunca imprime otra línea del .env: SEPOLIA_RPC_URL
# y WALLET_PRIVATE_KEY se leen del entorno y no se muestran. Sin terminal interactiva (cron, pipes) no despliega.
set -euo pipefail
cd "$(dirname "$0")/.."

MODO="--simular"
MAX_ETH="0.006"
TOPE_GWEI="20"
while [[ $# -gt 0 ]]; do
    case "$1" in
        --simular|--desplegar) MODO="$1"; shift ;;
        --max-eth) MAX_ETH="${2:?falta el valor de --max-eth}"; shift 2 ;;
        --tope-gwei) TOPE_GWEI="${2:?falta el valor de --tope-gwei}"; shift 2 ;;
        *) echo "Argumento desconocido: $1 (usá --simular o --desplegar)"; exit 2 ;;
    esac
done

if [[ ! -f .env ]]; then
    echo "Falta el archivo .env (copiá .env.example y completalo)."
    exit 1
fi

set -a
source .env
set +a

echo "Dirección actual (ANCHOR_CONTRACT_ADDRESS del .env): ${ANCHOR_CONTRACT_ADDRESS:-(vacía)}"
echo
echo "== Simulación (no gasta nada) =="
scripts/desplegar-contrato.sh --simular --max-eth "$MAX_ETH" --tope-gwei "$TOPE_GWEI"
echo
echo "Si se despliega, SOLO cambia la línea ANCHOR_CONTRACT_ADDRESS del .env (con copia de seguridad)."

if [[ "$MODO" == "--simular" ]]; then
    echo "No se desplegó nada. Para desplegar de verdad: scripts/nuevo-contrato.sh --desplegar"
    exit 0
fi

if [[ ! -t 0 ]]; then
    echo "ABORTADO: --desplegar necesita una terminal interactiva para pedir la confirmación."
    exit 1
fi
read -r -p "Esto GASTA SepoliaETH (costo informado arriba). Escribí DESPLEGAR para continuar: " CONFIRMACION
if [[ "${CONFIRMACION}" != "DESPLEGAR" ]]; then
    echo "Cancelado. No se desplegó nada."
    exit 1
fi

SALIDA="$(mktemp)"
trap 'rm -f "$SALIDA"' EXIT
scripts/desplegar-contrato.sh --max-eth "$MAX_ETH" --tope-gwei "$TOPE_GWEI" | tee "$SALIDA"

NUEVA="$(grep -E '^ANCHOR_CONTRACT_ADDRESS=0x[0-9a-fA-F]{40}$' "$SALIDA" | tail -1 | cut -d= -f2)"
if [[ -z "$NUEVA" ]]; then
    echo "ERROR: no encontré la dirección del contrato en la salida. El .env NO se tocó."
    exit 1
fi
if grep -q "NO es la billetera" "$SALIDA"; then
    echo "ERROR: el dueño del contrato nuevo no es la billetera configurada. El .env NO se tocó."
    exit 1
fi

echo
python3 -I scripts/actualizar_env.py .env "$NUEVA"
echo
echo "Listo: contrato nuevo $NUEVA. Arrancá con ./levantar.sh (el log tiene que decir 'anclado hasta el evento #0')."
