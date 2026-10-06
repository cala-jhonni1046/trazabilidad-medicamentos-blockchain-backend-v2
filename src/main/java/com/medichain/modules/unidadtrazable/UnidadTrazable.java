package com.medichain.modules.unidadtrazable;

import com.medichain.exceptions.ReglaNegocioException;
import com.medichain.modules.bulto.Bulto;
import com.medichain.modules.empresa.Empresa;
import com.medichain.modules.lote.Lote;
import com.medichain.utils.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Entidad UnidadTrazable en MediChain.
 * Representa una unidad individual (caja) de un Lote, identificada por su
 * número de serie único. Cada unidad pertenece a un Lote y está
 * físicamente en una Empresa; opcionalmente forma parte de un Bulto
 * mientras viaja o está depositada.
 * Hereda id, fechas de auditoría y version desde BaseEntity.
 */
@Entity
@Table(name = "unidades_trazables", uniqueConstraints = @UniqueConstraint(name = "ux_unidad_gtin_serie",
        columnNames = {"gtin", "serie"}))
public class UnidadTrazable extends BaseEntity {

    // La caja se identifica por GTIN + serie (DataMatrix (01) GTIN (21) serie): la serie es única POR GTIN.
    @Column(name = "serie", nullable = false, length = 20, unique = false)
    private String serie;

