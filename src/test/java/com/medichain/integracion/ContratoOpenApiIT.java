package com.medichain.integracion;

import com.medichain.modules.enlacecuit.EnlaceCuit;
import com.medichain.modules.lote.Lote;
import com.medichain.modules.medicamento.Medicamento;
import com.medichain.utils.enums.Provincia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test de integración ContratoOpenApiIT en MediChain (paso B11a).
 * El contrato OpenAPI (/api-docs) es lo que usa el frontend para generar su
 * cliente; este test lo cuida desde tres lados:
 * <ul>
 *   <li><b>Reglas del contrato:</b> operationId explícito y único, resumen y
 *       descripción con roles (o "Público"), un tag del flujo y en orden, el
 *       código de éxito real (201 en las altas, 202 en /anclar), los errores con
 *       ErrorResponseDTO según los criterios de @RespuestasError, enums como
 *       esquemas con nombre, required en los ResponseDTO y páginas {content, page}.</li>
 *   <li><b>Archivo versionado:</b> docs/openapi.json es igual al contrato
 *       generado. Para regenerarlo: ./mvnw verify -Dit.test=ContratoOpenApiIT
 *       -Dmedichain.contrato.actualizar=true</li>
 *   <li><b>Respuestas reales:</b> un recorrido completo y cada respuesta
 *       validada contra su esquema (lo required viene y no es null; los enums
 *       están en su lista).</li>
 * </ul>
 */
class ContratoOpenApiIT extends IntegracionBase {

    private static final Path CONTRATO_VERSIONADO = Path.of("docs/openapi.json");
    private static final Pattern OPERATION_ID = Pattern.compile("[a-z][A-Za-z]+");

    /** Tags en el orden del flujo (el mismo que OpenApiConfig). */
    private static final List<String> TAGS = List.of("Registro público", "Autenticación", "Empresas",
            "Inspectores ANMAT", "Usuarios", "Circuitos", "Medicamentos", "Lotes", "Unidades trazables", "Bultos",
            "Viajes", "Telemetría de temperatura", "Telemetría GPS", "Recepciones", "Dispensaciones", "Cuarentenas",
            "Reportes ciudadanos", "Verificación pública", "Eventos de trazabilidad", "Registros blockchain");

    /** Altas que responden 201 (ResponseEntity.status(HttpStatus.CREATED) en el controller). */
    private static final Set<String> ALTAS = Set.of("registrarEmpresa", "registrarPaciente", "armarBulto",
            "abrirCuarentena", "crearViaje", "dispensarCaja", "proponerCircuito", "crearInspector", "registrarLote",
            "registrarMedicamento", "recibirBulto", "reportarCaja", "registrarLecturaGps",
            "registrarLecturaTemperatura", "crearUsuario");

    /** Operaciones públicas (sin token). */
    private static final Set<String> PUBLICAS = Set.of("iniciarSesion", "registrarEmpresa", "registrarPaciente",
            "verificarCaja");

    @Test
    @DisplayName("Reglas del contrato: operationId, roles, tags en orden, éxito real, errores con ErrorResponseDTO, enums con nombre, required")
    void reglasDelContrato() throws Exception {
        JsonNode contrato = contrato();
        List<String> problemas = new ArrayList<>();
        Set<String> operationIds = new HashSet<>();
        int operaciones = 0;
        for (String ruta : contrato.path("paths").propertyNames()) {
            JsonNode metodos = contrato.path("paths").path(ruta);
            for (String metodo : metodos.propertyNames()) {
                JsonNode op = metodos.path(metodo);
                operaciones++;
                String id = op.path("operationId").asString();
                String donde = metodo.toUpperCase() + " " + ruta + " (" + id + ")";
                if (!OPERATION_ID.matcher(id).matches() || !operationIds.add(id)) {
                    problemas.add(donde + ": operationId ausente, con sufijo _N o repetido");
                }
                if (op.path("summary").asString().isBlank()) {
                    problemas.add(donde + ": sin summary");
                }
                String descripcion = op.path("description").asString();
                boolean publica = PUBLICAS.contains(id);
                if (!descripcion.contains(publica ? "Público" : "Roles:")) {
                    problemas.add(donde + ": la descripción debe decir " + (publica ? "'Público'" : "'Roles:'"));
                }
                if (op.path("tags").size() != 1 || !TAGS.contains(op.path("tags").get(0).asString())) {
                    problemas.add(donde + ": debe tener exactamente un tag del flujo");
                }
                problemas.addAll(revisarRespuestas(donde, id, ruta, metodo, op, publica));
            }
        }
        assertEquals(92, operaciones, "cantidad de operaciones del contrato");

        List<String> tags = new ArrayList<>();
        contrato.path("tags").forEach(tag -> tags.add(tag.path("name").asString()));
        assertEquals(TAGS, tags, "tags en el orden del flujo");

        JsonNode esquemas = contrato.path("components").path("schemas");
        for (String nombre : esquemas.propertyNames()) {
            JsonNode esquema = esquemas.path(nombre);
            for (String propiedad : esquema.path("properties").propertyNames()) {
                if (esquema.path("properties").path(propiedad).has("enum")) {
                    problemas.add(nombre + "." + propiedad + ": enum en línea (debe ser un esquema con nombre)");
                }
            }
            if ((nombre.endsWith("ResponseDTO") || nombre.equals("PageMetadata")) && esquema.path("required").isEmpty()) {
                problemas.add(nombre + ": sin campos required");
            }
            if (nombre.startsWith("Page") && !nombre.startsWith("PagedModel") && !nombre.equals("PageMetadata")) {
                problemas.add(nombre + ": página con el formato viejo de Spring (debe ser PagedModel {content, page})");
            }
        }
        assertTrue(esquemas.has("ErrorResponseDTO") && esquemas.has("ErrorCampoDTO"), "esquemas de error");
        assertTrue(problemas.isEmpty(), "Problemas del contrato:\n  " + String.join("\n  ", problemas));
    }

