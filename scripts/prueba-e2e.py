#!/usr/bin/env python3
"""
Prueba de punta a punta (end-to-end) de MediChain por la API real.

Recorre el flujo del proyecto sobre una base RECIÉN RESETEADA con la demo
cargada, y verifica cada resultado (código HTTP y datos). Reemplaza las
pruebas en vivo que se hacían a mano en Swagger.

Ejemplos de uso (desde la raíz del repo):
  1) Contra tu app local (después de ./reset-demo.sh y ./levantar.sh):
       set -a; source .env; set +a
       python3 scripts/prueba-e2e.py
  2) Contra otra instancia:
       DEMO_PASSWORD='...' ADMIN_EMAIL='...' ADMIN_PASSWORD='...' \
           python3 scripts/prueba-e2e.py --url http://localhost:8099
  3) Con anclaje real en Sepolia (--sepolia): la app levantada con
     ANCLAJE_HABILITADO=true y un contrato RECIÉN desplegado para esta base
     (scripts/desplegar-contrato.sh). Gasta un anclaje (~0.0001 SepoliaETH).

Contraseñas: SOLO desde variables de entorno (DEMO_PASSWORD, ADMIN_EMAIL,
ADMIN_PASSWORD), nunca en el código ni como argumentos.

Salida: una línea por verificación (✔ / ✘, paso, descripción, HTTP
esperado y obtenido); al final "N de M verificaciones OK".
Código de salida: 0 si todo pasó, 1 si algo falló, 2 si la base no está
recién reseteada (no se ejecuta nada).

Para agregar un paso nuevo (8…): escribir una función seccion_XX(ctx)
y sumarla a la lista SECCIONES al final del archivo.

Solo biblioteca estándar de Python 3.
"""
import argparse
import os
import re
import sys
import time
from datetime import datetime

from medichain_api import buscar_viaje, iniciar_sesion, pedir, pedir_multipart

# ---------- Datos fijos de la demo (DatosDemo) y de la prueba ----------

EMAIL_INSPECTOR = "inspector.mendoza@medichain.demo"
EMAIL_LABORATORIO = "admin@laboratorio-andino.demo"
EMAIL_DISTRIBUIDORA = "admin@distribuidora-piedemonte.demo"
EMAIL_FARMACIA = "admin@farmacia-losalamos.demo"
EMAIL_FARMACIA_DOS = "admin@farmacia-cerroarco.demo"
EMAIL_PACIENTE = "paciente@medichain.demo"
CUIT_DISTRIBUIDORA = "30-71000002-2"
CUIT_FARMACIA_DOS = "30-71000004-9"

GTIN_CUYAFEN = "07799000001010"

# Datos de paciente FICTICIOS de la prueba (para verificar que nunca salen de la dispensación, R13).
DNI_E2E = "30111006"
RECETA_E2E = "REC-E2E-0001"
OBRA_SOCIAL_E2E = "OSEP-E2E"
AFILIADO_E2E = "AF-E2E-123"

# Paciente nuevo y textos ficticios de 7f (para verificar que no llegan a la cadena, R13).
EMAIL_PACIENTE_NUEVO = "paciente.e2e@medichain.demo"
DESCRIPCION_REPORTE_E2E = "Soy Pedro Gómez, DNI 30111098; la caja llegó aplastada"
CONCLUSION_E2E = "Serie inexistente: se informa a la fiscalía"

# Farmacia nueva que registra la prueba (CUIT y GLN válidos que la demo no usa).
CUIT_FARMACIA_NUEVA = "30-71000006-5"
GLN_FARMACIA_NUEVA = "7799000000068"
EMAIL_FARMACIA_NUEVA = "admin@farmacia-e2e.demo"

# Inspector que la prueba da de alta, de baja y reactiva (paso 9: token de una cuenta desactivada).
EMAIL_INSPECTOR_BAJA = "inspector.baja.e2e@medichain.demo"


class SeccionInterrumpida(Exception):
    """Falta un dato imprescindible para seguir con la sección (por ejemplo un id)."""


class Contexto:
    """Estado compartido de la corrida: URL, tokens, contadores y datos entre secciones."""

    def __init__(self, base):
        self.base = base
        self.tokens = {}
        self.total = 0
        self.ok = 0
        self.datos = {}

    def url(self, ruta):
        """URL completa de una ruta de la API."""
        return self.base + ruta

    def token(self, email, password):
        """Token del usuario (inicia sesión una sola vez por usuario)."""
        if email not in self.tokens:
            self.tokens[email] = iniciar_sesion(self.base, email, password)
        return self.tokens[email]

    def verificar(self, paso, descripcion, esperado, respuesta, condicion=True, detalle=None):
        """
        Registra una verificación: el código HTTP es el esperado y la condición
        extra se cumple. Imprime ✔ / ✘; si falla, muestra el body (o el detalle).
        Devuelve True si pasó.
        """
        self.total += 1
        obtenido = respuesta.codigo if respuesta is not None else "-"
        paso_ok = (respuesta is None or respuesta.codigo == esperado) and bool(condicion)
        marca = "✔" if paso_ok else "✘"
        if respuesta is None:
            # Verificación de datos ya obtenidos por requests anteriores (no hay un HTTP propio).
            print("%s [%s] %s (verificación de datos)" % (marca, paso, descripcion))
        else:
            print("%s [%s] %s (esperado %s, obtenido %s)" % (marca, paso, descripcion, esperado, obtenido))
        if paso_ok:
            self.ok += 1
        else:
            cuerpo = detalle if detalle is not None else (respuesta.cuerpo if respuesta is not None else "")
            print("      body: %s" % str(cuerpo)[:600])
        return paso_ok


def contenido(respuesta):
    """Lista 'content' de una página, o lista vacía."""
    return respuesta.cuerpo.get("content", []) if isinstance(respuesta.cuerpo, dict) else []


def buscar(lista, clave, valor):
    """Primer elemento de la lista con lista[clave] == valor, o None."""
    for elemento in lista:
        if elemento.get(clave) == valor:
            return elemento
    return None


def exigir(valor, mensaje):
    """Si falta un dato imprescindible, interrumpe la sección con un mensaje claro."""
    if valor is None:
        raise SeccionInterrumpida(mensaje)
    return valor


# ---------- Precondición: base recién reseteada ----------

def base_recien_reseteada(ctx, demo):
    """Verifica que la base esté como la deja la demo; devuelve la lista de problemas (vacía = OK)."""
    problemas = []
    lab = ctx.token(EMAIL_LABORATORIO, demo)
    _, estado = buscar_viaje(ctx.base, lab, "VJ-0001")
    if estado != "PROGRAMADO":
        problemas.append("VJ-0001 debería estar PROGRAMADO (está %s)" % estado)
    circuitos = contenido(pedir("GET", ctx.url("/api/circuitos?size=100"), token=lab))
    cir2 = buscar(circuitos, "codigo", "CIR-0002")
    if cir2 is None or cir2["estado"] != "PENDIENTE_EMPRESAS":
        problemas.append("CIR-0002 debería estar PENDIENTE_EMPRESAS")
    lotes = contenido(pedir("GET", ctx.url("/api/lotes?size=100"), token=lab))
    if buscar(lotes, "codigo", "L2026-0003") is not None:
        problemas.append("ya existe el lote L2026-0003 (la prueba ya se corrió)")
    return problemas


# ---------- Secciones (una por paso del proyecto) ----------

