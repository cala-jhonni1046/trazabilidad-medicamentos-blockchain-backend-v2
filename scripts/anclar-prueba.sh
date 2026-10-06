#!/bin/bash
# anclar-prueba.sh: hace UN anclaje de prueba en un contrato MediChainAnchor DESCARTABLE,
# pasando por el mismo código que usa el backend antes de enviar (simulación con
# gas-maximo, estimación + 30 % dentro de [gas-minimo; gas-maximo], código esperado).
# Nunca lo uses con el contrato principal de la demo: le agrega un anclaje de prueba.
#
# Uso (desde la raíz del repo, con los secretos SOLO en el entorno):
#   set -a; source .env; set +a
#   scripts/anclar-prueba.sh --contrato 0x... [--max-eth 0.002]
set -euo pipefail
cd "$(dirname "$0")/.."

./mvnw -q test-compile
./mvnw -q dependency:build-classpath -Dmdep.includeScope=test -Dmdep.outputFile=target/classpath-herramientas.txt
java -cp "target/test-classes:target/classes:$(cat target/classpath-herramientas.txt)" \
    com.medichain.herramientas.AnclajeDePrueba "$@"
