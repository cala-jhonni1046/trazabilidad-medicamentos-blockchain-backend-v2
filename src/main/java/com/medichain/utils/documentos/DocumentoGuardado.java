package com.medichain.utils.documentos;

/**
 * Resultado DocumentoGuardado en MediChain.
 * Hash SHA-256 del documento guardado y su nombre original saneado.
 */
public class DocumentoGuardado {

    private final String hash;
    private final String nombre;

    /** Crea el resultado con el hash y el nombre saneado. */
    public DocumentoGuardado(String hash, String nombre) {
        this.hash = hash;
        this.nombre = nombre;
    }

    /** Devuelve el SHA-256 (64 hex minúscula) del contenido. */
    public String getHash() {
        return hash;
    }

    /** Devuelve el nombre original saneado (solo informativo, nunca se usa como ruta). */
    public String getNombre() {
        return nombre;
    }
}
