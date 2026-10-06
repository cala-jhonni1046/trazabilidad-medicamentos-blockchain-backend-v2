#!/usr/bin/env python3
"""
Simulación del ATAQUE CON RECÁLCULO contra la cadena de eventos de MediChain (demo).

Escenario: alguien con acceso a la base (un DBA, o quien robó la credencial)
quiere cambiar la historia de un medicamento. Altera un evento y, como el
algoritmo es público (JSON canónico + SHA-256), RECALCULA el hash de ese evento
y de todos los siguientes, y actualiza cadena_estado. La cadena queda coherente
consigo misma: la verificación local dice ÍNTEGRA. Pero el hash del último
evento anclado ya está en el contrato de Sepolia, y nadie puede reescribirlo:
la verificación contra la blockchain dice ALTERADA.

Qué hace este script (SOLO contra una base local, pidiendo escribir ATACAR):
  1. Lee todos los eventos y recalcula sus hashes en Python con el mismo
     algoritmo que el backend. Si alguno no coincide, se detiene sin tocar nada.
  2. Elige un evento ya anclado (o el de --numero) y cambia uno de sus datos:
     RUPTURA_FRIO → la temperatura pasa a estar en rango (se "oculta" la ruptura);
     LOTE_REGISTRADO → 5 cajas más en el lote (lugar para 5 falsificadas).
  3. Recalcula los hashes desde ese evento hasta el último y actualiza
     cadena_estado, en una sola transacción.
  4. Pide la verificación a la API (como la Sede) y muestra:
     cadena local ÍNTEGRA / blockchain ALTERADA.

Cómo deshacerlo: no hay vuelta atrás "limpia" (como en un ataque real):
  ./reset-demo.sh, desplegar un contrato NUEVO (contracts/GUIA-DESPLIEGUE.md),
  actualizar ANCHOR_CONTRACT_ADDRESS en .env y volver a levantar la app.

Uso (desde la raíz del repo, con la app corriendo):
  set -a; source .env; set +a
  python3 scripts/simular-ataque.py [--url http://localhost:8080] [--numero N] [--contenedor postgres-pruebas]

Variables de entorno: DB_URL (tiene que apuntar a localhost), DB_USER, ADMIN_EMAIL,
ADMIN_PASSWORD. La base se lee y se escribe con psql DENTRO del contenedor Docker
de PostgreSQL (el que publica el puerto de DB_URL, o el de --contenedor), como
reset-demo.sh. Nunca imprime contraseñas.

Solo biblioteca estándar de Python 3.
"""
import argparse
import hashlib
import json
import os
import re
import subprocess
import sys

from medichain_api import iniciar_sesion, pedir

SEPARADOR = "\t"
GENESIS = "GENESIS"


# ---------- Acceso a la base (psql dentro del contenedor) ----------

class Base:
    """psql en el contenedor Docker de PostgreSQL, con la sesión en UTC."""

    def __init__(self, contenedor, usuario, nombre_base):
        self.contenedor = contenedor
        self.usuario = usuario
        self.nombre_base = nombre_base

    def ejecutar(self, sql):
        """Ejecuta SQL (por stdin) y devuelve las filas como listas de columnas."""
        comando = ["docker", "exec", "-i", "-e", "PGOPTIONS=-c TimeZone=UTC", self.contenedor, "psql",
                   "-X", "-q", "-A", "-t", "-F", SEPARADOR, "-v", "ON_ERROR_STOP=1",
                   "-U", self.usuario, "-d", self.nombre_base]
        resultado = subprocess.run(comando, input=sql, capture_output=True, text=True)
        if resultado.returncode != 0:
            sys.exit("Error de la base: %s" % resultado.stderr.strip())
        return [linea.split(SEPARADOR) for linea in resultado.stdout.splitlines() if linea]


def datos_de_conexion():
    """(host, puerto, base) de DB_URL; aborta si no es una base local."""
    url = os.environ.get("DB_URL", "")
    coincidencia = re.match(r"jdbc:postgresql://([^:/]+)(?::(\d+))?/([^?]+)", url)
    if not coincidencia:
        sys.exit("DB_URL no tiene el formato jdbc:postgresql://host:puerto/base (hacé: set -a; source .env; set +a)")
    host, puerto, nombre = coincidencia.group(1), coincidencia.group(2) or "5432", coincidencia.group(3)
    if host not in ("localhost", "127.0.0.1"):
        sys.exit("ABORTADO: DB_URL no apunta a localhost. Este script solo ataca bases locales de demo.")
    return host, puerto, nombre