    @Test
    @DisplayName("docs/openapi.json (el que usa el frontend) es igual al contrato generado")
    void contratoVersionadoAlDia() throws Exception {
        String generado = JSON.writerWithDefaultPrettyPrinter().writeValueAsString(contrato()) + "\n";
        if (Boolean.getBoolean("medichain.contrato.actualizar")) {
            Files.writeString(CONTRATO_VERSIONADO, generado, StandardCharsets.UTF_8);
            return;
        }
        String regenerar = " Regeneralo con: ./mvnw verify -Dit.test=ContratoOpenApiIT -Dmedichain.contrato.actualizar=true"
                + " y versionalo junto con el cambio de la API (avisale al frontend).";
        assertTrue(Files.exists(CONTRATO_VERSIONADO), "Falta " + CONTRATO_VERSIONADO + "." + regenerar);
        JsonNode versionado = JSON.readTree(Files.readString(CONTRATO_VERSIONADO, StandardCharsets.UTF_8));
        assertEquals(versionado, JSON.readTree(generado), CONTRATO_VERSIONADO + " está desactualizado." + regenerar);
    }

    @Test
    @DisplayName("Respuestas reales de un recorrido completo cumplen su esquema (required no null, enums publicados)")
    void respuestasRealesCumplenElContrato() throws Exception {
        JsonNode contrato = contrato();
        ValidadorContrato validador = new ValidadorContrato(contrato);
        List<String> errores = new ArrayList<>();
        EscenarioIntegracion.Actores actores = escenario.actores(Provincia.MENDOZA);
        Medicamento medicamento = escenario.medicamento(actores);
        EnlaceCuit circuito = escenario.circuitoAprobado(actores);
        Lote lote = escenario.loteLiberado(actores, medicamento, "CT-0001", 3);
        escenario.cajasEnFarmacia(actores, circuito, lote, 2);
        String serie = jdbc.queryForObject("SELECT min(serie) FROM unidades_trazables WHERE estado = 'EN_STOCK'",
                String.class);
        String sede = bearer(escenario.sede().getEmail(), "clave-falsa-de-la-sede");
        String farmacia = bearer(actores.adminFarmacia().getEmail(), EscenarioIntegracion.CLAVE);
        String laboratorio = bearer(actores.adminLaboratorio().getEmail(), EscenarioIntegracion.CLAVE);
        String inspector = bearer(actores.inspector().getUsuario().getEmail(), EscenarioIntegracion.CLAVE);

        // Login (con y sin provincia), dispensación (201 y 409 por duplicada) y error de validación (400).
        String loginInspector = JSON.writeValueAsString(Map.of("email", actores.inspector().getUsuario().getEmail(),
                "password", EscenarioIntegracion.CLAVE));
        validar(validador, errores, "iniciarSesion", llamar(HttpMethod.POST, "/api/auth/login", null, loginInspector));
        String dispensa = JSON.writeValueAsString(Map.of("gtin", medicamento.getGtin(), "serie", serie,
                "particular", true, "numeroReceta", "REC-CT-1"));
        validar(validador, errores, "dispensarCaja", llamar(HttpMethod.POST, "/api/dispensaciones", farmacia, dispensa));
        validar(validador, errores, "dispensarCaja", llamar(HttpMethod.POST, "/api/dispensaciones", farmacia, dispensa));
        MockHttpServletResponse invalido = llamar(HttpMethod.POST, "/api/lotes", laboratorio, "{}");
        validar(validador, errores, "registrarLote", invalido);
        assertTrue(json(invalido.getContentAsString()).path("errors").size() > 0, "el 400 trae errors[]");

        // Registro público de una empresa y de un paciente, y el reporte del paciente.
        validar(validador, errores, "registrarEmpresa", registrarFarmaciaPorApi());
        String paciente = JSON.writeValueAsString(Map.of("email", "paciente-ct@integracion.test",
                "password", EscenarioIntegracion.CLAVE, "nombre", "Pedro", "apellido", "Gómez", "dni", "30111098"));
        validar(validador, errores, "registrarPaciente", llamar(HttpMethod.POST, "/api/registro/pacientes", null, paciente));
        String reporte = JSON.writeValueAsString(Map.of("gtin", medicamento.getGtin(), "serie", serie,
                "motivo", "ENVASE_DANADO", "provincia", "MENDOZA", "descripcion", "La caja llegó abierta"));
        validar(validador, errores, "reportarCaja", llamar(HttpMethod.POST, "/api/reportes-ciudadanos",
                bearer("paciente-ct@integracion.test", EscenarioIntegracion.CLAVE), reporte));

        // Cuarentena de LOTE abierta por el inspector (después de dispensar: bloquea el lote).
        String cuarentena = JSON.writeValueAsString(Map.of("loteId", lote.getId().toString(), "motivo", "PREVENTIVA",
                "descripcion", "Control preventivo"));
        validar(validador, errores, "abrirCuarentena", llamar(HttpMethod.POST, "/api/cuarentenas", inspector, cuarentena));

        // Verificación pública: caja dispensada y serie inexistente (sin producto).
        validar(validador, errores, "verificarCaja", llamar(HttpMethod.GET,
                "/api/verificacion?gtin=" + medicamento.getGtin() + "&serie=" + serie, null, null));
        validar(validador, errores, "verificarCaja", llamar(HttpMethod.GET,
                "/api/verificacion?gtin=" + medicamento.getGtin() + "&serie=FALSA0001", null, null));

        // Listados de la Sede y el detalle del primer elemento de cada uno.
        for (Map.Entry<String, String> listado : listadosConDetalle().entrySet()) {
            String rutaListado = ruta(contrato, listado.getKey());
            MockHttpServletResponse pagina = llamar(HttpMethod.GET, rutaListado, sede, null);
            validar(validador, errores, listado.getKey(), pagina);
            JsonNode contenido = json(pagina.getContentAsString()).path("content");
            if (listado.getValue() != null && !contenido.isEmpty()) {
                String rutaDetalle = ruta(contrato, listado.getValue())
                        .replace("{id}", contenido.get(0).path("id").asString());
                validar(validador, errores, listado.getValue(), llamar(HttpMethod.GET, rutaDetalle, sede, null));
            }
        }
        validar(validador, errores, "verificarCadena", llamar(HttpMethod.GET, "/api/eventos-trazabilidad/verificacion", sede, null));
        validar(validador, errores, "obtenerEstadoAnclaje", llamar(HttpMethod.GET, "/api/registros-blockchain/estado", sede, null));

        assertTrue(errores.isEmpty(), "Respuestas que no cumplen el contrato:\n  " + String.join("\n  ", errores));
    }