def seccion_5b_permisos(ctx, demo, admin):
    """5b: filtrado por empresa y rol, y tokens inválidos."""
    lab = ctx.token(EMAIL_LABORATORIO, demo)
    farmacia = ctx.token(EMAIL_FARMACIA, demo)
    paciente = ctx.token(EMAIL_PACIENTE, demo)
    lote = exigir(buscar(contenido(pedir("GET", ctx.url("/api/lotes?size=100"), token=lab)), "codigo", "L2026-0001"),
                  "no encontré el lote L2026-0001 de la demo")

    r = pedir("GET", ctx.url("/api/lotes"), token=farmacia)
    ctx.verificar("5b.1", "farmacia lista lotes → vacío (ninguno salió hacia ella)", 200, r,
                  r.campo("totalElements") == 0)
    r = pedir("GET", ctx.url("/api/lotes/" + lote["id"]), token=farmacia)
    ctx.verificar("5b.2", "farmacia pide un lote ajeno por id → 404", 404, r)
    r = pedir("GET", ctx.url("/api/dispensaciones"), token=paciente)
    ctx.verificar("5b.3", "paciente pide dispensaciones → 403", 403, r)
    r = pedir("GET", ctx.url("/api/empresas"), encabezado_auth="Bearer esto.no.es-un-jwt")
    ctx.verificar("5b.4", "token mal formado → 401", 401, r)


def seccion_6_cadena(ctx, demo, admin):
    """6: la cadena de eventos de la demo está íntegra."""
    sede = ctx.token(admin[0], admin[1])
    r = pedir("GET", ctx.url("/api/eventos-trazabilidad/verificacion"), token=sede)
    ctx.verificar("6.1", "cadena de la demo íntegra (%s eventos)" % r.campo("eventosVerificados"), 200, r,
                  r.campo("integra") is True)


def seccion_7a_empresas(ctx, demo, admin):
    """7a: registro público de una farmacia con PDF y habilitación por el inspector."""
    pdf = b"%PDF-1.4\n% MediChain prueba e2e\n%%EOF\n"
    campos = {
        "tipo": "FARMACIA", "cuit": CUIT_FARMACIA_NUEVA, "razonSocial": "Farmacia E2E", "gln": GLN_FARMACIA_NUEVA,
        "provincia": "MENDOZA", "localidad": "Mendoza", "domicilio": "Calle de Prueba 1",
        "adminEmail": EMAIL_FARMACIA_NUEVA, "adminPassword": demo, "adminNombre": "Prueba",
        "adminApellido": "Automatica", "adminDni": "30111099",
    }
    r = pedir_multipart(ctx.url("/api/registro/empresas"), campos, {"documento": ("habilitacion.pdf", pdf, "application/pdf")})
    ctx.verificar("7a.1", "registro público de farmacia con PDF → 201 PENDIENTE", 201, r,
                  r.campo("estado") == "PENDIENTE" and r.campo("identificador") == CUIT_FARMACIA_NUEVA)

    inspector = ctx.token(EMAIL_INSPECTOR, demo)
    r = pedir("GET", ctx.url("/api/empresas/bandeja?size=100"), token=inspector)
    empresa = buscar(contenido(r), "cuit", CUIT_FARMACIA_NUEVA)
    ctx.verificar("7a.2", "la farmacia nueva aparece en la bandeja del inspector", 200, r, empresa is not None)
    empresa_id = exigir(empresa, "la farmacia nueva no está en la bandeja")["id"]

    r = pedir("POST", ctx.url("/api/empresas/%s/habilitar" % empresa_id), token=inspector)
    ctx.verificar("7a.3", "habilitar sin tomar → 409 TRANSICION_INVALIDA", 409, r,
                  r.campo("regla") == "TRANSICION_INVALIDA")
    r = pedir("POST", ctx.url("/api/empresas/%s/tomar" % empresa_id), token=inspector)
    ctx.verificar("7a.4", "tomar la solicitud → 200", 200, r)
    r = pedir("POST", ctx.url("/api/empresas/%s/habilitar" % empresa_id), token=inspector)
    ctx.verificar("7a.5", "habilitar → 200 HABILITADA", 200, r, r.campo("estado") == "HABILITADA")


def seccion_7b_circuitos(ctx, demo, admin):
    """7b: aceptación de CIR-0002 por las dos empresas, aprobación y par vigente duplicado."""
    distribuidora = ctx.token(EMAIL_DISTRIBUIDORA, demo)
    r = pedir("GET", ctx.url("/api/circuitos/pendientes-aceptacion?size=100"), token=distribuidora)
    circuito = exigir(buscar(contenido(r), "codigo", "CIR-0002"), "CIR-0002 no está pendiente para Piedemonte")
    circuito_id = circuito["id"]

    r = pedir("POST", ctx.url("/api/circuitos/%s/aceptar" % circuito_id), token=distribuidora)
    ctx.verificar("7b.1", "Piedemonte acepta CIR-0002 → sigue PENDIENTE_EMPRESAS", 200, r,
                  r.campo("estado") == "PENDIENTE_EMPRESAS")
    r = pedir("POST", ctx.url("/api/circuitos/%s/aceptar" % circuito_id), token=ctx.token(EMAIL_FARMACIA_DOS, demo))
    ctx.verificar("7b.2", "Cerro Arco acepta CIR-0002 → PENDIENTE_INSPECTOR", 200, r,
                  r.campo("estado") == "PENDIENTE_INSPECTOR")

    inspector = ctx.token(EMAIL_INSPECTOR, demo)
    r = pedir("POST", ctx.url("/api/circuitos/%s/aprobar" % circuito_id), token=inspector)
    ctx.verificar("7b.3", "aprobar sin tomar → 409 TRANSICION_INVALIDA", 409, r,
                  r.campo("regla") == "TRANSICION_INVALIDA")
    r = pedir("POST", ctx.url("/api/circuitos/%s/tomar" % circuito_id), token=inspector)
    ctx.verificar("7b.4", "tomar CIR-0002 → 200", 200, r)
    r = pedir("POST", ctx.url("/api/circuitos/%s/aprobar" % circuito_id), token=inspector)
    ctx.verificar("7b.5", "aprobar CIR-0002 → APROBADO", 200, r, r.campo("estado") == "APROBADO")

    r = pedir("POST", ctx.url("/api/circuitos"), {"cuitDistribuidor": CUIT_DISTRIBUIDORA,
                                                   "cuitFarmacia": CUIT_FARMACIA_DOS},
              token=ctx.token(EMAIL_LABORATORIO, demo))
    ctx.verificar("7b.6", "proponer el mismo par laboratorio–farmacia → 409 R5", 409, r, r.campo("regla") == "R5")


