package com.medichain.utils.documentos;

import com.medichain.exceptions.DocumentoInvalidoException;
import com.medichain.modules.trazabilidad.HashUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test unitario AlmacenDocumentosTest en MediChain.
 * PDF real guardado como &lt;sha256&gt;.pdf; rechazo de no-PDF, vacío y de
 * más de 5 MB; arranque sin DOCUMENTOS_DIR.
 */
class AlmacenDocumentosTest {

    @TempDir
    Path carpeta;

    @Test
    @DisplayName("Guarda un PDF real como <sha256>.pdf y lo puede leer")
    void guardaPdfReal() throws Exception {
        AlmacenDocumentos almacen = new AlmacenDocumentos(carpeta.toString());
        byte[] pdf = "%PDF-1.4\nprueba\n%%EOF".getBytes(StandardCharsets.US_ASCII);

        DocumentoGuardado guardado = almacen.guardarPdf(pdf, "../../etc/habilitacion.pdf");

        String esperado = HashUtil.sha256Hex("%PDF-1.4\nprueba\n%%EOF");
        assertEquals(esperado, guardado.getHash());
        assertEquals("habilitacion.pdf", guardado.getNombre(), "el nombre se sanea (sin rutas)");
        assertTrue(Files.exists(carpeta.resolve(esperado + ".pdf")));
        assertArrayEquals(pdf, almacen.leer(esperado));
    }

    @Test
    @DisplayName("Rechaza un PNG con extensión .pdf (se mira el contenido, no el nombre)")
    void rechazaPngDisfrazado() {
        AlmacenDocumentos almacen = new AlmacenDocumentos(carpeta.toString());
        byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};

        assertThrows(DocumentoInvalidoException.class, () -> almacen.guardarPdf(png, "falso.pdf"));
    }

    @Test
    @DisplayName("Rechaza un archivo vacío y uno de más de 5 MB")
    void rechazaVacioYGrande() {
        AlmacenDocumentos almacen = new AlmacenDocumentos(carpeta.toString());
        byte[] grande = new byte[(int) AlmacenDocumentos.TAMANIO_MAXIMO + 1];
        System.arraycopy("%PDF-".getBytes(StandardCharsets.US_ASCII), 0, grande, 0, 5);

        assertThrows(DocumentoInvalidoException.class, () -> almacen.guardarPdf(new byte[0], "x.pdf"));
        assertThrows(DocumentoInvalidoException.class, () -> almacen.guardarPdf(grande, "x.pdf"));
    }

    @Test
    @DisplayName("Sin DOCUMENTOS_DIR la aplicación no arranca, con mensaje claro")
    void sinCarpetaNoArranca() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> new AlmacenDocumentos(""));
        assertTrue(ex.getMessage().contains("DOCUMENTOS_DIR"));
    }
}
