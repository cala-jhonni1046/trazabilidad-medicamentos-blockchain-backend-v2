package com.medichain.modules.trazabilidad;

import com.medichain.utils.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Entidad EventoTrazabilidad en MediChain.
 * Asiento inmutable de la cadena única de eventos. Su hash es
 * SHA-256(JSON canónico de todos sus campos, incluido hashAnterior), en
 * 64 caracteres hex minúscula; el primer evento usa hashAnterior "GENESIS".
 * No tiene setters ni FKs: todo lo que entra en el hash es un valor
 * propio del evento, y Hibernate nunca emite UPDATE ({@code @Immutable}).
 * Nunca contiene datos del paciente (R13).
 */
@Entity
@Table(name = "eventos_trazabilidad")
@Immutable
public class EventoTrazabilidad extends BaseEntity {

    /** hashAnterior del primer evento de la cadena. */
    public static final String GENESIS = "GENESIS";

    @Column(name = "numero", nullable = false, unique = true)
    private Long numero;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 40, unique = false)
    private TipoEvento tipo;

    // Instant en UTC truncado a microsegundos (precisión de timestamp de PostgreSQL),
    // para que el valor releído de la base reproduzca exactamente el mismo hash.
    @Column(name = "fecha_hora", nullable = false, unique = false)
    private Instant fechaHora;

    @Column(name = "entidad_tipo", nullable = false, length = 100, unique = false)
    private String entidadTipo;

    @Column(name = "entidad_id", nullable = false, unique = false)
    private UUID entidadId;

    // JSON canónico de la lista blanca de datos del tipo de evento.
    @Column(name = "datos_json", nullable = false, columnDefinition = "TEXT")
    private String datosJson;

    // nullable = true: eventos del sistema (sin usuario) o de un PACIENTE (se omite el actor).
    @Column(name = "actor_usuario_id", nullable = true, unique = false)
    private UUID actorUsuarioId;

    // nullable = true: el actor no pertenece a una empresa (Sede, inspector) o no hay actor.
    @Column(name = "actor_empresa_id", nullable = true, unique = false)
    private UUID actorEmpresaId;

    @Column(name = "hash_anterior", nullable = false, length = 64, unique = true)
    private String hashAnterior;

    @Column(name = "hash", nullable = false, length = 64, unique = true)
    private String hash;

    /** Constructor vacío exigido por JPA. */
    protected EventoTrazabilidad() {
    }

    /**
     * Constructor completo: recibe número, fecha y hashAnterior ya
     * resueltos por RegistradorEventos (bajo el bloqueo de la cadena) y
     * calcula el hash de inmediato. El evento queda completo e inmutable.
     */
    public EventoTrazabilidad(Long numero, TipoEvento tipo, Instant fechaHora, String entidadTipo, UUID entidadId,
                              String datosJson, UUID actorUsuarioId, UUID actorEmpresaId, String hashAnterior) {
        this.numero = numero;
        this.tipo = tipo;
        this.fechaHora = fechaHora.truncatedTo(ChronoUnit.MICROS);
        this.entidadTipo = entidadTipo;
        this.entidadId = entidadId;
        this.datosJson = datosJson;
        this.actorUsuarioId = actorUsuarioId;
        this.actorEmpresaId = actorEmpresaId;
        this.hashAnterior = hashAnterior;
        this.hash = calcularHash();
    }

    /**
     * Recalcula el hash a partir de los campos guardados. Se usa al crear
     * el evento y al verificar la cadena (un dato alterado cambia el hash).
     */
    public String calcularHash() {
        return HashUtil.sha256Hex(contenidoCanonico());
    }

    /** Devuelve el JSON canónico que se hashea (claves ordenadas, nulls omitidos). */
    public String contenidoCanonico() {
        Map<String, Object> contenido = new LinkedHashMap<>();
        contenido.put("actorEmpresaId", actorEmpresaId);
        contenido.put("actorUsuarioId", actorUsuarioId);
        contenido.put("datos", new JsonCanonico.JsonCrudo(datosJson));
        contenido.put("entidadId", entidadId);
        contenido.put("entidadTipo", entidadTipo);
        contenido.put("fechaHora", fechaHora);
        contenido.put("hashAnterior", hashAnterior);
        contenido.put("numero", numero);
        contenido.put("tipo", tipo);
        return JsonCanonico.escribir(contenido);
    }

    /** Devuelve el número correlativo del evento (sin huecos). */
    public Long getNumero() {
        return numero;
    }

    /** Devuelve el tipo de evento. */
    public TipoEvento getTipo() {
        return tipo;
    }

    /** Devuelve la fecha y hora UTC del evento. */
    public Instant getFechaHora() {
        return fechaHora;
    }

    /** Devuelve el nombre de la entidad de negocio afectada (p. ej. "Lote"). */
    public String getEntidadTipo() {
        return entidadTipo;
    }

    /** Devuelve el id de la entidad de negocio afectada. */
    public UUID getEntidadId() {
        return entidadId;
    }

    /** Devuelve los datos del evento en JSON canónico. */
    public String getDatosJson() {
        return datosJson;
    }

    /** Devuelve el id del usuario actor, o null. */
    public UUID getActorUsuarioId() {
        return actorUsuarioId;
    }

    /** Devuelve el id de la empresa del actor, o null. */
    public UUID getActorEmpresaId() {
        return actorEmpresaId;
    }

    /** Devuelve el hash del evento anterior ("GENESIS" en el primero). */
    public String getHashAnterior() {
        return hashAnterior;
    }

    /** Devuelve el hash propio de este evento. */
    public String getHash() {
        return hash;
    }
}
