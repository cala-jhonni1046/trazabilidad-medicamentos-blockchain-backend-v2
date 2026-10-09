#!/usr/bin/env python3
"""
Simulador del sensor de temperatura de un camión (MediChain, solo para demo).

Ejemplos de uso:
  1) Lecturas normales cada 5 segundos para el viaje VJ-0001 (Cuyafen, rango 15–30 °C):
       SENSOR_PASSWORD='...' python3 scripts/simular-sensor.py \
           --email admin@laboratorio-andino.demo --viaje VJ-0001 --intervalo 5 --min 18 --max 24
  2) Tres lecturas y la tercera fuera de rango a 35 °C (ruptura de frío):
       SENSOR_PASSWORD='...' python3 scripts/simular-sensor.py \
           --email admin@laboratorio-andino.demo --viaje VJ-0001 --lecturas 3 --romper-en 3 --temperatura-rotura 35

Qué hace: inicia sesión como la EMPRESA ORIGEN del viaje (la única que puede
reportar telemetría), busca el viaje por su código y manda lecturas al mismo
endpoint real que usaría el sensor (POST /api/telemetria-temperatura y, con
--gps, POST /api/telemetria-gps). El viaje tiene que estar EN_TRANSITO.

La contraseña se lee SOLO de la variable de entorno SENSOR_PASSWORD (nunca de
un argumento, para que no quede en el historial del shell ni en `ps`).

SIMPLIFICACIÓN: en la realidad el sensor tiene su propia credencial de
dispositivo; acá usa la cuenta de un usuario de la empresa origen. Pendiente
para más adelante (credenciales de dispositivo).

Solo biblioteca estándar de Python 3: no requiere instalar nada. Las funciones
HTTP comunes están en scripts/medichain_api.py.
"""
import argparse
import os
import random
import sys
import time
from datetime import datetime, timezone

from medichain_api import buscar_viaje, iniciar_sesion, pedir


def main():
    parser = argparse.ArgumentParser(description="Simulador del sensor de temperatura de un camión (demo).")
    parser.add_argument("--url", default="http://localhost:8080", help="URL base del backend")
    parser.add_argument("--email", required=True, help="Usuario de la empresa ORIGEN del viaje")
    parser.add_argument("--viaje", required=True, help="Código del viaje, por ejemplo VJ-0001")
    parser.add_argument("--sensor", default="SENSOR-DEMO-01", help="Identificador del sensor")
    parser.add_argument("--intervalo", type=float, default=5.0, help="Segundos entre lecturas")
    parser.add_argument("--lecturas", type=int, default=0, help="Cantidad de lecturas (0 = sin fin, Ctrl+C para cortar)")
    parser.add_argument("--min", type=float, default=18.0, help="Temperatura mínima de las lecturas normales")
    parser.add_argument("--max", type=float, default=24.0, help="Temperatura máxima de las lecturas normales")
    parser.add_argument("--romper-en", type=int, default=0, help="Número de lectura que sale fuera de rango (0 = nunca)")
    parser.add_argument("--temperatura-rotura", type=float, default=35.0, help="Temperatura de la lectura forzada")
    parser.add_argument("--gps", action="store_true", help="Mandar también una posición GPS por lectura")
    args = parser.parse_args()

    password = os.environ.get("SENSOR_PASSWORD")
    if not password:
        sys.exit("Falta la variable de entorno SENSOR_PASSWORD (contraseña del usuario de la empresa origen).")

    base = args.url.rstrip("/")
    token = iniciar_sesion(base, args.email, password)
    viaje_id, estado = buscar_viaje(base, token, args.viaje)
    if viaje_id is None:
        sys.exit("No encontré el viaje %s entre los viajes que ve %s" % (args.viaje, args.email))
    print("Viaje %s (%s) — estado %s" % (args.viaje, viaje_id, estado))
    if estado != "EN_TRANSITO":
        print("Aviso: el viaje no está EN_TRANSITO; registrá la salida (POST /api/viajes/{id}/salida) o el backend rechazará las lecturas.")

    numero = 0
    latitud, longitud = -32.8895, -68.8458  # Mendoza (ficticio)
    try:
        while args.lecturas == 0 or numero < args.lecturas:
            numero += 1
            forzada = args.romper_en and numero == args.romper_en
            temperatura = args.temperatura_rotura if forzada else round(random.uniform(args.min, args.max), 2)
            lectura = {
                "sensorId": args.sensor,
                "temperatura": temperatura,
                "fechaHora": datetime.now(timezone.utc).replace(microsecond=0).isoformat(),
                "despachoId": viaje_id,
            }
            respuesta = pedir("POST", base + "/api/telemetria-temperatura", lectura, token)
            if respuesta.codigo == 201:
                marca = "FUERA DE RANGO" if respuesta.campo("fueraDeRango") else "ok"
                print("#%d  %.2f °C  → %s%s" % (numero, temperatura, marca, "  (forzada)" if forzada else ""))
            else:
                print("#%d  %.2f °C  → HTTP %s: %s" % (numero, temperatura, respuesta.codigo, respuesta.cuerpo))
            if args.gps:
                latitud += 0.01
                longitud += 0.01
                pedir("POST", base + "/api/telemetria-gps", {
                    "sensorId": args.sensor, "latitud": round(latitud, 5), "longitud": round(longitud, 5),
                    "fechaHora": lectura["fechaHora"], "despachoId": viaje_id}, token)
            if args.lecturas == 0 or numero < args.lecturas:
                time.sleep(args.intervalo)
    except KeyboardInterrupt:
        print("\nSimulación detenida.")


if __name__ == "__main__":
    main()