def seccion_7c_lotes(ctx, demo, admin):
    """7c: lote con series generadas, intento con series inválidas y liberación (DT e inspector)."""
    lab = ctx.token(EMAIL_LABORATORIO, demo)
    medicamentos = contenido(pedir("GET", ctx.url("/api/medicamentos?size=100"), token=lab))
    cuyafen = exigir(buscar(medicamentos, "nombreComercial", "Cuyafen"), "no encontré Cuyafen")["id"]

    r = pedir("POST", ctx.url("/api/lotes"), {"codigo": "L2026-0003", "fechaFabricacion": "2026-09-01",
                                               "fechaVencimiento": "2028-09-01", "medicamentoId": cuyafen,
                                               "cantidad": 50}, token=lab)
    ctx.verificar("7c.1", "lote L2026-0003 con 50 series generadas → 201", 201, r,
                  r.campo("cantidad") == 50 and r.campo("estado") == "PENDIENTE_LIBERACION")
    lote_id = exigir(r.campo("id"), "no se creó el lote L2026-0003")

    r = pedir("GET", ctx.url("/api/lotes/%s/unidades?size=100" % lote_id), token=lab)
    cajas = contenido(r)
    ctx.verificar("7c.2", "sus 50 cajas, la primera L20260003S000001", 200, r,
                  r.campo("totalElements") == 50 and cajas and cajas[0]["serie"] == "L20260003S000001")

    r = pedir("POST", ctx.url("/api/lotes"), {"codigo": "L2026-0004", "fechaFabricacion": "2026-09-01",
                                               "fechaVencimiento": "2028-09-01", "medicamentoId": cuyafen,
                                               "series": ["OK0001", "779000123", "L20260001S000001"]}, token=lab)
    ctx.verificar("7c.3", "lote con una serie que empieza con 779 y otra existente → 409 R3", 409, r,
                  r.campo("regla") == "R3")
    sede = ctx.token(admin[0], admin[1])
    eventos = contenido(pedir("GET", ctx.url("/api/eventos-trazabilidad?size=100"), token=sede))
    intento = [e for e in eventos if e["tipo"] == "INTENTO_SERIE_INVALIDA" and '"L2026-0004"' in e["datosJson"]]
    r = pedir("GET", ctx.url("/api/lotes?size=100"), token=lab)
    ctx.verificar("7c.4", "INTENTO_SERIE_INVALIDA quedó en la cadena y L2026-0004 no existe", 200, r,
                  len(intento) == 1 and buscar(contenido(r), "codigo", "L2026-0004") is None,
                  detalle="intentos encontrados: %d" % len(intento))

    r = pedir("POST", ctx.url("/api/lotes/%s/liberar" % lote_id), token=lab)
    ctx.verificar("7c.5", "el DT libera L2026-0003 → LIBERADO", 200, r, r.campo("estado") == "LIBERADO")

    lotes = contenido(pedir("GET", ctx.url("/api/lotes?size=100"), token=lab))
    andivax = exigir(buscar(lotes, "codigo", "L2026-0002"), "no encontré el lote L2026-0002 (Andivax)")["id"]
    r = pedir("POST", ctx.url("/api/lotes/%s/liberar" % andivax), token=lab)
    ctx.verificar("7c.6", "el DT intenta liberar Andivax (biológico) → 409 R4", 409, r, r.campo("regla") == "R4")

    inspector = ctx.token(EMAIL_INSPECTOR, demo)
    r = pedir("GET", ctx.url("/api/lotes/bandeja-liberacion?size=100"), token=inspector)
    ctx.verificar("7c.7", "L2026-0002 está en la bandeja de liberación del inspector", 200, r,
                  buscar(contenido(r), "id", andivax) is not None)
    r = pedir("POST", ctx.url("/api/lotes/%s/liberar" % andivax), token=inspector)
    ctx.verificar("7c.8", "el inspector libera Andivax → LIBERADO", 200, r, r.campo("estado") == "LIBERADO")


def seccion_7d_viajes(ctx, demo, admin):
    """7d: salida de VJ-0001, ruptura de frío solo sobre BUL-0001 sin duplicar, y viaje para BUL-0002."""
    lab = ctx.token(EMAIL_LABORATORIO, demo)
    viaje_id, _ = buscar_viaje(ctx.base, lab, "VJ-0001")
    exigir(viaje_id, "no encontré VJ-0001")
    bultos = contenido(pedir("GET", ctx.url("/api/bultos?size=100"), token=lab))
    bulto_uno = exigir(buscar(bultos, "codigo", "BUL-0001"), "no encontré BUL-0001")["id"]

    r = pedir("POST", ctx.url("/api/viajes/%s/salida" % viaje_id), token=lab)
    ctx.verificar("7d.1", "salida de VJ-0001 → EN_TRANSITO", 200, r, r.campo("estado") == "EN_TRANSITO")

    def lectura(temperatura):
        return pedir("POST", ctx.url("/api/telemetria-temperatura"), {
            "sensorId": "SENSOR-E2E", "temperatura": temperatura,
            "fechaHora": datetime.now().replace(microsecond=0).isoformat(), "despachoId": viaje_id}, token=lab)

    def cuarentenas_ruptura():
        """Devuelve (respuesta del listado, cuarentenas con motivo RUPTURA_FRIO)."""
        respuesta = pedir("GET", ctx.url("/api/cuarentenas?size=100"), token=lab)
        return respuesta, [c for c in contenido(respuesta) if c["motivo"] == "RUPTURA_FRIO"]

    r = lectura(20)
    ctx.verificar("7d.2", "lectura normal 20 °C → 201 en rango", 201, r, r.campo("fueraDeRango") is False)
    r = lectura(35)
    ctx.verificar("7d.3", "lectura 35 °C → 201 fuera de rango", 201, r, r.campo("fueraDeRango") is True)
    listado, rupturas = cuarentenas_ruptura()
    ctx.verificar("7d.4", "una cuarentena DESPACHO RUPTURA_FRIO solo sobre BUL-0001", 200, listado,
                  len(rupturas) == 1 and rupturas[0]["alcance"] == "DESPACHO"
                  and rupturas[0]["bultoIds"] == [bulto_uno],
                  detalle=rupturas)
    r = lectura(40)
    _, rupturas = cuarentenas_ruptura()
    ctx.verificar("7d.5", "segunda lectura fuera de rango (40 °C) → sin cuarentena duplicada", 201, r,
                  r.campo("fueraDeRango") is True and len(rupturas) == 1, detalle=rupturas)

    r = pedir("POST", ctx.url("/api/viajes"), {"patente": "AB123CD", "chofer": "Chofer E2E",
                                                "fechaEstimadaEntrega": "2026-12-01T18:00:00",
                                                "bultos": ["BUL-0002"]}, token=lab)
    ctx.verificar("7d.6", "viaje para BUL-0002 → 201 PROGRAMADO (sin salida)", 201, r,
                  r.campo("estado") == "PROGRAMADO" and r.campo("bultoCodigos") == ["BUL-0002"])


def eventos_de_tipo(ctx, admin, tipo):
    """Eventos de la cadena del tipo dado (de los últimos 200), como lista de dicts."""
    sede = ctx.token(admin[0], admin[1])
    eventos = contenido(pedir("GET", ctx.url("/api/eventos-trazabilidad?size=100&page=0"), token=sede))
    eventos += contenido(pedir("GET", ctx.url("/api/eventos-trazabilidad?size=100&page=1"), token=sede))
    return [e for e in eventos if e["tipo"] == tipo]


