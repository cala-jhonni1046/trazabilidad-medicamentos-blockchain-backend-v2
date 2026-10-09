package com.medichain.integracion;

import com.medichain.modules.bulto.Bulto;
import com.medichain.modules.despachologistico.DespachoLogistico;
import com.medichain.modules.enlacecuit.EnlaceCuit;
import com.medichain.modules.lote.Lote;
import com.medichain.modules.medicamento.Medicamento;
import com.medichain.modules.trazabilidad.CadenaEstado;
import com.medichain.modules.trazabilidad.EventoTrazabilidad;
import com.medichain.modules.trazabilidad.TipoEvento;
import com.medichain.modules.trazabilidad.VerificacionCadenaResponseDTO;
import com.medichain.modules.trazabilidad.VerificadorCadena;
import com.medichain.utils.enums.Provincia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test de integración FechasNegocioIT en MediChain (paso B11d).
 * Las 25 fechas y horas del negocio son timestamptz (Instant, UTC) y la cadena
 * de eventos no se rompe por el cambio:
 * <ul>
 *   <li>las columnas de fecha del negocio quedaron timestamptz tras V4;</li>
 *   <li>los eventos NUEVOS que llevan una fecha dentro de sus datos
 *       (VIAJE_CREADO con fechaEstimadaEntrega, RUPTURA_FRIO con
 *       fechaHoraLectura) la escriben en UTC con Z, y la cadena verifica;</li>
 *   <li>una cadena MIXTA (un evento con el formato viejo —LocalDateTime sin Z,
 *       congelado en datos_json— seguido de uno con el formato nuevo) sigue
 *       íntegra: la verificación reinserta datos_json crudo, así que el formato
 *       con el que se guardó no cambia el hash.</li>
 * </ul>
 */
class FechasNegocioIT extends IntegracionBase {

    /** Las 25 columnas de fecha del negocio que V4 pasó a timestamptz (tabla.columna). */
    private static final List<String> COLUMNAS_NEGOCIO = List.of(
            "usuarios.ultimo_login",
            "empresas.fecha_solicitud", "empresas.fecha_habilitacion",
            "inspectores_anmat.fecha_alta", "inspectores_anmat.fecha_baja",
            "enlaces_cuit.fecha_propuesta", "enlaces_cuit.fecha_aceptacion_distribuidor",
            "enlaces_cuit.fecha_aceptacion_farmacia", "enlaces_cuit.fecha_aprobacion",
            "lotes.fecha_liberacion",
            "bultos.fecha_armado",
            "despachos_logisticos.fecha_salida", "despachos_logisticos.fecha_estimada_entrega",
            "telemetria_temperatura.fecha_hora", "telemetria_gps.fecha_hora",
            "recepciones.fecha_hora",
            "dispensaciones.fecha_hora", "dispensaciones.fecha_anulacion",
            "reportes_ciudadanos.fecha_reporte", "reportes_ciudadanos.fecha_cierre",
            "cuarentenas.fecha_inicio", "cuarentenas.fecha_fin",
            "registros_blockchain.proximo_intento", "registros_blockchain.fecha_envio",
            "registros_blockchain.fecha_confirmacion");

    @Autowired
    private VerificadorCadena verificadorCadena;

    @Test
    @DisplayName("Las 25 fechas del negocio son timestamptz (ninguna quedó sin zona tras V4)")
    void columnasDeNegocioEnTimestamptz() {
        List<String> sinZona = jdbc.queryForList(
                "SELECT table_name || '.' || column_name FROM information_schema.columns"
                        + " WHERE table_schema = 'public' AND data_type <> 'timestamp with time zone'"
                        + " AND table_name || '.' || column_name IN ("
                        + "'" + String.join("','", COLUMNAS_NEGOCIO) + "')",
                String.class);
        assertEquals(List.of(), sinZona, "estas columnas de fecha del negocio no quedaron timestamptz");
    }

