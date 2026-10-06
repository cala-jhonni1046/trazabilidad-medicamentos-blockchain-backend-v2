package com.medichain.modules.trazabilidad;

import com.medichain.utils.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Entidad CadenaEstado en MediChain.
 * Fila única (nombre "PRINCIPAL") con el último número y el último hash
 * de la cadena de eventos. Cada alta de evento la bloquea con
 * SELECT ... FOR UPDATE: así dos transacciones nunca toman el mismo
 * número ni encadenan con el mismo hash anterior. Arranca en (0, GENESIS).
 */
@Entity
@Table(name = "cadena_estado")
public class CadenaEstado extends BaseEntity {

    /** Nombre de la única fila de estado de la cadena. */
    public static final String PRINCIPAL = "PRINCIPAL";

    @Column(name = "nombre", nullable = false, length = 20, unique = true)
    private String nombre;

    @Column(name = "ultimo_numero", nullable = false, unique = false)
    private Long ultimoNumero;

    @Column(name = "ultimo_hash", nullable = false, length = 64, unique = false)
    private String ultimoHash;

    /** Constructor vacío exigido por JPA. */
    protected CadenaEstado() {
    }

    /** Crea el estado inicial de la cadena con su número y hash de partida. */
    public CadenaEstado(String nombre, Long ultimoNumero, String ultimoHash) {
        this.nombre = nombre;
        this.ultimoNumero = ultimoNumero;
        this.ultimoHash = ultimoHash;
    }

    /** Avanza el estado al evento recién registrado. */
    public void avanzar(Long numero, String hash) {
        this.ultimoNumero = numero;
        this.ultimoHash = hash;
    }

    /** Devuelve el nombre de la fila de estado. */
    public String getNombre() {
        return nombre;
    }

    /** Devuelve el número del último evento registrado (0 si no hay eventos). */
    public Long getUltimoNumero() {
        return ultimoNumero;
    }

    /** Devuelve el hash del último evento registrado ("GENESIS" si no hay eventos). */
    public String getUltimoHash() {
        return ultimoHash;
    }
}