    // Desnormalizado: GTIN del medicamento del lote (inmutable). Permite la restricción única (gtin, serie).
    @Column(name = "gtin", nullable = false, length = 14, unique = false)
    private String gtin;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 30, unique = false)
    private EstadoUnidad estado;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lote_id", nullable = false)
    private Lote lote;

    // nullable = true: la empresa que tiene la caja en su poder; null mientras viaja.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "empresa_actual_id", nullable = true)
    private Empresa empresaActual;

    // nullable = true: cardinalidad "0..1"; la unidad solo pertenece a un
    // bulto mientras está empacada para el transporte.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "bulto_id", nullable = true)
    private Bulto bulto;

    /** Constructor vacío exigido por JPA. */
    protected UnidadTrazable() {
    }

    /**
     * Crea una caja del lote dado, en poder del laboratorio: nace
     * EN_LABORATORIO y copia el GTIN del medicamento del lote.
     */
    public UnidadTrazable(String serie, Lote lote) {
        this.serie = serie;
        this.lote = lote;
        this.gtin = lote.getMedicamento().getGtin();
        this.empresaActual = lote.getLaboratorio();
        this.estado = EstadoUnidad.EN_LABORATORIO;
    }

    /** Devuelve el número de serie de la unidad. */
    public String getSerie() {
        return serie;
    }

    /** Devuelve el estado actual de la unidad. */
    public EstadoUnidad getEstado() {
        return estado;
    }

    /** Devuelve el GTIN del medicamento (junto con la serie identifica la caja). */
    public String getGtin() {
        return gtin;
    }

    /** Devuelve el lote al que pertenece la unidad. */
    public Lote getLote() {
        return lote;
    }

    /** Devuelve la empresa donde se encuentra actualmente la unidad. */
    public Empresa getEmpresaActual() {
        return empresaActual;
    }

    /** Devuelve el bulto que contiene a la unidad, si aplica. */
    public Bulto getBulto() {
        return bulto;
    }

    /** Mete la caja en un bulto: debe estar EN_LABORATORIO y sin bulto (R6). */
    public void asignarABulto(Bulto bulto) {
        if (this.estado != EstadoUnidad.EN_LABORATORIO || this.bulto != null) {
            throw new ReglaNegocioException("R6", "La caja " + serie + " no está disponible (estado " + estado
                    + (this.bulto != null ? ", ya está en el bulto " + this.bulto.getCodigo() : "") + ")");
        }
        this.bulto = bulto;
    }

    /** Saca la caja de un bulto desarmado: vuelve a quedar sin bulto, EN_LABORATORIO. */
    public void liberarDeBulto() {
        if (this.estado != EstadoUnidad.EN_LABORATORIO || this.bulto == null) {
            throw transicionInvalida("La caja " + serie + " no está en un bulto armado");
        }
        this.bulto = null;
    }

    /** Salida del viaje de su bulto: EN_LABORATORIO o EN_DEPOSITO → EN_TRANSITO; deja de estar en una empresa. */
    public void salir() {
        if (this.estado != EstadoUnidad.EN_LABORATORIO && this.estado != EstadoUnidad.EN_DEPOSITO) {
            throw transicionInvalida("Una caja en estado " + estado + " no puede salir de viaje");
        }
        this.estado = EstadoUnidad.EN_TRANSITO;
        this.empresaActual = null;
    }

    /** Robo o extravío del viaje (R14): EN_TRANSITO → ROBADA. */
    public void robar() {
        if (this.estado != EstadoUnidad.EN_TRANSITO) {
            throw transicionInvalida("Una caja en estado " + estado + " no puede reportarse robada");
        }
        this.estado = EstadoUnidad.ROBADA;
    }

    /** Recepción conforme del tramo 1 (R8): EN_TRANSITO → EN_DEPOSITO, en la distribuidora. */
    public void recibirEnDeposito(Empresa distribuidora) {
        exigirEstado(EstadoUnidad.EN_TRANSITO, "recibir en depósito");
        this.estado = EstadoUnidad.EN_DEPOSITO;
        this.empresaActual = distribuidora;
    }

    /** Recepción conforme del tramo 2 (R8): EN_TRANSITO → EN_STOCK, en la farmacia. */
    public void recibirEnFarmacia(Empresa farmacia) {
        exigirEstado(EstadoUnidad.EN_TRANSITO, "recibir en farmacia");
        this.estado = EstadoUnidad.EN_STOCK;
        this.empresaActual = farmacia;
    }

    /**
     * Recepción NO conforme de su bulto (R8): EN_TRANSITO → RECHAZADA, en poder
     * de la receptora (donde está físicamente). Qué se hace después con estas
     * cajas lo define el paso 7f.
     */
    public void quedarRechazadaEn(Empresa receptora) {
        exigirEstado(EstadoUnidad.EN_TRANSITO, "rechazar");
        this.estado = EstadoUnidad.RECHAZADA;
        this.empresaActual = receptora;
    }

    /**
     * Aceptación por dictamen (7f): se levantó la cuarentena de su bulto,
     * rechazado solo por precinto roto. RECHAZADA → EN_DEPOSITO (tramo 1) o
     * EN_STOCK (tramo 2), en la empresa que ya la tiene.
     */
    public void aceptarPorDictamen(boolean enDeposito) {
        exigirEstado(EstadoUnidad.RECHAZADA, "aceptar por dictamen");
        this.estado = enDeposito ? EstadoUnidad.EN_DEPOSITO : EstadoUnidad.EN_STOCK;
    }

    /** Dispensación (R11): EN_STOCK → DISPENSADA. */
    public void dispensar() {
        exigirEstado(EstadoUnidad.EN_STOCK, "dispensar");
        this.estado = EstadoUnidad.DISPENSADA;
    }

    /** Anulación de la dispensación dentro de las 2 h (R11): DISPENSADA → EN_STOCK. */
    public void anularDispensa() {
        exigirEstado(EstadoUnidad.DISPENSADA, "anular la dispensación");
        this.estado = EstadoUnidad.EN_STOCK;
    }

    /** Devolución (R14): EN_STOCK → DEVUELTA; no vuelve a dispensarse. */
    public void devolver() {
        exigirEstado(EstadoUnidad.EN_STOCK, "devolver");
        this.estado = EstadoUnidad.DEVUELTA;
    }

    /** Indica si la caja está en poder de la empresa dada. */
    public boolean estaEn(Empresa empresa) {
        return empresaActual != null && empresa != null && empresaActual.getId().equals(empresa.getId());
    }

    /** Lanza TRANSICION_INVALIDA si el estado actual no es el esperado. */
    private void exigirEstado(EstadoUnidad esperado, String accion) {
        if (this.estado != esperado) {
            throw transicionInvalida("Una caja en estado " + estado + " no admite la acción " + accion);
        }
    }

    /** Crea la excepción TRANSICION_INVALIDA. */
    private ReglaNegocioException transicionInvalida(String mensaje) {
        return new ReglaNegocioException("TRANSICION_INVALIDA", mensaje);
    }

    /** Arma el código GS1 de la caja: (01) GTIN (21) serie. */
    public String codigoGS1() {
        return "(01)" + gtin + "(21)" + serie;
    }
}