def seccion_7e_recepcion_dispensacion(ctx, demo, admin):
    """7e: recepción (R8), viaje de tramo 2, dispensación (R11, R13), devolución y verificación pública."""
    lab = ctx.token(EMAIL_LABORATORIO, demo)
    distribuidora = ctx.token(EMAIL_DISTRIBUIDORA, demo)
    farmacia = ctx.token(EMAIL_FARMACIA, demo)

    def recibir(token, codigo, precinto=True, cantidad=10, temperatura=20):
        return pedir("POST", ctx.url("/api/recepciones"), {"codigoBulto": codigo, "precintoIntacto": precinto,
                                                         "cantidadVerificada": cantidad, "temperatura": temperatura},
                     token=token)

    def verificar(serie, gtin=GTIN_CUYAFEN):
        return pedir("GET", ctx.url("/api/verificacion?gtin=%s&serie=%s" % (gtin, serie)))

    def estado_viaje(codigo):
        return buscar_viaje(ctx.base, lab, codigo)[1]

    # --- Tramo 1: salida de VJ-0002 y recepción de BUL-0002 en Piedemonte ---
    vj2, _ = buscar_viaje(ctx.base, lab, "VJ-0002")
    exigir(vj2, "no encontré VJ-0002 (lo crea la sección 7d)")
    r = pedir("POST", ctx.url("/api/viajes/%s/salida" % vj2), token=lab)
    ctx.verificar("7e.1", "salida de VJ-0002 → EN_TRANSITO", 200, r, r.campo("estado") == "EN_TRANSITO")

    r = recibir(farmacia, "BUL-0002")
    ctx.verificar("7e.2", "Los Álamos intenta recibir BUL-0002 en el tramo 1 (no es la destino) → 404", 404, r)

    r = recibir(distribuidora, "BUL-9999")
    inexistentes = eventos_de_tipo(ctx, admin, "BULTO_INEXISTENTE")
    ctx.verificar("7e.3", "Piedemonte escanea BUL-9999 → 404 y BULTO_INEXISTENTE en la cadena", 404, r,
                  len(inexistentes) == 1, detalle="eventos BULTO_INEXISTENTE: %d" % len(inexistentes))

    r = recibir(distribuidora, "BUL-0002")
    ctx.verificar("7e.4", "Piedemonte recibe BUL-0002 conforme → 201", 201, r, r.campo("conforme") is True)
    ctx.verificar("7e.5", "VJ-0002 quedó FINALIZADO (no quedan bultos en curso)", 200, None,
                  estado_viaje("VJ-0002") == "FINALIZADO", detalle="estado %s" % estado_viaje("VJ-0002"))

    r = recibir(distribuidora, "BUL-0002")
    duplicados = eventos_de_tipo(ctx, admin, "BULTO_DUPLICADO")
    ctx.verificar("7e.6", "recibir BUL-0002 otra vez → 409 TRANSICION_INVALIDA y BULTO_DUPLICADO", 409, r,
                  r.campo("regla") == "TRANSICION_INVALIDA" and len(duplicados) == 1,
                  detalle=(r.cuerpo, "eventos BULTO_DUPLICADO: %d" % len(duplicados)))

    # --- BUL-0001 (ruptura de frío en 7d): se rechaza automáticamente y VJ-0001 termina ---
    r = recibir(distribuidora, "BUL-0001")
    ctx.verificar("7e.7", "Piedemonte recibe BUL-0001 (bloqueado) → RECHAZADO automático, motivo BULTO_BLOQUEADO", 201, r,
                  r.campo("conforme") is False and "BULTO_BLOQUEADO" in (r.campo("motivoRechazo") or ""))
    finalizados = [e for e in eventos_de_tipo(ctx, admin, "VIAJE_FINALIZADO") if '"VJ-0001"' in e["datosJson"]]
    ctx.verificar("7e.8", "VJ-0001 FINALIZADO con resumen de temperatura (3 lecturas, 2 fuera de rango)", 200, None,
                  estado_viaje("VJ-0001") == "FINALIZADO" and len(finalizados) == 1
                  and '"lecturas":3' in finalizados[0]["datosJson"] and '"fueraDeRango":2' in finalizados[0]["datosJson"],
                  detalle=finalizados)

    # --- Tramo 2: Piedemonte → Los Álamos ---
    r = pedir("POST", ctx.url("/api/viajes"), {"patente": "CD456EF", "chofer": "Chofer E2E Tramo 2",
                                                "fechaEstimadaEntrega": "2026-12-02T18:00:00",
                                                "bultos": ["BUL-0002"]}, token=distribuidora)
    ctx.verificar("7e.9", "Piedemonte crea el viaje de tramo 2 con BUL-0002 → 201", 201, r,
                  r.campo("tramo") == "DISTRIBUIDOR_A_FARMACIA")
    vj3 = exigir(r.campo("id"), "no se creó el viaje de tramo 2")
    r = pedir("POST", ctx.url("/api/viajes/%s/salida" % vj3), token=distribuidora)
    ctx.verificar("7e.10", "salida del viaje de tramo 2 → EN_TRANSITO", 200, r, r.campo("estado") == "EN_TRANSITO")

    r = recibir(farmacia, "BUL-0002", temperatura=22)
    ctx.verificar("7e.11", "Los Álamos recibe BUL-0002 conforme → 201", 201, r, r.campo("conforme") is True)
    r = pedir("GET", ctx.url("/api/unidades-trazables?size=100"), token=farmacia)
    en_stock = [u for u in contenido(r) if u["estado"] == "EN_STOCK"]
    ctx.verificar("7e.12", "Los Álamos tiene las 10 cajas EN_STOCK", 200, r, len(en_stock) == 10,
                  detalle="EN_STOCK: %d" % len(en_stock))

    # --- Verificación pública y dispensación ---
    serie = "L20260001S000011"
    r = verificar(serie)
    anclaje_esperado = "PENDIENTE" if ctx.datos.get("sepolia") else "NO_DISPONIBLE"
    ctx.verificar("7e.13", "verificación pública (sin token) de %s → APTA, anclaje %s" % (serie, anclaje_esperado),
                  200, r, r.campo("estado") == "APTA" and (r.campo("anclaje") or {}).get("estado") == anclaje_esperado
                  and len(r.campo("recorrido") or []) >= 5)

    r = pedir("POST", ctx.url("/api/dispensaciones"), {"gtin": GTIN_CUYAFEN, "serie": serie, "particular": False,
                                                        "obraSocial": OBRA_SOCIAL_E2E, "numeroAfiliado": AFILIADO_E2E,
                                                        "numeroReceta": RECETA_E2E, "dni": DNI_E2E}, token=farmacia)
    ctx.verificar("7e.14", "dispensar → 201 con el DNI SOLO enmascarado (R13)", 201, r,
                  r.campo("dniEnmascarado") == "*****006" and DNI_E2E not in str(r.cuerpo))
    dispensacion_id = r.campo("id")
    dispensaciones = eventos_de_tipo(ctx, admin, "DISPENSACION")
    textos = " ".join(e["datosJson"] for e in dispensaciones)
    ctx.verificar("7e.15", "el evento DISPENSACION no tiene DNI, receta, obra social ni afiliado (R13)", 200, None,
                  len(dispensaciones) == 1 and all(x not in textos for x in (DNI_E2E, RECETA_E2E, OBRA_SOCIAL_E2E, AFILIADO_E2E)),
                  detalle=textos)

    r = verificar(serie)
    ctx.verificar("7e.16", "verificación → YA_DISPENSADA, sin datos del paciente ni de la dispensación", 200, r,
                  r.campo("estado") == "YA_DISPENSADA"
                  and all(x not in str(r.cuerpo) for x in (DNI_E2E, "*****006", RECETA_E2E, OBRA_SOCIAL_E2E, AFILIADO_E2E)))

    r = pedir("POST", ctx.url("/api/dispensaciones"), {"gtin": GTIN_CUYAFEN, "serie": serie, "particular": True,
                                                        "numeroReceta": "REC-OTRA"}, token=farmacia)
    duplicados = eventos_de_tipo(ctx, admin, "INTENTO_DUPLICADO")
    ctx.verificar("7e.17", "dispensar la misma caja otra vez → 409 R11 e INTENTO_DUPLICADO", 409, r,
                  r.campo("regla") == "R11" and len(duplicados) == 1)

    r = pedir("POST", ctx.url("/api/dispensaciones/%s/anular" % exigir(dispensacion_id, "no se creó la dispensación")),
              {"motivo": "Error de carga en la prueba"}, token=farmacia)
    ctx.verificar("7e.18", "anular la dispensación → 200 y la caja vuelve a APTA", 200, r,
                  r.campo("anulada") is True and verificar(serie).campo("estado") == "APTA")
    r = pedir("POST", ctx.url("/api/dispensaciones"), {"gtin": GTIN_CUYAFEN, "serie": serie, "particular": True,
                                                        "numeroReceta": "REC-E2E-0002"}, token=farmacia)
    ctx.verificar("7e.19", "dispensar de nuevo como particular → 201", 201, r, r.campo("particular") is True)

    # --- Devolución (R14) ---
    devuelta = "L20260001S000012"
    r = pedir("POST", ctx.url("/api/unidades-trazables/devolucion"), {"gtin": GTIN_CUYAFEN, "serie": devuelta,
                                                                        "motivo": "DANADA",
                                                                        "observacion": "Caja aplastada"}, token=farmacia)
    ctx.verificar("7e.20", "devolver %s (DANADA) → DEVUELTA" % devuelta, 200, r, r.campo("estado") == "DEVUELTA")
    r = pedir("POST", ctx.url("/api/dispensaciones"), {"gtin": GTIN_CUYAFEN, "serie": devuelta, "particular": True,
                                                        "numeroReceta": "REC-X"}, token=farmacia)
    ctx.verificar("7e.21", "una caja DEVUELTA no se dispensa → 409", 409, r, r.campo("regla") == "TRANSICION_INVALIDA")
    r = verificar(devuelta)
    ctx.verificar("7e.22", "verificación de la caja devuelta → BLOQUEADA", 200, r, r.campo("estado") == "BLOQUEADA")

    # --- Otros estados de la verificación pública ---
    r = verificar("L20260001S000001")
    ctx.verificar("7e.23", "caja de BUL-0001 (rechazado) → BLOQUEADA", 200, r, r.campo("estado") == "BLOQUEADA")
    r = verificar("L20260003S000001")
    ctx.verificar("7e.24", "caja de L2026-0003 todavía en el laboratorio → EN_DISTRIBUCION", 200, r,
                  r.campo("estado") == "EN_DISTRIBUCION")
    r1 = verificar("FALSA00001")
    r2 = verificar("FALSA00001")
    inexistentes = [e for e in eventos_de_tipo(ctx, admin, "SERIE_INEXISTENTE") if "FALSA00001" in e["datosJson"]]
    ctx.verificar("7e.25", "serie falsa dos veces → NO_EXISTE y un solo SERIE_INEXISTENTE (anti-spam)", 200, r2,
                  r1.campo("estado") == "NO_EXISTE" and r2.campo("estado") == "NO_EXISTE" and len(inexistentes) == 1,
                  detalle="eventos SERIE_INEXISTENTE: %d" % len(inexistentes))
    r = verificar("S1", gtin="07799000001011")
    ctx.verificar("7e.26", "GTIN con dígito verificador inválido → 400", 400, r)


