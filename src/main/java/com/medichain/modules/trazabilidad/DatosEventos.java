package com.medichain.modules.trazabilidad;

import com.medichain.modules.bulto.Bulto;
import com.medichain.modules.cuarentena.Cuarentena;
import com.medichain.modules.despachologistico.DespachoLogistico;
import com.medichain.modules.dispensacion.Dispensacion;
import com.medichain.modules.empresa.Empresa;
import com.medichain.modules.enlacecuit.EnlaceCuit;
import com.medichain.modules.inspectoranmat.InspectorAnmat;
import com.medichain.modules.lote.Lote;
import com.medichain.modules.medicamento.Medicamento;
import com.medichain.modules.recepcion.Recepcion;
import com.medichain.modules.reporteciudadano.ReporteCiudadano;
import com.medichain.modules.telemetriatemperatura.TelemetriaTemperatura;
import com.medichain.modules.unidadtrazable.UnidadTrazable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.Map;

/**
 * Fábrica DatosEventos en MediChain.
 * Lista blanca de los datos que entran en cada tipo de evento. Cada
 * método copia campo por campo SOLO lo permitido: nunca se serializa una
 * entidad entera. Prohibido en cualquier evento (R13 y privacidad):
 * DNI, obra social, número de afiliado, receta, datos del paciente,
 * patente y chofer, textos libres de descripción. Los motivos libres
 * (rechazo, suspensión) van solo como SHA-256: el texto queda en la
 * entidad y la cadena prueba que no se modificó.
 */
public final class DatosEventos {

    private DatosEventos() {
    }