def contenedor_del_puerto(puerto):
    """Nombre del contenedor Docker que publica ese puerto hacia el 5432 de PostgreSQL."""
    salida = subprocess.run(["docker", "ps", "--format", "{{.Names}}\t{{.Ports}}"], capture_output=True, text=True)
    for linea in salida.stdout.splitlines():
        nombre, _, puertos = linea.partition("\t")
        if re.search(r":%s->5432/tcp" % re.escape(puerto), puertos):
            return nombre
    sys.exit("No encontré un contenedor Docker que publique el puerto %s; indicalo con --contenedor." % puerto)


# ---------- El mismo algoritmo de hash que el backend (JsonCanonico + SHA-256) ----------

def texto_json(valor):
    """String JSON con los mismos escapes que JsonCanonico (comillas, barra invertida y controles)."""
    return json.dumps(valor, ensure_ascii=False)


def datos_canonicos(objeto):
    """JSON canónico de los datos: claves ordenadas, sin espacios (igual que JsonCanonico)."""
    return json.dumps(objeto, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def contenido_canonico(evento):
    """El JSON que se hashea (EventoTrazabilidad.contenidoCanonico): claves ordenadas, sin nulls."""
    campos = {
        "actorEmpresaId": evento["actorEmpresaId"],
        "actorUsuarioId": evento["actorUsuarioId"],
        "datos": evento["datos"],
        "entidadId": evento["entidadId"],
        "entidadTipo": evento["entidadTipo"],
        "fechaHora": evento["fechaHora"],
        "hashAnterior": evento["hashAnterior"],
        "numero": evento["numero"],
        "tipo": evento["tipo"],
    }
    partes = []
    for clave in sorted(campos):
        valor = campos[clave]
        if valor is None:
            continue
        if clave == "datos":
            texto = valor  # ya es JSON canónico (se inserta tal cual, como JsonCrudo)
        elif isinstance(valor, int):
            texto = str(valor)
        else:
            texto = texto_json(valor)
        partes.append(texto_json(clave) + ":" + texto)
    return "{" + ",".join(partes) + "}"


def calcular_hash(evento):
    """SHA-256 en hexadecimal minúscula del contenido canónico."""
    return hashlib.sha256(contenido_canonico(evento).encode("utf-8")).hexdigest()


def leer_eventos(base):
    """Todos los eventos, en orden, con los campos que entran al hash."""
    filas = base.ejecutar(
        "SELECT numero, tipo, to_char(fecha_hora, 'YYYY-MM-DD\"T\"HH24:MI:SS.US\"Z\"'), entidad_tipo, entidad_id, "
        "datos_json, coalesce(actor_usuario_id::text, ''), coalesce(actor_empresa_id::text, ''), hash_anterior, hash "
        "FROM eventos_trazabilidad ORDER BY numero;")
    eventos = []
    for fila in filas:
        eventos.append({
            "numero": int(fila[0]), "tipo": fila[1], "fechaHora": fila[2], "entidadTipo": fila[3],
            "entidadId": fila[4], "datos": fila[5], "actorUsuarioId": fila[6] or None,
            "actorEmpresaId": fila[7] or None, "hashAnterior": fila[8], "hash": fila[9],
        })
    return eventos


# ---------- El ataque ----------

def elegir_evento(eventos, ultimo_anclado, numero_pedido):
    """El evento a alterar: el pedido, o el último ya anclado de un tipo con una historia clara."""
    if numero_pedido:
        for evento in eventos:
            if evento["numero"] == numero_pedido:
                return evento
        sys.exit("No existe el evento #%d." % numero_pedido)
    candidatos = [e for e in eventos if ultimo_anclado and e["numero"] <= ultimo_anclado] or eventos
    for tipo in ("RUPTURA_FRIO", "LOTE_REGISTRADO"):
        de_tipo = [e for e in candidatos if e["tipo"] == tipo]
        if de_tipo:
            return de_tipo[-1]
    return candidatos[-1]


def alterar(evento):
    """Cambia un dato del evento; devuelve (datos nuevos en JSON canónico, descripción del cambio)."""
    datos = json.loads(evento["datos"])
    if datos_canonicos(datos) != evento["datos"]:
        sys.exit("No puedo reescribir los datos del evento #%d sin cambiar su formato; elegí otro con --numero."
                 % evento["numero"])
    if evento["tipo"] == "RUPTURA_FRIO" and "temperatura" in datos:
        antes = datos["temperatura"]
        maximas = [b.get("temperaturaMaxima") for b in datos.get("bultosAfectados", []) if b.get("temperaturaMaxima")]
        datos["temperatura"] = maximas[0] if maximas else "8"
        cambio = "temperatura %s °C → %s °C (se oculta la ruptura de frío)" % (antes, datos["temperatura"])
    elif evento["tipo"] == "LOTE_REGISTRADO" and isinstance(datos.get("cantidadSeries"), int):
        antes = datos["cantidadSeries"]
        datos["cantidadSeries"] = antes + 5
        cambio = "cantidadSeries %d → %d (lugar para 5 cajas falsificadas en el lote %s)" % (
            antes, datos["cantidadSeries"], datos.get("codigo", "?"))
    else:
        enteros = [c for c, v in sorted(datos.items()) if isinstance(v, int) and not isinstance(v, bool)]
        textos = [c for c, v in sorted(datos.items()) if isinstance(v, str)]
        if enteros:
            campo = enteros[0]
            cambio = "%s %d → %d" % (campo, datos[campo], datos[campo] + 1)
            datos[campo] += 1
        elif textos:
            campo = textos[0]
            cambio = "%s \"%s\" → \"%s (editado)\"" % (campo, datos[campo], datos[campo])
            datos[campo] += " (editado)"
        else:
            sys.exit("El evento #%d no tiene un dato simple para alterar; elegí otro con --numero." % evento["numero"])
    return datos_canonicos(datos), cambio


def sql_texto(texto):
    """Literal SQL con comillas de dólar (sin problemas con comillas ni barras)."""
    if "$medichain$" in texto:
        sys.exit("Texto con un delimitador reservado; no se modificó nada.")
    return "$medichain$" + texto + "$medichain$"


def main():
    parser = argparse.ArgumentParser(description="Simula el ataque con recálculo de hashes (solo base local).")
    parser.add_argument("--url", default="http://localhost:8080", help="URL base del backend")
    parser.add_argument("--numero", type=int, help="número del evento a alterar (por defecto, uno ya anclado)")
    parser.add_argument("--contenedor", help="contenedor Docker de PostgreSQL (por defecto, el del puerto de DB_URL)")
    args = parser.parse_args()

    faltan = [v for v in ("DB_URL", "DB_USER", "ADMIN_EMAIL", "ADMIN_PASSWORD") if not os.environ.get(v)]
    if faltan:
        sys.exit("Faltan variables de entorno: %s (hacé: set -a; source .env; set +a)" % ", ".join(faltan))
    _, puerto, nombre_base = datos_de_conexion()
    base = Base(args.contenedor or contenedor_del_puerto(puerto), os.environ["DB_USER"], nombre_base)

    # 1. Recalcular toda la cadena con el algoritmo público: tiene que coincidir con la del backend.
    eventos = leer_eventos(base)
    if not eventos:
        sys.exit("La cadena está vacía.")
    anterior = GENESIS
    for evento in eventos:
        if evento["hashAnterior"] != anterior or calcular_hash(evento) != evento["hash"]:
            sys.exit("Mi recálculo no coincide con el backend en el evento #%d: la cadena ya estaba rota o el "
                     "algoritmo cambió. No se modificó nada." % evento["numero"])
        anterior = evento["hash"]
    print("Recalculé los %d hashes de la cadena con el mismo algoritmo del backend: coinciden todos."
          % len(eventos))

    filas = base.ejecutar("SELECT coalesce(max(hasta_numero), 0) FROM registros_blockchain "
                          "WHERE estado IN ('ENVIADO', 'CONFIRMADO');")
    ultimo_anclado = int(filas[0][0]) if filas else 0
    print("Último evento anclado en Sepolia según la base: #%d (de %d)." % (ultimo_anclado, len(eventos)))

    # 2. Elegir y alterar.
    objetivo = elegir_evento(eventos, ultimo_anclado, args.numero)
    datos_nuevos, cambio = alterar(objetivo)
    print("\nEvento a alterar: #%d %s — %s" % (objetivo["numero"], objetivo["tipo"], cambio))
    if objetivo["numero"] > ultimo_anclado:
        print("ATENCIÓN: el evento #%d todavía NO está anclado: la blockchain no va a detectar el cambio (es la "
              "ventana de hasta 5 minutos entre anclajes). Para la demo elegí un número ≤ #%d con --numero."
              % (objetivo["numero"], ultimo_anclado))
    print("Se van a recalcular los hashes del #%d al #%d y cadena_estado." % (objetivo["numero"],
                                                                           eventos[-1]["numero"]))
    if input("Escribí ATACAR para confirmar: ").strip() != "ATACAR":
        sys.exit("Cancelado. No se modificó nada.")

    # 3. Recalcular desde el evento alterado hasta el último. Se copian ANTES el número y el hash
    #    originales del último evento: el control de concurrencia los compara con cadena_estado.
    ultimo_numero_original = eventos[-1]["numero"]
    ultimo_hash_original = eventos[-1]["hash"]
    objetivo["datos"] = datos_nuevos
    anterior = GENESIS
    sentencias = []
    for evento in eventos:
        if evento["numero"] >= objetivo["numero"]:
            evento["hashAnterior"] = anterior
            evento["hash"] = calcular_hash(evento)
            sentencias.append("UPDATE eventos_trazabilidad SET datos_json = %s, hash_anterior = '%s', hash = '%s' "
                              "WHERE numero = %d;" % (sql_texto(evento["datos"]), evento["hashAnterior"],
                                                      evento["hash"], evento["numero"]))
        anterior = evento["hash"]
    sql = "\n".join([
        "BEGIN;",
        # Si la app agregó un evento mientras tanto, no se toca nada.
        "DO $$ BEGIN PERFORM 1 FROM cadena_estado WHERE nombre = 'PRINCIPAL' AND ultimo_numero = %d "
        "AND ultimo_hash = '%s' FOR UPDATE; IF NOT FOUND THEN RAISE EXCEPTION 'La cadena cambió mientras se "
        "preparaba el ataque: no se modificó nada'; END IF; END $$;" % (ultimo_numero_original,
                                                                         ultimo_hash_original),
    ] + sentencias + [
        "UPDATE cadena_estado SET ultimo_hash = '%s' WHERE nombre = 'PRINCIPAL';" % eventos[-1]["hash"],
        "COMMIT;",
    ])
    base.ejecutar(sql)
    print("Listo: evento #%d alterado y %d hashes recalculados; cadena_estado actualizado."
          % (objetivo["numero"], len(sentencias)))

    # 4. La verificación, como la vería la Sede.
    token = iniciar_sesion(args.url.rstrip("/"), os.environ["ADMIN_EMAIL"], os.environ["ADMIN_PASSWORD"])
    respuesta = pedir("GET", args.url.rstrip("/") + "/api/eventos-trazabilidad/verificacion", token=token)
    if respuesta.codigo != 200:
        sys.exit("La verificación respondió %s: %s" % (respuesta.codigo, respuesta.cuerpo))
    resultado = respuesta.cuerpo
    blockchain = resultado.get("blockchain") or {}
    print("\n== Verificación (GET /api/eventos-trazabilidad/verificacion) ==")
    print("Cadena local: %s (%d eventos recalculados)" % ("ÍNTEGRA ✔" if resultado.get("integra") else "ROTA ✘",
                                                          resultado.get("eventosVerificados", 0)))
    estado = blockchain.get("estado")
    if estado == "ALTERADA":
        print("Blockchain:   ALTERADA ✘ entre el evento #%s y el #%s" % (blockchain.get("alteradoDesde"),
                                                                       blockchain.get("alteradoHasta")))
    else:
        print("Blockchain:   %s" % estado)
    print("Motivo:       %s" % blockchain.get("motivo"))
    print("Resumen:      %s" % resultado.get("resumen"))
    if estado == "NO_DISPONIBLE":
        print("\nSin anclaje, nadie se entera de la alteración: para eso está el anclaje en Sepolia.")
    print("\nPara deshacerlo: ./reset-demo.sh, desplegá un contrato nuevo (contracts/GUIA-DESPLIEGUE.md), "
          "actualizá ANCHOR_CONTRACT_ADDRESS y levantá la app.")


if __name__ == "__main__":
    main()