def seccion_7f_dictamen_reportes(ctx, demo, admin):
    """7f: dictamen de cuarentenas (R8, R9, R12, R14), recall de lote y reportes ciudadanos (R13)."""
    lab = ctx.token(EMAIL_LABORATORIO, demo)
    distribuidora = ctx.token(EMAIL_DISTRIBUIDORA, demo)
    farmacia = ctx.token(EMAIL_FARMACIA, demo)
    inspector = ctx.token(EMAIL_INSPECTOR, demo)

    def bandeja_cuarentenas():
        return contenido(pedir("GET", ctx.url("/api/cuarentenas/bandeja?size=100"), token=inspector))

    def accion(id_medida, que, fundamento=None):
        cuerpo = {"fundamento": fundamento} if fundamento is not None else None
        return pedir("POST", ctx.url("/api/cuarentenas/%s/%s" % (id_medida, que)), cuerpo, token=inspector)

    def abrir(lote_id, motivo):
        return pedir("POST", ctx.url("/api/cuarentenas"), {"loteId": lote_id, "motivo": motivo,
                                                            "descripcion": "Medida de la prueba e2e"}, token=inspector)

    def lote(codigo):
        return buscar(contenido(pedir("GET", ctx.url("/api/lotes?size=100"), token=lab)), "codigo", codigo)

    def verificar(serie):
        return pedir("GET", ctx.url("/api/verificacion?gtin=%s&serie=%s" % (GTIN_CUYAFEN, serie)))

    # --- Ruptura de frío de BUL-0001 (7d): solo cabe recall, y el lote no cambia ---
    ruptura = exigir(next((c for c in bandeja_cuarentenas() if c["motivo"] == "RUPTURA_FRIO"), None),
                     "no encontré la cuarentena RUPTURA_FRIO en la bandeja del inspector")
    ctx.verificar("7f.1", "la cuarentena RUPTURA_FRIO de BUL-0001 está en la bandeja del inspector", 200, None, True)
    r = accion(ruptura["id"], "levantar", "x")
    ctx.verificar("7f.2", "levantar sin tomar → 409 TRANSICION_INVALIDA", 409, r, r.campo("regla") == "TRANSICION_INVALIDA")
    r = accion(ruptura["id"], "tomar")
    tomadas = eventos_de_tipo(ctx, admin, "CUARENTENA_TOMADA")
    ctx.verificar("7f.3", "tomar → 200 y evento CUARENTENA_TOMADA", 200, r, len(tomadas) == 1)
    r = accion(ruptura["id"], "levantar", "Quiero levantarla")
    ctx.verificar("7f.4", "levantar una ruptura de frío → 409 R9", 409, r, r.campo("regla") == "R9")
    r = accion(ruptura["id"], "recall", "Cadena de frío rota: no apto")
    ctx.verificar("7f.5", "convertir en recall → CONVERTIDA_EN_RECALL y el lote L2026-0001 sigue LIBERADO", 200, r,
                  r.campo("estado") == "CONVERTIDA_EN_RECALL" and (lote("L2026-0001") or {}).get("estado") == "LIBERADO")

    r = abrir(exigir(lote("L2026-0003"), "no encontré L2026-0003")["id"], "RUPTURA_FRIO")
    ctx.verificar("7f.6", "abrir una cuarentena manual con un motivo de sistema → 400", 400, r)

    # --- Cuarentena PREVENTIVA de L2026-0003 y levantamiento ---
    l3 = lote("L2026-0003")["id"]
    r = abrir(l3, "PREVENTIVA")
    preventiva = r.campo("id")
    ctx.verificar("7f.7", "cuarentena PREVENTIVA de L2026-0003 → 201 y el lote en CUARENTENA", 201, r,
                  (lote("L2026-0003") or {}).get("estado") == "CUARENTENA")
    r = verificar("L20260003S000001")
    ctx.verificar("7f.8", "una caja de L2026-0003 → BLOQUEADA (cuarentena preventiva)", 200, r,
                  r.campo("estado") == "BLOQUEADA" and "cuarentena" in (r.campo("mensaje") or "").lower())
    r = accion(exigir(preventiva, "no se abrió la cuarentena preventiva"), "levantar", "Análisis conforme")
    ctx.verificar("7f.9", "levantar la preventiva (quien la abre ya es revisor) → LEVANTADA y el lote vuelve a LIBERADO", 200, r,
                  r.campo("estado") == "LEVANTADA" and (lote("L2026-0003") or {}).get("estado") == "LIBERADO")

    # --- Rechazos en recepción: precinto roto (se acepta por dictamen) y cantidad distinta (R8) ---
    circuito = exigir(buscar(contenido(pedir("GET", ctx.url("/api/circuitos?size=100"), token=lab)), "codigo", "CIR-0001"),
                      "no encontré CIR-0001")["id"]
    codigos = []
    for precinto in ("PRE-E2E-3", "PRE-E2E-4"):
        r = pedir("POST", ctx.url("/api/bultos"), {"circuitoId": circuito, "loteId": l3, "precinto": precinto,
                                                    "cantidad": 5}, token=lab)
        codigos.append(r.campo("codigo"))
    ctx.verificar("7f.10", "armar dos bultos de 5 cajas de L2026-0003 → %s" % codigos, 200, None, all(codigos))
    r = pedir("POST", ctx.url("/api/viajes"), {"patente": "EF789GH", "chofer": "Chofer E2E 7f",
                                                "fechaEstimadaEntrega": "2026-12-03T18:00:00", "bultos": codigos}, token=lab)
    viaje = exigir(r.campo("id"), "no se creó el viaje de los bultos de 7f")
    r = pedir("POST", ctx.url("/api/viajes/%s/salida" % viaje), token=lab)
    ctx.verificar("7f.11", "viaje de tramo 1 con esos dos bultos → salida EN_TRANSITO", 200, r,
                  r.campo("estado") == "EN_TRANSITO")
    rp = pedir("POST", ctx.url("/api/recepciones"), {"codigoBulto": codigos[0], "precintoIntacto": False,
                                                     "cantidadVerificada": 5, "temperatura": 20}, token=distribuidora)
    rc = pedir("POST", ctx.url("/api/recepciones"), {"codigoBulto": codigos[1], "precintoIntacto": True,
                                                     "cantidadVerificada": 4, "temperatura": 20}, token=distribuidora)
    ctx.verificar("7f.12", "Piedemonte rechaza %s (PRECINTO_ROTO) y %s (CANTIDAD_DISTINTA)" % tuple(codigos), 201, rc,
                  rp.campo("motivoRechazo") == "PRECINTO_ROTO" and rc.campo("motivoRechazo") == "CANTIDAD_DISTINTA")
    bultos = {b["codigo"]: b["id"] for b in contenido(pedir("GET", ctx.url("/api/bultos?size=100"), token=lab))}
    medidas = {c["bultoIds"][0]: c["id"] for c in bandeja_cuarentenas()
               if c["alcance"] == "BULTO" and c["bultoIds"]}
    medida_precinto = exigir(medidas.get(bultos.get(codigos[0])), "no encontré la cuarentena BULTO de %s" % codigos[0])
    medida_cantidad = exigir(medidas.get(bultos.get(codigos[1])), "no encontré la cuarentena BULTO de %s" % codigos[1])
    accion(medida_precinto, "tomar")
    accion(medida_cantidad, "tomar")
    r = accion(medida_precinto, "levantar", "Contenido verificado: precinto dañado en el traslado")
    estado_bulto = buscar(contenido(pedir("GET", ctx.url("/api/bultos?size=100"), token=lab)), "codigo", codigos[0])
    en_deposito = [u for u in contenido(pedir("GET", ctx.url("/api/unidades-trazables?size=100"), token=distribuidora))
                   if u["estado"] == "EN_DEPOSITO"]
    ctx.verificar("7f.13", "levantar la medida por precinto roto → bulto EN_DEPOSITO y sus 5 cajas EN_DEPOSITO", 200, r,
                  r.campo("estado") == "LEVANTADA" and estado_bulto["estado"] == "EN_DEPOSITO" and len(en_deposito) == 5,
                  detalle=(r.cuerpo, estado_bulto, "EN_DEPOSITO: %d" % len(en_deposito)))
    r = accion(medida_cantidad, "levantar", "x")
    ctx.verificar("7f.14", "levantar la medida por cantidad distinta → 409 R8", 409, r, r.campo("regla") == "R8")
    r = accion(medida_cantidad, "recall", "Faltante no justificado")
    ctx.verificar("7f.15", "esa medida solo admite recall → CONVERTIDA_EN_RECALL", 200, r,
                  r.campo("estado") == "CONVERTIDA_EN_RECALL")

    # --- Recall de L2026-0001 con cajas en Los Álamos ---
    l1 = lote("L2026-0001")["id"]
    r = abrir(l1, "DEFECTO_CALIDAD")
    defecto = r.campo("id")
    ctx.verificar("7f.16", "cuarentena DEFECTO_CALIDAD de L2026-0001 → 201, lote en CUARENTENA", 201, r,
                  (lote("L2026-0001") or {}).get("estado") == "CUARENTENA")
    r = pedir("POST", ctx.url("/api/dispensaciones"), {"gtin": GTIN_CUYAFEN, "serie": "L20260001S000013",
                                                        "particular": True, "numeroReceta": "REC-7F"}, token=farmacia)
    ctx.verificar("7f.17", "dispensar una caja de L2026-0001 en cuarentena → 409 R10", 409, r, r.campo("regla") == "R10")
    r = accion(exigir(defecto, "no se abrió la cuarentena DEFECTO_CALIDAD"), "recall", "Defecto de calidad confirmado")
    ctx.verificar("7f.18", "recall → el lote L2026-0001 pasa a RECALL", 200, r,
                  r.campo("estado") == "CONVERTIDA_EN_RECALL" and (lote("L2026-0001") or {}).get("estado") == "RECALL")
    r1 = verificar("L20260001S000013")
    r2 = verificar("L20260001S000011")
    ctx.verificar("7f.19", "verificación de una caja en stock y de una ya dispensada → BLOQUEADA, retiro del mercado", 200, r2,
                  r1.campo("estado") == "BLOQUEADA" and "Retiro del mercado" in (r1.campo("mensaje") or "")
                  and r2.campo("estado") == "BLOQUEADA" and "Retiro del mercado" in (r2.campo("mensaje") or ""))
    r = pedir("POST", ctx.url("/api/unidades-trazables/devolucion"), {"gtin": GTIN_CUYAFEN, "serie": "L20260001S000013",
                                                                        "motivo": "RETIRO_DEL_MERCADO"}, token=farmacia)
    ctx.verificar("7f.20", "la farmacia devuelve la caja con RETIRO_DEL_MERCADO → DEVUELTA", 200, r,
                  r.campo("estado") == "DEVUELTA")

    # --- Reportes ciudadanos ---
    paciente = ctx.token(EMAIL_PACIENTE, demo)
    propios = contenido(pedir("GET", ctx.url("/api/reportes-ciudadanos?size=100"), token=paciente))
    rep1 = exigir(buscar(propios, "codigo", "REP-0001"), "no encontré REP-0001 de la demo")
    ctx.verificar("7f.21", "el paciente demo ve su REP-0001 ABIERTO", 200, None, rep1["estado"] == "ABIERTO")

    r = pedir("POST", ctx.url("/api/registro/pacientes"), {"email": EMAIL_PACIENTE_NUEVO, "password": demo,
                                                           "nombre": "Pedro", "apellido": "Gómez", "dni": "30111098"})
    otro = ctx.token(EMAIL_PACIENTE_NUEVO, demo)
    r = pedir("POST", ctx.url("/api/reportes-ciudadanos"), {"gtin": GTIN_CUYAFEN, "serie": "L20260001S000014",
                                                            "motivo": "ENVASE_DANADO", "provincia": "MENDOZA",
                                                            "descripcion": DESCRIPCION_REPORTE_E2E}, token=otro)
    ctx.verificar("7f.22", "otro paciente reporta una caja existente → 201 ABIERTO, cajaExiste", 201, r,
                  r.campo("estado") == "ABIERTO" and r.campo("cajaExiste") is True)
    rep2 = r.campo("codigo")
    reportes = [e for e in eventos_de_tipo(ctx, admin, "REPORTE_CIUDADANO") if ('"%s"' % rep2) in e["datosJson"]]
    ctx.verificar("7f.23", "REPORTE_CIUDADANO sin la descripción ni datos del paciente (R13)", 200, None,
                  len(reportes) == 1 and all(x not in reportes[0]["datosJson"] for x in ("Pedro", "30111098", "aplastada")),
                  detalle=reportes)
    r = pedir("GET", ctx.url("/api/reportes-ciudadanos?size=100"), token=otro)
    ajeno = pedir("GET", ctx.url("/api/reportes-ciudadanos/%s" % rep1["id"]), token=otro)
    ctx.verificar("7f.24", "el paciente nuevo ve solo su reporte; REP-0001 ajeno → 404", 404, ajeno,
                  [x["codigo"] for x in contenido(r)] == [rep2])

    r = pedir("GET", ctx.url("/api/reportes-ciudadanos/bandeja?size=100"), token=inspector)
    ctx.verificar("7f.25", "REP-0001 está en la bandeja del inspector", 200, r,
                  buscar(contenido(r), "codigo", "REP-0001") is not None)
    r = pedir("POST", ctx.url("/api/reportes-ciudadanos/%s/cerrar" % rep1["id"]), {"conclusion": "x"}, token=inspector)
    ctx.verificar("7f.26", "cerrar sin tomar → 409 TRANSICION_INVALIDA", 409, r, r.campo("regla") == "TRANSICION_INVALIDA")
    r = pedir("POST", ctx.url("/api/reportes-ciudadanos/%s/tomar" % rep1["id"]), token=inspector)
    ctx.verificar("7f.27", "tomar REP-0001 → EN_INVESTIGACION", 200, r, r.campo("estado") == "EN_INVESTIGACION")
    r = pedir("POST", ctx.url("/api/reportes-ciudadanos/%s/cerrar" % rep1["id"]),
              {"conclusion": CONCLUSION_E2E}, token=inspector)
    cerrados = [e for e in eventos_de_tipo(ctx, admin, "REPORTE_CERRADO") if '"REP-0001"' in e["datosJson"]]
    ctx.verificar("7f.28", "cerrar REP-0001 → CERRADO; REPORTE_CERRADO sin el texto de la conclusión", 200, r,
                  r.campo("estado") == "CERRADO" and len(cerrados) == 1 and "fiscal" not in cerrados[0]["datosJson"])
    r = pedir("GET", ctx.url("/api/reportes-ciudadanos/%s" % rep1["id"]), token=paciente)
    ctx.verificar("7f.29", "el paciente ve REP-0001 CERRADO, sin la conclusión", 200, r,
                  r.campo("estado") == "CERRADO" and r.campo("conclusion") is None)


