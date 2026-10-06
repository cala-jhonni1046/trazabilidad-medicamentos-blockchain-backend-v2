"""
Funciones comunes para hablar con la API de MediChain desde los scripts
(simular-sensor.py y prueba-e2e.py). Solo biblioteca estándar de Python 3.

Las contraseñas nunca llegan acá por argumento de línea de comandos: cada
script las lee de variables de entorno.
"""
import json
import sys
import urllib.error
import urllib.request
import uuid


class Respuesta:
    """Resultado de una request: código HTTP y cuerpo (JSON decodificado o texto)."""

    def __init__(self, codigo, cuerpo):
        self.codigo = codigo
        self.cuerpo = cuerpo

    def campo(self, nombre, defecto=None):
        """Devuelve un campo del cuerpo JSON, o el defecto si no es un objeto o no lo tiene."""
        if isinstance(self.cuerpo, dict):
            return self.cuerpo.get(nombre, defecto)
        return defecto


def _enviar(request):
    """Ejecuta la request y devuelve una Respuesta (también para códigos 4xx/5xx)."""
    try:
        with urllib.request.urlopen(request, timeout=30) as respuesta:
            texto = respuesta.read().decode("utf-8")
            return Respuesta(respuesta.status, _decodificar(texto))
    except urllib.error.HTTPError as error:
        return Respuesta(error.code, _decodificar(error.read().decode("utf-8")))


def _decodificar(texto):
    """Intenta decodificar JSON; si no se puede, devuelve el texto."""
    if not texto:
        return None
    try:
        return json.loads(texto)
    except json.JSONDecodeError:
        return texto


def pedir(metodo, url, cuerpo=None, token=None, encabezado_auth=None):
    """
    Request con cuerpo JSON. token → "Authorization: Bearer <token>".
    encabezado_auth permite mandar un Authorization arbitrario (para probar tokens mal formados).
    """
    datos = json.dumps(cuerpo).encode("utf-8") if cuerpo is not None else None
    request = urllib.request.Request(url, data=datos, method=metodo)
    request.add_header("Content-Type", "application/json")
    if encabezado_auth is not None:
        request.add_header("Authorization", encabezado_auth)
    elif token:
        request.add_header("Authorization", "Bearer " + token)
    return _enviar(request)


def pedir_multipart(url, campos, archivos, token=None):
    """
    POST multipart/form-data armado a mano (sin dependencias).
    campos: {nombre: valor}; archivos: {nombre: (nombre_archivo, bytes, content_type)}.
    """
    frontera = "----medichain" + uuid.uuid4().hex
    partes = []
    for nombre, valor in campos.items():
        partes.append(("--%s\r\nContent-Disposition: form-data; name=\"%s\"\r\n\r\n%s\r\n"
                       % (frontera, nombre, valor)).encode("utf-8"))
    for nombre, (nombre_archivo, contenido, tipo) in archivos.items():
        partes.append(("--%s\r\nContent-Disposition: form-data; name=\"%s\"; filename=\"%s\"\r\n"
                       "Content-Type: %s\r\n\r\n" % (frontera, nombre, nombre_archivo, tipo)).encode("utf-8"))
        partes.append(contenido + b"\r\n")
    partes.append(("--%s--\r\n" % frontera).encode("utf-8"))
    request = urllib.request.Request(url, data=b"".join(partes), method="POST")
    request.add_header("Content-Type", "multipart/form-data; boundary=" + frontera)
    if token:
        request.add_header("Authorization", "Bearer " + token)
    return _enviar(request)


def iniciar_sesion(base, email, password):
    """Login: devuelve el token JWT o termina con un mensaje claro."""
    respuesta = pedir("POST", base + "/api/auth/login", {"email": email, "password": password})
    if respuesta.codigo != 200:
        sys.exit("No se pudo iniciar sesión como %s (HTTP %s): %s" % (email, respuesta.codigo, respuesta.cuerpo))
    return respuesta.cuerpo["token"]


def buscar_viaje(base, token, codigo_viaje):
    """Devuelve (id, estado) del viaje con ese código entre los que ve el usuario, o (None, None)."""
    respuesta = pedir("GET", base + "/api/viajes?size=100", token=token)
    if respuesta.codigo != 200:
        return None, None
    for viaje in respuesta.cuerpo["content"]:
        if viaje["codigo"] == codigo_viaje:
            return viaje["id"], viaje["estado"]
    return None, None