    /** SOLICITUD_HABILITACION: identificación pública de la empresa y hash de su PDF. */
    public static Map<String, Object> solicitudHabilitacion(Empresa empresa) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("cuit", empresa.getCuit());
        datos.put("razonSocial", empresa.getRazonSocial());
        datos.put("gln", empresa.getGln());
        datos.put("tipo", empresa.getTipo());
        datos.put("provincia", empresa.getProvincia());
        datos.put("documentoHash", empresa.getDocumentoHash());
        return datos;
    }

    /** ALTA_INSPECTOR: legajo, provincia y cuenta del inspector (sin DNI ni email). */
    public static Map<String, Object> altaInspector(InspectorAnmat inspector) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("legajo", inspector.getLegajo());
        datos.put("provincia", inspector.getProvincia());
        datos.put("usuarioId", inspector.getUsuario() != null ? inspector.getUsuario().getId() : null);
        return datos;
    }

    /** CIRCUITO_PROPUESTO: código y las tres empresas del circuito. */
    public static Map<String, Object> circuitoPropuesto(EnlaceCuit circuito) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("codigo", circuito.getCodigo());
        datos.put("laboratorioId", circuito.getLaboratorio().getId());
        datos.put("distribuidorId", circuito.getDistribuidor().getId());
        datos.put("farmaciaId", circuito.getFarmacia().getId());
        return datos;
    }

    /** MEDICAMENTO_REGISTRADO: GTIN, nombre comercial, principio activo y laboratorio. */
    public static Map<String, Object> medicamentoRegistrado(Medicamento medicamento) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("gtin", medicamento.getGtin());
        datos.put("nombreComercial", medicamento.getNombreComercial());
        datos.put("principioActivo", medicamento.getPrincipioActivo());
        datos.put("laboratorioId", medicamento.getLaboratorio().getId());
        return datos;
    }

    /**
     * LOTE_REGISTRADO: código, medicamento, GTIN, fechas, cantidad de series,
     * seriesHash (HashUtil.seriesHash) y si las series vinieron en lista o
     * las generó el servidor. Nunca la lista de series.
     */
    public static Map<String, Object> loteRegistrado(Lote lote, String seriesHash, String origenSeries) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("codigo", lote.getCodigo());
        datos.put("medicamentoId", lote.getMedicamento().getId());
        datos.put("gtin", lote.getMedicamento().getGtin());
        datos.put("fechaFabricacion", lote.getFechaFabricacion());
        datos.put("fechaVencimiento", lote.getFechaVencimiento());
        datos.put("cantidadSeries", lote.getCantidad());
        datos.put("seriesHash", seriesHash);
        datos.put("origenSeries", origenSeries);
        return datos;
    }

    /** LOTE_LIBERADO: código, si es biológico, cómo se liberó y el inspector (si fue un inspector). */
    public static Map<String, Object> loteLiberado(Lote lote, String liberadoComo, UUID inspectorId) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("codigo", lote.getCodigo());
        datos.put("biologico", lote.getMedicamento().getBiologico());
        datos.put("liberadoComo", liberadoComo);
        datos.put("inspectorId", inspectorId);
        return datos;
    }

    /**
     * INTENTO_SERIE_INVALIDA: el lote no se creó. Resumen de los problemas,
     * una muestra de hasta 10 series y el hash de la lista recibida.
     */
    public static Map<String, Object> intentoSerieInvalida(String codigoLote, Medicamento medicamento,
                                                           int cantidadRecibida, int invalidas,
                                                           int duplicadasEnLote, int yaExistentes,
                                                           List<String> muestra, String seriesHash) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("codigoLote", codigoLote);
        datos.put("medicamentoId", medicamento.getId());
        datos.put("gtin", medicamento.getGtin());
        datos.put("cantidadRecibida", cantidadRecibida);
        datos.put("invalidas", invalidas);
        datos.put("duplicadasEnLote", duplicadasEnLote);
        datos.put("yaExistentes", yaExistentes);
        datos.put("muestra", muestra);
        datos.put("seriesHash", seriesHash);
        return datos;
    }

    /** BULTO_ARMADO: código, lote, circuito, farmacia de destino, cantidad, precinto y hash de las series. */
    public static Map<String, Object> bultoArmado(Bulto bulto, String seriesHash) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("codigo", bulto.getCodigo());
        datos.put("loteId", bulto.getLote().getId());
        datos.put("circuitoId", bulto.getDestino().getId());
        datos.put("farmaciaId", bulto.getDestino().getFarmacia().getId());
        datos.put("cantidad", bulto.getCantidad());
        datos.put("precinto", bulto.getPrecinto());
        datos.put("seriesHash", seriesHash);
        return datos;
    }

    /** BULTO_DESARMADO: código y cantidad de cajas liberadas. */
    public static Map<String, Object> bultoDesarmado(Bulto bulto) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("codigo", bulto.getCodigo());
        datos.put("cantidad", bulto.getCantidad());
        return datos;
    }

    /** VIAJE_CREADO: código, tramo, origen, paradas, códigos de bultos y fecha estimada (sin patente ni chofer). */
    public static Map<String, Object> viajeCreado(DespachoLogistico viaje) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("codigo", viaje.getCodigo());
        datos.put("tramo", viaje.getTramo());
        datos.put("origenId", viaje.getOrigen().getId());
        datos.put("paradas", viaje.paradas().stream().map(Empresa::getId).toList());
        datos.put("bultos", codigosDeBultos(viaje.getBultos()));
        datos.put("fechaEstimadaEntrega", viaje.getFechaEstimadaEntrega());
        return datos;
    }

    /** VIAJE_SALIDA: código, cantidad y códigos de los bultos que salieron. */
    public static Map<String, Object> viajeSalida(DespachoLogistico viaje) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("codigo", viaje.getCodigo());
        datos.put("cantidadBultos", viaje.getBultos().size());
        datos.put("bultos", codigosDeBultos(viaje.getBultos()));
        return datos;
    }

    /** VIAJE_CANCELADO: código, bultos liberados y hash del motivo. */
    public static Map<String, Object> viajeCancelado(DespachoLogistico viaje, String motivo) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("codigo", viaje.getCodigo());
        datos.put("bultos", codigosDeBultos(viaje.getBultos()));
        datos.put("motivoHash", HashUtil.sha256Hex(motivo));
        return datos;
    }

    /** ROBO_EXTRAVIO: código del viaje, cantidades, códigos de bultos y hash del motivo. */
    public static Map<String, Object> roboExtravio(DespachoLogistico viaje, int cantidadCajas, String motivo) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("codigo", viaje.getCodigo());
        datos.put("cantidadBultos", viaje.getBultos().size());
        datos.put("cantidadCajas", cantidadCajas);
        datos.put("bultos", codigosDeBultos(viaje.getBultos()));
        datos.put("motivoHash", HashUtil.sha256Hex(motivo));
        return datos;
    }

    /** Códigos de los bultos, ordenados (para que el JSON sea determinista). */
    private static List<String> codigosDeBultos(Collection<Bulto> bultos) {
        return bultos.stream().map(Bulto::getCodigo).sorted().toList();
    }

    /**
     * RUPTURA_FRIO (R9): la lectura y los bultos NUEVOS afectados, cada uno con
     * el rango de SU medicamento.
     */
    public static Map<String, Object> rupturaFrio(TelemetriaTemperatura lectura, List<Bulto> afectados) {
        List<Map<String, Object>> bultos = new ArrayList<>();
        for (Bulto bulto : afectados) {
            Map<String, Object> item = new HashMap<>();
            item.put("codigo", bulto.getCodigo());
            item.put("temperaturaMinima", bulto.getLote().getMedicamento().getTemperaturaMinima());
            item.put("temperaturaMaxima", bulto.getLote().getMedicamento().getTemperaturaMaxima());
            bultos.add(item);
        }
        Map<String, Object> datos = new HashMap<>();
        datos.put("sensorId", lectura.getSensorId());
        datos.put("temperatura", lectura.getTemperatura());
        datos.put("fechaHoraLectura", lectura.getFechaHora());
        datos.put("bultosAfectados", bultos);
        return datos;
    }

    /**
     * BULTO_RECIBIDO / BULTO_RECHAZADO (R8): bulto, viaje, tramo, receptora y lo
     * verificado; si fue rechazado, los motivos (códigos fijos). Nunca la observación libre.
     */
    public static Map<String, Object> recepcion(Recepcion recepcion) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("codigo", recepcion.getBulto().getCodigo());
        datos.put("viaje", recepcion.getDespacho().getCodigo());
        datos.put("tramo", recepcion.getDespacho().getTramo());
        datos.put("receptoraId", recepcion.getReceptora().getId());
        datos.put("precintoIntacto", recepcion.getPrecintoIntacto());
        datos.put("cantidadVerificada", recepcion.getCantidadVerificada());
        datos.put("temperatura", recepcion.getTemperatura());
        datos.put("motivos", recepcion.getConforme() ? null : recepcion.motivos());
        return datos;
    }

    /** BULTO_INEXISTENTE: el código escaneado (ya validado como código) y quién lo escaneó. */
    public static Map<String, Object> bultoInexistente(String codigoEscaneado, UUID receptoraId) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("codigoEscaneado", codigoEscaneado);
        datos.put("receptoraId", receptoraId);
        return datos;
    }

    /** BULTO_DUPLICADO: bulto que la empresa ya había recibido, su estado actual y quién lo reescaneó. */
    public static Map<String, Object> bultoDuplicado(Bulto bulto, UUID receptoraId) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("codigo", bulto.getCodigo());
        datos.put("estadoActual", bulto.getEstado());
        datos.put("receptoraId", receptoraId);
        return datos;
    }

    /**
     * VIAJE_FINALIZADO (R7): código, tramo, bultos recibidos y rechazados, y el
     * resumen de temperatura del viaje (lecturas, mínima, máxima y fuera de rango).
     */
    public static Map<String, Object> viajeFinalizado(DespachoLogistico viaje, int recibidos, int rechazados,
                                                      List<TelemetriaTemperatura> lecturas) {
        Map<String, Object> resumen = new HashMap<>();
        resumen.put("lecturas", lecturas.size());
        resumen.put("fueraDeRango", (int) lecturas.stream().filter(l -> Boolean.TRUE.equals(l.getFueraDeRango())).count());
        resumen.put("minima", lecturas.stream().map(TelemetriaTemperatura::getTemperatura).min(BigDecimal::compareTo).orElse(null));
        resumen.put("maxima", lecturas.stream().map(TelemetriaTemperatura::getTemperatura).max(BigDecimal::compareTo).orElse(null));
        Map<String, Object> datos = new HashMap<>();
        datos.put("codigo", viaje.getCodigo());
        datos.put("tramo", viaje.getTramo());
        datos.put("recibidos", recibidos);
        datos.put("rechazados", rechazados);
        datos.put("temperatura", resumen);
        return datos;
    }

    /** DISPENSACION: solo caja (id, GTIN, serie) y farmacia. NINGÚN dato del paciente ni de la receta (R13). */
    public static Map<String, Object> dispensacion(Dispensacion dispensacion) {
        return caja(dispensacion.getUnidadTrazable(), dispensacion.getFarmacia().getId());
    }

    /** ANULACION_DISPENSA: la caja, la farmacia y el hash del motivo (nunca el texto). */
    public static Map<String, Object> anulacionDispensa(Dispensacion dispensacion, String motivo) {
        Map<String, Object> datos = caja(dispensacion.getUnidadTrazable(), dispensacion.getFarmacia().getId());
        datos.put("motivoHash", HashUtil.sha256Hex(motivo));
        return datos;
    }

    /** DEVOLUCION (R14): la caja, la farmacia, el motivo (código fijo) y el hash de la observación. */
    public static Map<String, Object> devolucion(UnidadTrazable caja, UUID farmaciaId, Enum<?> motivo, String observacion) {
        Map<String, Object> datos = caja(caja, farmaciaId);
        datos.put("motivo", motivo);
        datos.put("observacionHash", observacion != null && !observacion.isBlank() ? HashUtil.sha256Hex(observacion) : null);
        return datos;
    }

    /** INTENTO_DUPLICADO (R11): una farmacia intentó dispensar una caja ya dispensada. */
    public static Map<String, Object> intentoDuplicado(UnidadTrazable caja, UUID farmaciaIntentoId) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("gtin", caja.getGtin());
        datos.put("serie", caja.getSerie());
        datos.put("farmaciaIntentoId", farmaciaIntentoId);
        return datos;
    }

    /** SERIE_ROBADA (R14): apareció una caja robada; dónde (verificación pública o dispensación) y de qué viaje. */
    public static Map<String, Object> serieRobada(UnidadTrazable caja, String origen) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("gtin", caja.getGtin());
        datos.put("serie", caja.getSerie());
        datos.put("origen", origen);
        datos.put("viaje", caja.getBulto() != null && caja.getBulto().getViajeActual() != null
                ? caja.getBulto().getViajeActual().getCodigo() : null);
        return datos;
    }

    /** SERIE_INEXISTENTE: alguien verificó una serie que no existe para un GTIN registrado. */
    public static Map<String, Object> serieInexistente(String gtin, String serie, String origen) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("gtin", gtin);
        datos.put("serie", serie);
        datos.put("origen", origen);
        return datos;
    }

    /** Datos comunes de una caja: id, GTIN, serie y la farmacia que actúa. */
    private static Map<String, Object> caja(UnidadTrazable caja, UUID farmaciaId) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("unidadId", caja.getId());
        datos.put("gtin", caja.getGtin());
        datos.put("serie", caja.getSerie());
        datos.put("farmaciaId", farmaciaId);
        return datos;
    }

    /**
     * CUARENTENA: alcance, medida, motivo, si es automática, provincia, objeto,
     * códigos de bultos, inspector (si es manual) y reporte de origen (si nació
     * de uno). Nunca la descripción libre.
     */
    public static Map<String, Object> cuarentena(Cuarentena cuarentena) {
        Map<String, Object> datos = objetoDeLaMedida(cuarentena);
        datos.put("tipo", cuarentena.getTipo());
        datos.put("automatica", cuarentena.getAutomatica());
        datos.put("provincia", cuarentena.getProvincia());
        datos.put("inspectorId", cuarentena.getInspectorRevisor() != null ? cuarentena.getInspectorRevisor().getId() : null);
        datos.put("reporte", cuarentena.getReporteOrigen() != null ? cuarentena.getReporteOrigen().getCodigo() : null);
        return datos;
    }

    /** CUARENTENA_TOMADA: inspector que toma la medida para dictaminarla y su provincia. */
    public static Map<String, Object> cuarentenaTomada(Cuarentena cuarentena, InspectorAnmat inspector) {
        Map<String, Object> datos = objetoDeLaMedida(cuarentena);
        datos.put("inspectorId", inspector.getId());
        datos.put("provinciaInspector", inspector.getProvincia());
        return datos;
    }

    /**
     * CUARENTENA_LEVANTADA (R12): objeto de la medida, inspector, hash del
     * fundamento y efecto (LOTE_LIBERADO o BULTOS_ACEPTADOS).
     */
    public static Map<String, Object> cuarentenaLevantada(Cuarentena cuarentena, String fundamento, String efecto) {
        Map<String, Object> datos = objetoDeLaMedida(cuarentena);
        datos.put("inspectorId", cuarentena.getInspector().getId());
        datos.put("fundamentoHash", HashUtil.sha256Hex(fundamento));
        datos.put("efecto", efecto);
        return datos;
    }

    /** RECALL (R12): objeto de la medida, código del lote si es de LOTE, inspector, hash del fundamento y cajas afectadas. */
    public static Map<String, Object> recall(Cuarentena cuarentena, String fundamento, long cajasAfectadas) {
        Map<String, Object> datos = objetoDeLaMedida(cuarentena);
        datos.put("loteCodigo", cuarentena.getLote() != null ? cuarentena.getLote().getCodigo() : null);
        datos.put("inspectorId", cuarentena.getInspector().getId());
        datos.put("fundamentoHash", HashUtil.sha256Hex(fundamento));
        datos.put("cajasAfectadas", cajasAfectadas);
        return datos;
    }

    /** Datos comunes de una medida: alcance, motivo, lote o viaje y códigos de bultos. */
    private static Map<String, Object> objetoDeLaMedida(Cuarentena cuarentena) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("alcance", cuarentena.getAlcance());
        datos.put("motivo", cuarentena.getMotivo());
        datos.put("loteId", cuarentena.getLote() != null ? cuarentena.getLote().getId() : null);
        datos.put("despachoId", cuarentena.getDespacho() != null ? cuarentena.getDespacho().getId() : null);
        datos.put("bultos", cuarentena.getBultos().isEmpty() ? null : codigosDeBultos(cuarentena.getBultos()));
        return datos;
    }

    /**
     * REPORTE_CIUDADANO: código, lo escaneado (GTIN + serie), motivo, provincia
     * declarada y si la caja existe. NUNCA el paciente ni la descripción (R13).
     */
    public static Map<String, Object> reporteCiudadano(ReporteCiudadano reporte) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("codigo", reporte.getCodigo());
        datos.put("gtin", reporte.getGtinReportado());
        datos.put("serie", reporte.getSerieReportada());
        datos.put("motivo", reporte.getMotivo());
        datos.put("provincia", reporte.getProvincia());
        datos.put("cajaExiste", reporte.getUnidadTrazable() != null);
        return datos;
    }

    /** REPORTE_TOMADO: código e inspector que investiga. */
    public static Map<String, Object> reporteTomado(ReporteCiudadano reporte, InspectorAnmat inspector) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("codigo", reporte.getCodigo());
        datos.put("inspectorId", inspector.getId());
        return datos;
    }

    /** REPORTE_CERRADO: código, inspector, hash de la conclusión y medidas que se abrieron desde el reporte. */
    public static Map<String, Object> reporteCerrado(ReporteCiudadano reporte, InspectorAnmat inspector,
                                                     String conclusion, List<UUID> cuarentenas) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("codigo", reporte.getCodigo());
        datos.put("inspectorId", inspector.getId());
        datos.put("conclusionHash", HashUtil.sha256Hex(conclusion));
        datos.put("cuarentenas", cuarentenas.isEmpty() ? null : cuarentenas);
        return datos;
    }

    /** SOLICITUD_TOMADA / SOLICITUD_ASIGNADA: inspector revisor y su provincia. */
    public static Map<String, Object> revisorAsignado(InspectorAnmat inspector) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("inspectorId", inspector.getId());
        datos.put("provinciaInspector", inspector.getProvincia());
        return datos;
    }

    /** HABILITACION_APROBADA: inspector y número de habilitación. */
    public static Map<String, Object> habilitacionAprobada(Empresa empresa, InspectorAnmat inspector) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("inspectorId", inspector.getId());
        datos.put("numeroHabilitacion", empresa.getNumeroHabilitacion());
        return datos;
    }

    /** HABILITACION_RECHAZADA: inspector y hash del motivo (nunca el texto). */
    public static Map<String, Object> habilitacionRechazada(InspectorAnmat inspector, String motivo) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("inspectorId", inspector.getId());
        datos.put("motivoHash", HashUtil.sha256Hex(motivo));
        return datos;
    }

    /** EMPRESA_SUSPENDIDA: hash del motivo y cantidad de circuitos suspendidos en cascada. */
    public static Map<String, Object> empresaSuspendida(String motivo, int circuitosSuspendidos) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("motivoHash", HashUtil.sha256Hex(motivo));
        datos.put("circuitosSuspendidos", circuitosSuspendidos);
        return datos;
    }

    /** EMPRESA_REHABILITADA: cantidad de circuitos que volvieron a APROBADO. */
    public static Map<String, Object> empresaRehabilitada(int circuitosRehabilitados) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("circuitosRehabilitados", circuitosRehabilitados);
        return datos;
    }

    /** CIRCUITO_SUSPENDIDO / CIRCUITO_REHABILITADO por cascada de una empresa. */
    public static Map<String, Object> circuitoPorEmpresa(EnlaceCuit circuito, Empresa empresa, String causa) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("codigo", circuito.getCodigo());
        datos.put("empresaId", empresa.getId());
        datos.put("causa", causa);
        return datos;
    }

    /** BAJA_INSPECTOR: legajo, provincia y solicitudes que volvieron a la bandeja. */
    public static Map<String, Object> bajaInspector(InspectorAnmat inspector, List<UUID> empresasLiberadas) {
        Map<String, Object> liberadas = new HashMap<>();
        liberadas.put("cantidad", empresasLiberadas.size());
        liberadas.put("empresaIds", empresasLiberadas);
        Map<String, Object> datos = new HashMap<>();
        datos.put("legajo", inspector.getLegajo());
        datos.put("provincia", inspector.getProvincia());
        datos.put("solicitudesLiberadas", liberadas);
        return datos;
    }

    /** REACTIVACION_INSPECTOR: legajo y provincia. */
    public static Map<String, Object> reactivacionInspector(InspectorAnmat inspector) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("legajo", inspector.getLegajo());
        datos.put("provincia", inspector.getProvincia());
        return datos;
    }

    /** CIRCUITO_ACEPTADO: empresa que aceptó, su parte y si con esto quedó completo. */
    public static Map<String, Object> circuitoAceptado(Empresa empresa, String parte, boolean completo) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("empresaId", empresa.getId());
        datos.put("parte", parte);
        datos.put("completo", completo);
        return datos;
    }

    /** CIRCUITO_RECHAZADO: quién rechazó (empresa o inspector) y hash del motivo. */
    public static Map<String, Object> circuitoRechazado(EnlaceCuit circuito, UUID empresaId, UUID inspectorId,
                                                        String motivo) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("rechazadoPor", circuito.getRechazadoPor());
        datos.put("empresaId", empresaId);
        datos.put("inspectorId", inspectorId);
        datos.put("motivoHash", HashUtil.sha256Hex(motivo));
        return datos;
    }

    /** CIRCUITO_APROBADO: inspector que aprobó. */
    public static Map<String, Object> circuitoAprobado(InspectorAnmat inspector) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("inspectorId", inspector.getId());
        return datos;
    }

    /**
     * CIRCUITO_SUSPENDIDO / CIRCUITO_REHABILITADO manual: causa MANUAL,
     * inspector (null si actuó la Sede: queda como actor) y hash del motivo (si hay).
     */
    public static Map<String, Object> circuitoManual(UUID inspectorId, String motivo) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("causa", "MANUAL");
        datos.put("inspectorId", inspectorId);
        datos.put("motivoHash", motivo != null ? HashUtil.sha256Hex(motivo) : null);
        return datos;
    }
}