def seccion_8_anclaje_deshabilitado(ctx, demo, admin):
    """8 (por defecto): sin blockchain, el anclaje responde como deshabilitado y la cadena local sigue verificándose."""
    sede = ctx.token(admin[0], admin[1])
    r = pedir("GET", ctx.url("/api/registros-blockchain/estado"), token=sede)
    ctx.verificar("8.1", "estado del anclaje (Sede) → deshabilitado, con el último evento local y el techo de gas", 200,
                  r, r.campo("habilitado") is False and (r.campo("ultimoNumeroLocal") or 0) > 0
                  and r.campo("gasMaximo") == 500000 and r.campo("gasMinimo") == 100000)
    r = pedir("POST", ctx.url("/api/registros-blockchain/anclar"), token=sede)
    ctx.verificar("8.2", "anclar ya con el anclaje deshabilitado → 409 ANCLAJE_DESHABILITADO", 409, r,
                  r.campo("regla") == "ANCLAJE_DESHABILITADO")
    r = pedir("POST", ctx.url("/api/registros-blockchain/anclar"), token=ctx.token(EMAIL_INSPECTOR, demo))
    ctx.verificar("8.3", "anclar ya como inspector → 403 (solo la Sede)", 403, r)
    r = pedir("GET", ctx.url("/api/registros-blockchain"), token=sede)
    ctx.verificar("8.4", "sin anclajes registrados", 200, r, contenido(r) == [])
    r = pedir("GET", ctx.url("/api/verificacion?gtin=%s&serie=L20260003S000006" % GTIN_CUYAFEN))
    ctx.verificar("8.5", "verificación pública → anclaje NO_DISPONIBLE, sin transacción", 200, r,
                  (r.campo("anclaje") or {}).get("estado") == "NO_DISPONIBLE"
                  and (r.campo("anclaje") or {}).get("transactionHash") is None)
    r = pedir("GET", ctx.url("/api/eventos-trazabilidad/verificacion"), token=sede)
    ctx.verificar("8.6", "verificación de la cadena → local íntegra, blockchain NO_DISPONIBLE, con resumen", 200, r,
                  r.campo("integra") is True and (r.campo("blockchain") or {}).get("estado") == "NO_DISPONIBLE"
                  and "deshabilitado" in (r.campo("resumen") or ""))


