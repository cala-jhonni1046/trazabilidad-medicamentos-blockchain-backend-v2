#!/bin/bash
# desplegar-contrato.sh: despliega en Sepolia un contrato MediChainAnchor DESCARTABLE
# (bytecode de contracts/MediChainAnchor.bin) para las pruebas e2e --sepolia sobre una
# base descartable. El contrato PRINCIPAL de la demo se despliega a mano con Remix
# (contracts/GUIA-DESPLIEGUE.md).
#
# Uso (desde la raíz del repo, con los secretos SOLO en el entorno):
#   set -a; source .env; set +a
#   scripts/desplegar-contrato.sh --saldo                 # solo billetera y saldo
#   scripts/desplegar-contrato.sh [--max-eth 0.002] [--tope-gwei 5]
#
# Nunca imprime SEPOLIA_RPC_URL ni WALLET_PRIVATE_KEY. Se niega a desplegar si el costo
# máximo posible supera --max-eth o si la comisión de la red supera --tope-gwei.
# Por qué Java y no Python: firmar una transacción de Ethereum necesita keccak-256 y
# secp256k1, que Python no trae; web3j ya es dependencia del proyecto.
set -euo pipefail
cd "$(dirname "$0")/.."

./mvnw -q test-compile
./mvnw -q dependency:build-classpath -Dmdep.includeScope=test -Dmdep.outputFile=target/classpath-herramientas.txt
java -cp "target/test-classes:target/classes:$(cat target/classpath-herramientas.txt)" \
    com.medichain.herramientas.DesplegadorContrato "$@"
