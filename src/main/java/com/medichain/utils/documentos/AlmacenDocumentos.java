package com.medichain.utils.documentos;

import com.medichain.exceptions.DocumentoInvalidoException;
import com.medichain.exceptions.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Componente AlmacenDocumentos en MediChain.
 * Guarda los PDF de habilitación en una carpeta del disco configurada
 * con DOCUMENTOS_DIR (fuera del repo; nunca en la base ni en Git). Cada
 * archivo se llama "&lt;sha256&gt;.pdf": el nombre lo da el contenido, así
 * no se usa el nombre que manda el cliente para armar rutas (sin path
 * traversal) y el mismo PDF subido dos veces ocupa un solo archivo.
 * Valida que sea un PDF real (empieza con "%PDF-"), no vacío y de hasta 5 MB.
 * Si DOCUMENTOS_DIR falta, la aplicación no arranca.
 */
@Component
public class AlmacenDocumentos {

    /** Tamaño máximo de un documento: 5 MB. */
    public static final long TAMANIO_MAXIMO = 5L * 1024 * 1024;

    private static final byte[] FIRMA_PDF = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final int LARGO_MAXIMO_NOMBRE = 255;

    private final Path carpeta;

    @Autowired
    public AlmacenDocumentos(@Value("${medichain.documentos.dir:}") String carpeta) {
        if (carpeta == null || carpeta.isBlank()) {
            throw new IllegalStateException("Falta la variable de entorno DOCUMENTOS_DIR: carpeta (fuera del repo) "
                    + "donde se guardan los PDF de habilitación. Ejemplo: DOCUMENTOS_DIR=/home/usuario/medichain-documentos");
        }
        this.carpeta = Path.of(carpeta).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.carpeta);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo crear la carpeta DOCUMENTOS_DIR: " + this.carpeta, e);
        }
    }

    /**
     * Valida y guarda un PDF. Escribe a un temporal y lo mueve de forma
     * atómica. Si la transacción de negocio después falla, el archivo queda
     * huérfano pero es inofensivo (su nombre es su hash).
     * TODO: tarea de limpieza de PDFs huérfanos.
     */
    public DocumentoGuardado guardarPdf(byte[] contenido, String nombreOriginal) {
        validarPdf(contenido);
        String hash = sha256Hex(contenido);
        Path destino = carpeta.resolve(hash + ".pdf");
        try {
            if (!Files.exists(destino)) {
                Path temporal = Files.createTempFile(carpeta, "subida-", ".tmp");
                Files.write(temporal, contenido);
                Files.move(temporal, destino, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el documento", e);
        }
        return new DocumentoGuardado(hash, sanearNombre(nombreOriginal));
    }

    /** Lee el PDF guardado con el hash dado, o 404 si no está. */
    public byte[] leer(String hash) {
        if (hash == null || !hash.matches("[0-9a-f]{64}")) {
            throw new ResourceNotFoundException("Documento no encontrado");
        }
        Path archivo = carpeta.resolve(hash + ".pdf");
        if (!Files.exists(archivo)) {
            throw new ResourceNotFoundException("Documento no encontrado");
        }
        try {
            return Files.readAllBytes(archivo);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el documento", e);
        }
    }

    /** Valida no vacío, tamaño máximo y firma de PDF real (no alcanza con la extensión). */
    private void validarPdf(byte[] contenido) {
        if (contenido == null || contenido.length == 0) {
            throw new DocumentoInvalidoException("El documento de habilitación es obligatorio");
        }
        if (contenido.length > TAMANIO_MAXIMO) {
            throw new DocumentoInvalidoException("El documento no puede superar 5 MB");
        }
        if (contenido.length < FIRMA_PDF.length) {
            throw new DocumentoInvalidoException("El documento debe ser un PDF");
        }
        for (int i = 0; i < FIRMA_PDF.length; i++) {
            if (contenido[i] != FIRMA_PDF[i]) {
                throw new DocumentoInvalidoException("El documento debe ser un PDF");
            }
        }
    }

    /** SHA-256 en hex minúscula de los bytes (mismo formato que HashUtil). */
    private String sha256Hex(byte[] contenido) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 no disponible en esta JVM", e);
        }
    }

    /** Deja solo el último segmento del nombre, sin caracteres de control, truncado a 255. */
    private String sanearNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return "documento.pdf";
        }
        String limpio = nombre.replace('\\', '/');
        limpio = limpio.substring(limpio.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "");
        return limpio.length() > LARGO_MAXIMO_NOMBRE ? limpio.substring(0, LARGO_MAXIMO_NOMBRE) : limpio;
    }
}