def seccion_8_anclaje_sepolia(ctx, demo, admin):
    """8 (--sepolia): anclaje real en Sepolia sobre un contrato descartable recién desplegado."""
    sede = ctx.token(admin[0], admin[1])
    r = pedir("GET", ctx.url("/api/registros-blockchain/estado"), token=sede)
    exigir(r.campo("habilitado") or None, "la app no tiene el anclaje habilitado (ANCLAJE_HABILITADO=true)")
    ctx.verificar("8.1", "estado: habilitado, contrato nuevo (0 anclados), saldo %s ETH, primer anclaje ~%s de gas "
                  "(límite %s ≤ %s)" % (r.campo("saldoEth"), r.campo("gasPorAnclaje"), r.campo("limiteGas"),
                                         r.campo("gasMaximo")), 200, r,
                  r.campo("ultimoNumeroAnclado") == 0 and (r.campo("billetera") or "").startswith("0x")
                  and r.campo("eventosSinAnclar") == r.campo("ultimoNumeroLocal")
                  and r.campo("primerAnclaje") is True and (r.campo("gasPorAnclaje") or 0) > 0
                  and (r.campo("limiteGas") or 0) <= (r.campo("gasMaximo") or 0)
                  and r.campo("anclajeAutomaticoFrenado") is False)
    r = pedir("POST", ctx.url("/api/registros-blockchain/anclar"), token=ctx.token(EMAIL_INSPECTOR, demo))
    ctx.verificar("8.2", "anclar ya como inspector → 403 (solo la Sede)", 403, r)

    r = pedir("POST", ctx.url("/api/registros-blockchain/anclar"), token=sede)
    anclaje_id = r.campo("id")
    ctx.verificar("8.3", "anclar ya → 202 ENVIADO con transacción y enlace a Etherscan: %s" % r.campo("enlaceEtherscan"),
                  202, r, r.campo("estado") == "ENVIADO"
                  and re.fullmatch(r"0x[0-9a-f]{64}", r.campo("transactionHash") or "") is not None
                  and (r.campo("enlaceEtherscan") or "").startswith("https://sepolia.etherscan.io/tx/0x"))
    exigir(anclaje_id, "no se creó el anclaje")
    r = pedir("POST", ctx.url("/api/registros-blockchain/anclar"), token=sede)
    ctx.verificar("8.4", "anclar ya otra vez mientras está en curso → 409 ANCLAJE_EN_CURSO", 409, r,
                  r.campo("regla") == "ANCLAJE_EN_CURSO")

    limite = time.time() + 300
    while True:
        r = pedir("GET", ctx.url("/api/registros-blockchain/%s" % anclaje_id), token=sede)
        if r.campo("estado") in ("CONFIRMADO", "FALLIDO") or time.time() > limite:
            break
        time.sleep(10)
    ctx.datos["anclaje"] = r.cuerpo
    ctx.verificar("8.5", "el anclaje llega a CONFIRMADO (bloque %s, gas %s, costo %s ETH)"
                  % (r.campo("bloque"), r.campo("gasUsado"), r.campo("costoEth")), 200, r,
                  r.campo("estado") == "CONFIRMADO" and (r.campo("confirmaciones") or 0) >= 3
                  and r.campo("gasUsado") is not None)

    r = pedir("GET", ctx.url("/api/eventos-trazabilidad/verificacion"), token=sede)
    blockchain = r.campo("blockchain") or {}
    ctx.verificar("8.6", "verificación de la cadena → local íntegra y blockchain VERIFICADA (1 anclaje, 0 sin anclar)",
                  200, r, r.campo("integra") is True and blockchain.get("estado") == "VERIFICADA"
                  and blockchain.get("anclajesVerificados") == 1 and blockchain.get("eventosSinAnclar") == 0,
                  detalle=r.cuerpo)
    r = pedir("GET", ctx.url("/api/verificacion?gtin=%s&serie=L20260003S000006" % GTIN_CUYAFEN))
    anclaje = r.campo("anclaje") or {}
    ctx.verificar("8.7", "verificación pública → anclaje CONFIRMADO con bloque y enlace a Etherscan", 200, r,
                  anclaje.get("estado") == "CONFIRMADO" and anclaje.get("bloque") is not None
                  and (anclaje.get("enlace") or "").startswith("https://sepolia.etherscan.io/tx/"))
    r = pedir("POST", ctx.url("/api/registros-blockchain/anclar"), token=sede)
    ctx.verificar("8.8", "anclar ya sin eventos nuevos → 409 SIN_EVENTOS_NUEVOS", 409, r,
                  r.campo("regla") == "SIN_EVENTOS_NUEVOS")