    @Test
    @DisplayName("Eventos nuevos con fecha en sus datos (VIAJE_CREADO, RUPTURA_FRIO) la llevan en UTC con Z; la cadena verifica")
    void eventosNuevosEnUtcConZ() throws Exception {
        EscenarioIntegracion.Actores actores = escenario.actores(Provincia.TIERRA_DEL_FUEGO);
        Medicamento medicamento = escenario.medicamento(actores);
        EnlaceCuit circuito = escenario.circuitoAprobado(actores);
        Lote lote = escenario.loteLiberado(actores, medicamento, "FN-0001", 3);
        Bulto bulto = escenario.bultoArmado(actores, circuito, lote, 2);
        DespachoLogistico viaje = escenario.viajeEnTransito(actores.adminLaboratorio(), bulto);
        // Lectura fuera de rango → RUPTURA_FRIO; fechaHora con offset obligatorio (UTC).
        mockMvc.perform(post("/api/telemetria-temperatura")
                        .header("Authorization", bearer(actores.adminLaboratorio().getEmail(), EscenarioIntegracion.CLAVE))
                        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(Map.of(
                                "sensorId", "SENSOR-FN", "temperatura", "40", "despachoId", viaje.getId().toString(),
                                "fechaHora", OffsetDateTime.now(ZoneOffset.UTC).toString()))))
                .andExpect(status().isCreated());

        String datosViaje = datosJsonDe(TipoEvento.VIAJE_CREADO);
        assertTrue(datosViaje.matches(".*\"fechaEstimadaEntrega\":\"[^\"]+Z\".*"),
                "VIAJE_CREADO guarda la fecha estimada en UTC con Z: " + datosViaje);
        String datosRuptura = datosJsonDe(TipoEvento.RUPTURA_FRIO);
        assertTrue(datosRuptura.matches(".*\"fechaHoraLectura\":\"[^\"]+Z\".*"),
                "RUPTURA_FRIO guarda la hora de la lectura en UTC con Z: " + datosRuptura);

        assertTrue(verificadorCadena.verificar().isIntegra(), "la cadena con los eventos nuevos es íntegra");
    }

    @Test
    @DisplayName("Cadena mixta: un evento con el formato viejo (sin Z) y uno nuevo (con Z) verifican por igual")
    void cadenaMixtaVerifica() {
        // Evento 1: formato VIEJO (LocalDateTime sin Z, como quedó congelado en datos_json antes de B11d).
        EventoTrazabilidad viejo = insertar(1L, "{\"fechaEstimadaEntrega\":\"2026-12-01T18:00:00.000000\"}",
                EventoTrazabilidad.GENESIS);
        // Evento 2: formato NUEVO (Instant en UTC con Z), encadenado sobre el anterior.
        EventoTrazabilidad nuevo = insertar(2L, "{\"fechaEstimadaEntrega\":\"2026-12-01T21:00:00.000000Z\"}",
                viejo.getHash());
        // La fila de estado debe reflejar el último evento para que la verificación cierre.
        jdbc.update("UPDATE cadena_estado SET ultimo_numero = ?, ultimo_hash = ? WHERE nombre = ?",
                2L, nuevo.getHash(), CadenaEstado.PRINCIPAL);

        VerificacionCadenaResponseDTO resultado = verificadorCadena.verificar();
        assertTrue(resultado.isIntegra(), "cadena mixta íntegra: " + resultado.getMotivo());
        assertEquals(2, resultado.getEventosVerificados());
    }

    /** Lee el datos_json del (único) evento del tipo dado. */
    private String datosJsonDe(TipoEvento tipo) {
        return jdbc.queryForObject("SELECT datos_json FROM eventos_trazabilidad WHERE tipo = ?",
                String.class, tipo.name());
    }

    /**
     * Inserta un evento con su datos_json tal cual (congelado): el hash lo
     * calcula EventoTrazabilidad a partir de ese texto, igual que en la app.
     */
    private EventoTrazabilidad insertar(long numero, String datosJson, String hashAnterior) {
        Instant fechaHora = Instant.parse("2026-12-01T12:00:00.000000Z");
        OffsetDateTime ts = OffsetDateTime.ofInstant(fechaHora, ZoneOffset.UTC);
        EventoTrazabilidad evento = new EventoTrazabilidad(numero, TipoEvento.VIAJE_CREADO, fechaHora,
                "DespachoLogistico", UUID.randomUUID(), datosJson, null, null, hashAnterior);
        jdbc.update("INSERT INTO eventos_trazabilidad (id, numero, tipo, fecha_hora, entidad_tipo, entidad_id,"
                        + " datos_json, hash_anterior, hash, fecha_creacion, fecha_actualizacion, version)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)",
                UUID.randomUUID(), evento.getNumero(), evento.getTipo().name(), ts, evento.getEntidadTipo(),
                evento.getEntidadId(), evento.getDatosJson(), evento.getHashAnterior(), evento.getHash(),
                ts, ts);
        return evento;
    }
}