    // ---------- Utilidades ----------

    /** Revisa el código de éxito y las respuestas de error de una operación. */
    private List<String> revisarRespuestas(String donde, String id, String ruta, String metodo, JsonNode op,
                                           boolean publica) {
        List<String> problemas = new ArrayList<>();
        JsonNode respuestas = op.path("responses");
        String exito = ALTAS.contains(id) ? "201" : ("anclarAhora".equals(id) ? "202" : "200");
        List<String> exitos = new ArrayList<>();
        for (String codigo : respuestas.propertyNames()) {
            if (codigo.startsWith("2")) {
                exitos.add(codigo);
            } else if (!"#/components/schemas/ErrorResponseDTO".equals(
                    respuestas.path(codigo).path("content").path("application/json").path("schema").path("$ref").asString())) {
                problemas.add(donde + ": el " + codigo + " no usa ErrorResponseDTO");
            }
        }
        if (!exitos.equals(List.of(exito))) {
            problemas.add(donde + ": éxito documentado " + exitos + ", se esperaba " + exito);
        }
        boolean conDatos = op.has("requestBody") || op.path("parameters").size() > 0;
        if (conDatos && !respuestas.has("400")) {
            problemas.add(donde + ": recibe datos y no documenta 400");
        }
        if ((!publica || "iniciarSesion".equals(id)) != respuestas.has("401")) {
            problemas.add(donde + ": 401 debe documentarse solo si necesita token (o es el login)");
        }
        if (publica != (op.has("security") && op.path("security").isEmpty())) {
            problemas.add(donde + ": " + (publica ? "pública sin security: []" : "no pública marcada sin seguridad"));
        }
        if (ruta.contains("{id}") && !respuestas.has("404")) {
            problemas.add(donde + ": tiene {id} y no documenta 404");
        }
        if ("post".equals(metodo) && !"iniciarSesion".equals(id) && !respuestas.has("409")) {
            problemas.add(donde + ": escritura sin 409");
        }
        return problemas;
    }

