#!/usr/bin/env python3
"""Pruebas de actualizar_env.py con archivos .env FALSOS (nunca el real). Correr: python3 -m unittest scripts/test_actualizar_env.py"""
import os
import stat
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import actualizar_env  # noqa: E402

VIEJA = "0x" + "a" * 40
NUEVA = "0x" + "B" * 40
ENV = "DB_URL=jdbc:falso\nWALLET_PRIVATE_KEY=clave-falsa-de-prueba\nANCHOR_CONTRACT_ADDRESS=%s\nANCLAJE_HABILITADO=true\n" % VIEJA


class ActualizarEnvTest(unittest.TestCase):
    def setUp(self):
        self.carpeta = tempfile.TemporaryDirectory()
        self.ruta = os.path.join(self.carpeta.name, ".env")

    def tearDown(self):
        self.carpeta.cleanup()

    def escribir(self, texto):
        with open(self.ruta, "w", encoding="utf-8", newline="") as f:
            f.write(texto)

    def leer(self, ruta=None):
        with open(ruta or self.ruta, encoding="utf-8", newline="") as f:
            return f.read()

    def test_cambia_solo_esa_linea_y_hace_copia(self):
        self.escribir(ENV)
        vieja, copia = actualizar_env.actualizar(self.ruta, NUEVA)
        self.assertEqual(vieja, VIEJA)
        self.assertEqual(self.leer(), ENV.replace(VIEJA, NUEVA))
        self.assertEqual(self.leer(copia), ENV, "la copia es el original")
        self.assertEqual(stat.S_IMODE(os.stat(copia).st_mode), 0o600)

    def test_si_no_estaba_la_agrega_al_final_sin_tocar_el_resto(self):
        self.escribir("DB_URL=x\nOTRA=y")  # sin salto de línea final
        vieja, _ = actualizar_env.actualizar(self.ruta, NUEVA)
        self.assertIsNone(vieja)
        self.assertEqual(self.leer(), "DB_URL=x\nOTRA=y\nANCHOR_CONTRACT_ADDRESS=%s\n" % NUEVA)

    def test_conserva_finales_de_linea_y_comentarios(self):
        original = "# comentario con ANCHOR_CONTRACT_ADDRESS=0x\r\nANCHOR_CONTRACT_ADDRESS=%s\r\nB=2\r\n" % VIEJA
        self.escribir(original)
        actualizar_env.actualizar(self.ruta, NUEVA)
        self.assertEqual(self.leer(), original.replace(VIEJA, NUEVA).replace("\r\nB=2", "\r\nB=2"))
        self.assertIn("# comentario con ANCHOR_CONTRACT_ADDRESS=0x", self.leer(), "el comentario no es la línea de la clave")

    def test_direccion_invalida_no_toca_nada(self):
        self.escribir(ENV)
        for mala in ("", "0x123", "0x" + "z" * 40, VIEJA + "00"):
            with self.assertRaises(ValueError):
                actualizar_env.actualizar(self.ruta, mala)
        self.assertEqual(self.leer(), ENV)
        self.assertEqual([f for f in os.listdir(self.carpeta.name) if ".bak-" in f], [], "sin copias si no hubo cambio")

    def test_clave_repetida_se_niega(self):
        self.escribir(ENV + "ANCHOR_CONTRACT_ADDRESS=%s\n" % VIEJA)
        with self.assertRaises(ValueError):
            actualizar_env.actualizar(self.ruta, NUEVA)

    def test_main_imprime_solo_las_direcciones(self):
        self.escribir(ENV)
        import contextlib
        import io
        salida = io.StringIO()
        with contextlib.redirect_stdout(salida):
            codigo = actualizar_env.main([self.ruta, NUEVA])
        self.assertEqual(codigo, 0)
        self.assertIn(VIEJA, salida.getvalue())
        self.assertIn(NUEVA, salida.getvalue())
        self.assertNotIn("clave-falsa-de-prueba", salida.getvalue())
        self.assertNotIn("jdbc:falso", salida.getvalue())


if __name__ == "__main__":
    unittest.main()
