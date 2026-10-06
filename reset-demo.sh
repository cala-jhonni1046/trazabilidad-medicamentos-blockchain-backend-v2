#!/bin/bash
# reset-demo.sh: BORRA TODA la base local de MediChain (schema public) para
# que Hibernate la recree al próximo arranque. Uso solo en desarrollo.
# Protecciones: se niega si DB_URL no apunta a localhost y pide escribir BORRAR.
set -euo pipefail

CONTENEDOR="postgres-pruebas"

set -a
source .env
set +a

if [[ "${DB_URL:-}" != *"localhost"* ]]; then
    echo "ABORTADO: DB_URL no contiene 'localhost'. Este script solo borra bases locales."
    exit 1
fi

# jdbc:postgresql://localhost:5432/medichain?param=x  ->  medichain
BASE="${DB_URL##*/}"
BASE="${BASE%%\?*}"

echo "Se va a borrar TODO el contenido de la base '${BASE}' en el contenedor '${CONTENEDOR}'."
if [[ -n "${DOCUMENTOS_DIR:-}" ]]; then
    echo "También se borran los PDF de habilitación (<sha256>.pdf) de '${DOCUMENTOS_DIR}'."
fi
if [[ "${ANCLAJE_HABILITADO:-false}" == "true" ]]; then
    echo "El anclaje en Sepolia está habilitado: una base nueva necesita un contrato NUEVO (ver al final)."
fi
read -r -p "Escribí BORRAR para confirmar: " CONFIRMACION
if [[ "${CONFIRMACION}" != "BORRAR" ]]; then
    echo "Cancelado. No se borró nada."
    exit 1
fi

docker start "${CONTENEDOR}" > /dev/null
docker exec -i "${CONTENEDOR}" psql -v ON_ERROR_STOP=1 -U "${DB_USER}" -d "${BASE}" \
    -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"

# Los PDF quedan huérfanos al vaciar la base: se borran solo los que tienen
# el formato que genera la app (<64 hex>.pdf y temporales subida-*.tmp).
if [[ -n "${DOCUMENTOS_DIR:-}" && -d "${DOCUMENTOS_DIR}" && "${DOCUMENTOS_DIR}" != "/" ]]; then
    BORRADOS=$(find "${DOCUMENTOS_DIR}" -maxdepth 1 -type f \
        \( -regextype posix-extended -regex '.*/[0-9a-f]{64}\.pdf' -o -name 'subida-*.tmp' \) -print -delete | wc -l)
    echo "PDF borrados de '${DOCUMENTOS_DIR}': ${BORRADOS}"
fi

echo "Base '${BASE}' vaciada. Arrancá con ./levantar.sh: se crean las tablas, la cadena (GENESIS) y la demo."

# Anclaje (paso 8): el contrato viejo ya ancló eventos de la base anterior; para él, una cadena
# que vuelve a empezar en #1 es igual a eventos borrados. Con el contrato viejo la app no arranca.
if [[ "${ANCLAJE_HABILITADO:-false}" == "true" ]]; then
    echo ""
    echo "IMPORTANTE (anclaje habilitado): antes de ./levantar.sh desplegá un contrato NUEVO con Remix"
    echo "(contracts/GUIA-DESPLIEGUE.md) y actualizá ANCHOR_CONTRACT_ADDRESS en .env."
    echo "Con el contrato anterior la app no arranca: ya ancló eventos que la base nueva no tiene."
fi