    /** Listado de la Sede → operación de detalle de sus elementos (null si no hay detalle por id). */
    private static Map<String, String> listadosConDetalle() {
        Map<String, String> listados = new LinkedHashMap<>();
        listados.put("listarEmpresas", "obtenerEmpresa");
        listados.put("listarInspectores", "obtenerInspector");
        listados.put("listarUsuarios", "obtenerUsuario");
        listados.put("listarCircuitos", "obtenerCircuito");
        listados.put("listarMedicamentos", "obtenerMedicamento");
        listados.put("listarLotes", "obtenerLote");
        listados.put("listarCajas", "obtenerCaja");
        listados.put("listarBultos", "obtenerBulto");
        listados.put("listarViajes", "obtenerViaje");
        listados.put("listarRecepciones", "obtenerRecepcion");
        listados.put("listarDispensaciones", "obtenerDispensacion");
        listados.put("listarCuarentenas", "obtenerCuarentena");
        listados.put("listarReportes", "obtenerReporte");
        listados.put("listarEventos", "obtenerEvento");
        listados.put("listarLecturasTemperatura", "obtenerLecturaTemperatura");
        listados.put("listarLecturasGps", "obtenerLecturaGps");
        listados.put("listarAnclajes", "obtenerAnclaje");
        return listados;
    }

    /** Ruta (plantilla, con {id}) de una operación, leída del contrato. */
    private static String ruta(JsonNode contrato, String operationId) {
        for (String ruta : contrato.path("paths").propertyNames()) {
            for (JsonNode op : contrato.path("paths").path(ruta)) {
                if (operationId.equals(op.path("operationId").asString())) {
                    return ruta;
                }
            }
        }
        throw new IllegalArgumentException("Sin ruta: " + operationId);
    }

    /** Valida una respuesta contra el contrato y acumula sus errores. */
    private static void validar(ValidadorContrato validador, List<String> errores, String operationId,
                                MockHttpServletResponse respuesta) throws Exception {
        errores.addAll(validador.validar(operationId, respuesta.getStatus(), json(respuesta.getContentAsString())));
    }

    /** Ejecuta un request JSON (con token si se da) y devuelve la respuesta. */
    private MockHttpServletResponse llamar(HttpMethod metodo, String ruta, String token, String cuerpo) throws Exception {
        MockHttpServletRequestBuilder pedido = request(metodo, ruta);
        if (token != null) {
            pedido.header("Authorization", token);
        }
        if (cuerpo != null) {
            pedido.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        }
        return mockMvc.perform(pedido).andReturn().getResponse();
    }

    /** Registro público de una farmacia por la API (multipart con su PDF). */
    private MockHttpServletResponse registrarFarmaciaPorApi() throws Exception {
        byte[] pdf = "%PDF-1.4\n% contrato\n%%EOF\n".getBytes(StandardCharsets.UTF_8);
        return mockMvc.perform(multipart("/api/registro/empresas")
                        .file(new MockMultipartFile("documento", "habilitacion.pdf", "application/pdf", pdf))
                        .param("tipo", "FARMACIA").param("cuit", EscenarioIntegracion.cuitValido(80_000))
                        .param("razonSocial", "Farmacia del Contrato")
                        .param("gln", EscenarioIntegracion.gs1Valido("779922080000")).param("provincia", "MENDOZA")
                        .param("localidad", "Mendoza").param("domicilio", "San Martín 100")
                        .param("adminEmail", "farmacia-contrato@integracion.test")
                        .param("adminPassword", EscenarioIntegracion.CLAVE).param("adminNombre", "Rita")
                        .param("adminApellido", "Contrato").param("adminDni", "29000222"))
                .andReturn().getResponse();
    }

    /** El contrato OpenAPI real (/api-docs). */
    private JsonNode contrato() throws Exception {
        return json(mockMvc.perform(get("/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
