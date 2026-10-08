package com.medichain.integracion;

import com.medichain.modules.enlacecuit.EnlaceCuit;
import com.medichain.modules.lote.Lote;
import com.medichain.modules.medicamento.Medicamento;
import com.medichain.utils.enums.Provincia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test de integración DatosSensiblesIT en MediChain (R13, Ley 25.326).
 * Lo que solo se ve en la base real:
 * <ul>
 *   <li>Los enums se guardan por NOMBRE en columnas de texto
 *       (@Enumerated(EnumType.STRING)), nunca por posición: reordenar un enum
 *       no puede cambiar el significado de los datos guardados.</li>
 *   <li>El DNI completo del paciente que se informa al dispensar no queda en
 *       NINGUNA columna de texto de NINGUNA tabla (se busca en todas, leídas
 *       del catálogo), ni en la respuesta, ni en el log (con el log de
 *       MediChain en DEBUG durante la prueba). Solo queda enmascarado.</li>
 *   <li>El evento DISPENSACION no lleva obra social, afiliado ni receta.</li>
 * </ul>
 */
@ExtendWith(OutputCaptureExtension.class)
class DatosSensiblesIT extends IntegracionBase {

    /** DNI de prueba: no es el de ningún usuario del escenario. */
    private static final String DNI = "40123006";

    @Test
    @DisplayName("Enums por nombre: todas las columnas estado/rol/tipo/provincia/alcance/motivo son texto, ninguna smallint")
    void enumsComoTexto() {
        EscenarioIntegracion.Actores actores = escenario.actores(Provincia.CORRIENTES);
        escenario.loteLiberado(actores, escenario.medicamento(actores), "DS-0002", 1);

        List<Map<String, Object>> columnas = jdbc.queryForList("SELECT table_name, column_name, data_type FROM "
                + "information_schema.columns WHERE table_schema = 'public' "
                + "AND column_name IN ('estado', 'rol', 'tipo', 'provincia', 'alcance', 'motivo')");
        assertTrue(columnas.size() >= 10, "columnas de enums encontradas: " + columnas);
        for (Map<String, Object> columna : columnas) {
            assertEquals("character varying", columna.get("data_type"), "columna de enum: " + columna);
        }
        assertEquals(List.of(), jdbc.queryForList("SELECT table_name || '.' || column_name FROM "
                + "information_schema.columns WHERE table_schema = 'public' AND data_type = 'smallint'", String.class),
                "ninguna columna smallint (así guardaría Hibernate un enum ORDINAL)");
        // Los valores guardados son los nombres de los enums.
        assertEquals("LIBERADO", jdbc.queryForObject("SELECT estado FROM lotes", String.class));
        assertEquals("CORRIENTES", jdbc.queryForObject(
                "SELECT DISTINCT provincia FROM empresas", String.class));
        assertEquals(List.of("DISTRIBUIDOR", "FARMACIA", "LABORATORIO"),
                jdbc.queryForList("SELECT tipo FROM empresas ORDER BY tipo", String.class));
    }

    @Test
    @DisplayName("R13: el DNI completo del paciente no queda en ninguna tabla, ni en la respuesta ni en el log; solo *****006")
    void dniCompletoEnNingunLado(CapturedOutput salida) throws Exception {
        EscenarioIntegracion.Actores actores = escenario.actores(Provincia.CORRIENTES);
        Medicamento medicamento = escenario.medicamento(actores);
        EnlaceCuit circuito = escenario.circuitoAprobado(actores);
        Lote lote = escenario.loteLiberado(actores, medicamento, "DS-0001", 1);
        escenario.cajasEnFarmacia(actores, circuito, lote, 1);
        String serie = jdbc.queryForObject("SELECT serie FROM unidades_trazables WHERE lote_id = ?", String.class,
                lote.getId());
        Map<String, Object> pedido = Map.of("gtin", medicamento.getGtin(), "serie", serie, "particular", false,
                "obraSocial", "OBRA-SOCIAL-SECRETA", "numeroAfiliado", "AFILIADO-SECRETO-77",
                "numeroReceta", "RECETA-SECRETA-99", "dni", DNI);

        LoggingSystem logging = LoggingSystem.get(getClass().getClassLoader());
        logging.setLogLevel("com.medichain", LogLevel.DEBUG);
        String respuesta;
        try {
            respuesta = mockMvc.perform(post("/api/dispensaciones")
                            .header("Authorization", bearer(actores.adminFarmacia().getEmail(), EscenarioIntegracion.CLAVE))
                            .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(pedido)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
        } finally {
            logging.setLogLevel("com.medichain", null);
        }

        assertFalse(respuesta.contains(DNI), "la respuesta no devuelve el DNI completo");
        assertFalse(salida.getAll().contains(DNI), "el log no tiene el DNI completo");
        assertEquals("*****006", jdbc.queryForObject("SELECT dni_enmascarado FROM dispensaciones", String.class));
        assertEquals(List.of(), tablasQueContienen(DNI), "ninguna columna de texto guarda el DNI completo");

        String evento = jdbc.queryForObject(
                "SELECT datos_json FROM eventos_trazabilidad WHERE tipo = 'DISPENSACION'", String.class);
        for (String dato : List.of(DNI, "006", "OBRA-SOCIAL-SECRETA", "AFILIADO-SECRETO-77", "RECETA-SECRETA-99")) {
            assertFalse(evento.contains(dato), "el evento DISPENSACION no lleva " + dato + ": " + evento);
        }
    }

    /**
     * Busca el valor en TODAS las columnas de texto de TODAS las tablas del
     * esquema (leídas de information_schema). Devuelve "tabla.columna" donde aparece.
     */
    private List<String> tablasQueContienen(String valor) {
        List<Map<String, Object>> columnas = jdbc.queryForList("SELECT table_name, column_name FROM "
                + "information_schema.columns WHERE table_schema = 'public' "
                + "AND data_type IN ('character varying', 'text', 'character')");
        assertTrue(columnas.size() > 50, "se recorren las columnas de texto de todas las tablas: " + columnas.size());
        return columnas.stream()
                .filter(c -> {
                    String sql = "SELECT count(*) FROM \"" + c.get("table_name") + "\" WHERE \""
                            + c.get("column_name") + "\" LIKE ?";
                    Integer cantidad = jdbc.queryForObject(sql, Integer.class, "%" + valor + "%");
                    return cantidad != null && cantidad > 0;
                })
                .map(c -> c.get("table_name") + "." + c.get("column_name"))
                .toList();
    }
}
