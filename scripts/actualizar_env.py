#!/usr/bin/env python3
"""
Cambia SOLO la línea ANCHOR_CONTRACT_ADDRESS de un archivo .env, con copia de seguridad.

Lo usa scripts/nuevo-contrato.sh después de desplegar un contrato nuevo. Garantías:
  - Hace una copia de seguridad del archivo (<archivo>.bak-AAAAMMDD-HHMMSS, con permisos 600) ANTES de tocar nada.
  - Cambia únicamente esa línea (si no existe, la agrega al final); las demás quedan byte a byte iguales.
  - Comprueba el resultado: respecto de la copia difiere UNA sola línea. Si no, restaura la copia y falla.
  - Solo la dirección (pública) se imprime; nunca otra línea del archivo (el .env tiene secretos).

Uso: actualizar_env.py ARCHIVO NUEVA_DIRECCION      (solo biblioteca estándar)
"""
import os
import re
import shutil
import sys
from datetime import datetime

CLAVE = "ANCHOR_CONTRACT_ADDRESS"
DIRECCION = re.compile(r"^0x[0-9a-fA-F]{40}$")


def direccion_actual(texto):
    """Valor de ANCHOR_CONTRACT_ADDRESS en el texto del .env, o None si no está (sin comillas ni espacios)."""
    for linea in texto.splitlines():
        if linea.startswith(CLAVE + "="):
            return linea[len(CLAVE) + 1:].strip().strip('"').strip("'")
    return None


def reemplazar(texto, nueva):
    """Texto con la línea de la clave cambiada (o agregada al final); el resto, igual."""
    lineas = texto.splitlines(keepends=True)
    nuevas, encontrada = [], False
    for linea in lineas:
        if linea.startswith(CLAVE + "="):
            fin = linea[len(linea.rstrip("\r\n")):]  # conserva el final de línea original (\n, \r\n o ninguno)
            nuevas.append("%s=%s%s" % (CLAVE, nueva, fin))
            encontrada = True
        else:
            nuevas.append(linea)
    if not encontrada:
        if nuevas and not nuevas[-1].endswith("\n"):
            nuevas[-1] += "\n"
        nuevas.append("%s=%s\n" % (CLAVE, nueva))
    return "".join(nuevas)


def actualizar(ruta, nueva, ahora=None):
    """Actualiza el archivo; devuelve (direccion_vieja_o_None, ruta_de_la_copia). Lanza ValueError si algo no cierra."""
    if not DIRECCION.match(nueva):
        raise ValueError("la dirección nueva no tiene el formato 0x + 40 hexadecimales")
    with open(ruta, encoding="utf-8", newline="") as archivo:
        original = archivo.read()
    if len(re.findall(r"^%s=" % CLAVE, original, flags=re.MULTILINE)) > 1:
        raise ValueError("%s aparece más de una vez en el archivo: revisalo a mano" % CLAVE)
    vieja = direccion_actual(original)
    sello = (ahora or datetime.now()).strftime("%Y%m%d-%H%M%S")
    copia = "%s.bak-%s" % (ruta, sello)
    shutil.copyfile(ruta, copia)
    os.chmod(copia, 0o600)
    nuevo = reemplazar(original, nueva)
    with open(ruta, "w", encoding="utf-8", newline="") as archivo:
        archivo.write(nuevo)
    # Comprobación: respecto de la copia, una sola línea cambió (o se agregó) y es la de la clave.
    a, b = original.splitlines(), nuevo.splitlines()
    distintas = [l for l in b if l not in a]
    quitadas = [l for l in a if l not in b]
    if len(distintas) != 1 or not distintas[0].startswith(CLAVE + "=") or len(quitadas) > 1 \
            or any(not q.startswith(CLAVE + "=") for q in quitadas):
        shutil.copyfile(copia, ruta)
        raise ValueError("el cambio tocaba más que la línea de %s: se restauró el archivo original" % CLAVE)
    return vieja, copia


def main(argumentos):
    if len(argumentos) != 2:
        print(__doc__.strip().splitlines()[-1], file=sys.stderr)
        return 2
    ruta, nueva = argumentos
    try:
        vieja, copia = actualizar(ruta, nueva)
    except (OSError, ValueError) as error:
        print("ERROR: %s" % error, file=sys.stderr)
        return 1
    print("%s: dirección vieja %s" % (CLAVE, vieja or "(no estaba)"))
    print("%s: dirección nueva %s" % (CLAVE, nueva))
    print("Copia de seguridad: %s" % copia)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