def seccion_9_cuentas(ctx, demo, admin):
    """9: el token todavía vigente de una cuenta desactivada deja de servir (el filtro JWT mira la base)."""
    sede = ctx.token(admin[0], admin[1])
    alta = {"legajo": "E2E-0009", "dni": "39000909", "provincia": "SAN_JUAN", "email": EMAIL_INSPECTOR_BAJA,
            "password": demo, "nombre": "Inspector", "apellido": "Baja"}
    r = pedir("POST", ctx.url("/api/inspectores-anmat"), alta, token=sede)
    ctx.verificar("9.1", "la Sede da de alta un inspector de San Juan → 201", 201, r)
    inspector_id = exigir(r.campo("id"), "el alta del inspector no devolvió id")
    token_previo = iniciar_sesion(ctx.base, EMAIL_INSPECTOR_BAJA, demo)
    r = pedir("GET", ctx.url("/api/eventos-trazabilidad/verificacion"), token=token_previo)
    ctx.verificar("9.2", "con su token verifica la cadena → 200", 200, r)

    r = pedir("POST", ctx.url("/api/inspectores-anmat/%s/baja" % inspector_id), token=sede)
    ctx.verificar("9.3", "la Sede lo da de baja → 200 BAJA", 200, r, r.campo("estado") == "BAJA")
    r = pedir("GET", ctx.url("/api/eventos-trazabilidad/verificacion"), token=token_previo)
    ctx.verificar("9.4", "el MISMO token, todavía vigente, después de la baja → 401", 401, r)
    r = pedir("POST", ctx.url("/api/auth/login"), {"email": EMAIL_INSPECTOR_BAJA, "password": demo})
    ctx.verificar("9.5", "login del inspector dado de baja → 401", 401, r)

    r = pedir("POST", ctx.url("/api/inspectores-anmat/%s/reactivar" % inspector_id), token=sede)
    ctx.verificar("9.6", "la Sede lo reactiva → 200 ACTIVO", 200, r, r.campo("estado") == "ACTIVO")
    r = pedir("GET", ctx.url("/api/eventos-trazabilidad/verificacion"),
              token=iniciar_sesion(ctx.base, EMAIL_INSPECTOR_BAJA, demo))
    ctx.verificar("9.7", "reactivado, con un login nuevo vuelve a operar → 200", 200, r)


def seccion_final_cadena(ctx, demo, admin):
    """Final: la cadena sigue íntegra después de todo el recorrido."""
    sede = ctx.token(admin[0], admin[1])
    r = pedir("GET", ctx.url("/api/eventos-trazabilidad/verificacion"), token=sede)
    ctx.verificar("final", "cadena íntegra al terminar (%s eventos)" % r.campo("eventosVerificados"), 200, r,
                  r.campo("integra") is True)


# Orden de ejecución. Para un paso nuevo, agregar aquí su función.
SECCIONES = [
    ("5b · Permisos", seccion_5b_permisos),
    ("6  · Cadena de eventos", seccion_6_cadena),
    ("7a · Empresas", seccion_7a_empresas),
    ("7b · Circuitos", seccion_7b_circuitos),
    ("7c · Lotes", seccion_7c_lotes),
    ("7d · Viajes y telemetría", seccion_7d_viajes),
    ("7e · Recepción, dispensación y verificación pública", seccion_7e_recepcion_dispensacion),
    ("7f · Dictamen de cuarentenas y reportes ciudadanos", seccion_7f_dictamen_reportes),
    ("8  · Anclaje (deshabilitado)", seccion_8_anclaje_deshabilitado),
    ("9  · Cuentas desactivadas con token vigente", seccion_9_cuentas),
    ("Final · Cadena íntegra", seccion_final_cadena),
]


def main():
    parser = argparse.ArgumentParser(description="Prueba de punta a punta de MediChain por la API.")
    parser.add_argument("--url", default="http://localhost:8080", help="URL base del backend")
    parser.add_argument("--sepolia", action="store_true",
                        help="la app tiene el anclaje habilitado con un contrato recién desplegado: ancla de verdad "
                             "en Sepolia (gasta un anclaje) en lugar de probar el modo deshabilitado")
    args = parser.parse_args()

    faltan = [v for v in ("DEMO_PASSWORD", "ADMIN_EMAIL", "ADMIN_PASSWORD") if not os.environ.get(v)]
    if faltan:
        sys.exit("Faltan variables de entorno: %s (hacé: set -a; source .env; set +a)" % ", ".join(faltan))
    demo = os.environ["DEMO_PASSWORD"]
    admin = (os.environ["ADMIN_EMAIL"], os.environ["ADMIN_PASSWORD"])
    ctx = Contexto(args.url.rstrip("/"))
    ctx.datos["sepolia"] = args.sepolia
    secciones = [(titulo, seccion) for titulo, seccion in SECCIONES]
    if args.sepolia:
        secciones = [("8  · Anclaje en Sepolia", seccion_8_anclaje_sepolia) if seccion is seccion_8_anclaje_deshabilitado
                     else (titulo, seccion) for titulo, seccion in secciones]

    problemas = base_recien_reseteada(ctx, demo)
    if problemas:
        print("La base no está recién reseteada con la demo; no se ejecutó ninguna verificación:")
        for problema in problemas:
            print("  - " + problema)
        print("Reseteá con ./reset-demo.sh, levantá con ./levantar.sh y volvé a correr la prueba.")
        sys.exit(2)

    for titulo, seccion in secciones:
        print("\n== %s ==" % titulo)
        try:
            seccion(ctx, demo, admin)
        except SeccionInterrumpida as motivo:
            ctx.total += 1
            print("✘ sección interrumpida: %s" % motivo)

    print("\n%d de %d verificaciones OK" % (ctx.ok, ctx.total))
    sys.exit(0 if ctx.ok == ctx.total else 1)


if __name__ == "__main__":
    main()
