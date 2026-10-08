package com.medichain.config;

import com.medichain.utils.ErrorResponseDTO;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuración OpenApiConfig en MediChain.
 * Arma el contrato OpenAPI (/api-docs) que usa el frontend para generar su
 * cliente y que Swagger UI muestra a las personas:
 * <ul>
 *   <li>esquema de seguridad "bearerAuth" (JWT) aplicado a toda la API;</li>
 *   <li>tags en el orden del flujo del negocio, cada uno con su descripción
 *       (springdoc los reordena: tagsEnOrdenDelFlujo los vuelve a poner en orden);</li>
 *   <li>servidor relativo "/" (mismo origen): el contrato no depende del host;</li>
 *   <li>los esquemas ErrorResponseDTO y ErrorCampoDTO, y las respuestas de error
 *       de cada operación a partir de su anotación @RespuestasError;</li>
 *   <li>las páginas {content, page} con sus campos obligatorios.</li>
 * </ul>
 * Swagger UI y /api-docs solo se publican con SWAGGER_HABILITADO=true
 * (application.properties); en producción, sin esa variable, responden 404.
 */
@Configuration
public class OpenApiConfig {

    private static final String ESQUEMA = "bearerAuth";
    private static final String REF_ERROR = "#/components/schemas/ErrorResponseDTO";

    /** Tags en el orden del flujo: registro, circuitos, lotes, bultos, viajes, recepción, dispensación, cuarentenas, verificación, blockchain. */
    private static final Map<String, String> TAGS = tags();

    /** Descripción fija de cada código de error del contrato. */
    private static final Map<Integer, String> ERRORES = errores();

    /** Definición OpenAPI: información, servidor, seguridad, tags ordenados y esquemas de error. */
    @Bean
    public OpenAPI medichainOpenApi() {
        Components componentes = new Components().addSecuritySchemes(ESQUEMA, new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Pegá SOLO el token del login, sin la palabra Bearer y sin comillas: "
                        + "Swagger agrega \"Bearer \" por su cuenta."));
        // ErrorResponseDTO no es el tipo de retorno de ningún método: se registra a mano (con ErrorCampoDTO).
        Map<String, Schema> esquemasDeError = ModelConverters.getInstance(true)
                .readAll(new AnnotatedType(ErrorResponseDTO.class));
        esquemasDeError.forEach(componentes::addSchemas);

        OpenAPI openApi = new OpenAPI()
                .info(new Info().title("MediChain API").version("v1")
                        .description("Trazabilidad de medicamentos (ANMAT) con anclaje en blockchain. Todas las "
                                + "fechas y horas van en UTC con Z. Los listados son páginas {content, page}. "
                                + "Todo error responde ErrorResponseDTO; el 409 trae su código en 'regla'."))
                .servers(List.of(new Server().url("/").description("Mismo origen que el frontend")))
                .components(componentes)
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA));
        TAGS.forEach((nombre, descripcion) -> openApi.addTagsItem(new Tag().name(nombre).description(descripcion)));
        return openApi;
    }

    /**
     * Agrega a cada operación las respuestas de error que declara su
     * @RespuestasError, todas con el esquema ErrorResponseDTO.
     */
    @Bean
    public OperationCustomizer respuestasDeError() {
        return (Operation operacion, HandlerMethod metodo) -> {
            RespuestasError declaradas = metodo.getMethodAnnotation(RespuestasError.class);
            if (declaradas == null) {
                return operacion;
            }
            ApiResponses respuestas = operacion.getResponses() != null ? operacion.getResponses() : new ApiResponses();
            for (int codigo : declaradas.value()) {
                String descripcion = ERRORES.get(codigo);
                if (descripcion == null) {
                    throw new IllegalStateException("Código de error sin descripción en OpenApiConfig: " + codigo
                            + " (" + metodo.getMethod().getName() + ")");
                }
                respuestas.addApiResponse(String.valueOf(codigo), new ApiResponse().description(descripcion)
                        .content(new Content().addMediaType("application/json",
                                new MediaType().schema(new Schema<>().$ref(REF_ERROR)))));
            }
            operacion.setResponses(respuestas);
            return operacion;
        };
    }

    /**
     * springdoc arma la lista de tags a partir de los controllers y pierde el
     * orden: acá se deja en el orden del flujo (TAGS), con sus descripciones.
     * Un tag de un controller que no esté en TAGS falla (ContratoOpenApiIT).
     */
    @Bean
    public OpenApiCustomizer tagsEnOrdenDelFlujo() {
        return openApi -> {
            List<Tag> ordenados = new ArrayList<>();
            TAGS.forEach((nombre, descripcion) -> ordenados.add(new Tag().name(nombre).description(descripcion)));
            if (openApi.getTags() != null) {
                for (Tag tag : openApi.getTags()) {
                    if (!TAGS.containsKey(tag.getName())) {
                        ordenados.add(tag);
                    }
                }
            }
            openApi.setTags(ordenados);
        };
    }

    /**
     * Las páginas ({content, page}) las arma springdoc sin campos obligatorios,
     * aunque siempre vienen completas: se marcan acá para que el cliente
     * generado no los trate como opcionales.
     */
    @Bean
    public OpenApiCustomizer paginasConCamposObligatorios() {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
                return;
            }
            openApi.getComponents().getSchemas().forEach((nombre, esquema) -> {
                if (nombre.startsWith("PagedModel")) {
                    esquema.setRequired(List.of("content", "page"));
                } else if ("PageMetadata".equals(nombre)) {
                    esquema.setRequired(List.of("size", "number", "totalElements", "totalPages"));
                }
            });
        };
    }

    /** Tags del contrato, en el orden del flujo del negocio. */
    private static Map<String, String> tags() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("Registro público", "Alta de empresas (con su PDF de habilitación) y de pacientes. Público.");
        tags.put("Autenticación", "Login: devuelve el JWT y los datos del usuario para el frontend.");
        tags.put("Empresas", "Empresas y su habilitación por un inspector de su provincia (R2).");
        tags.put("Inspectores ANMAT", "Alta, baja y reactivación de inspectores: solo la Sede (R1).");
        tags.put("Usuarios", "Empleados de cada empresa y cuentas de la Sede.");
        tags.put("Circuitos", "Circuitos laboratorio → distribuidora → farmacia (R5).");
        tags.put("Medicamentos", "Medicamentos de cada laboratorio, identificados por GTIN.");
        tags.put("Lotes", "Lotes con sus series (R3) y su liberación (R4).");
        tags.put("Unidades trazables", "Cajas (GTIN + serie) y devoluciones.");
        tags.put("Bultos", "Bultos cerrados de un solo lote con su circuito de destino (R6).");
        tags.put("Viajes", "Viajes del tramo 1 y del tramo 2: salida, cancelación y robo (R7, R14).");
        tags.put("Telemetría de temperatura", "Lecturas de temperatura de los viajes y ruptura de la cadena de frío (R9).");
        tags.put("Telemetría GPS", "Lecturas de posición de los viajes.");
        tags.put("Recepciones", "Recepción de bultos escaneando su código (R8).");
        tags.put("Dispensaciones", "Dispensación de cajas y su anulación (R11, R13).");
        tags.put("Cuarentenas", "Cuarentenas y recalls, y el dictamen del inspector (R10, R12).");
        tags.put("Reportes ciudadanos", "Reportes de pacientes sobre una caja y su investigación (R12, R13).");
        tags.put("Verificación pública", "Verificación de una caja por GTIN + serie. Público.");
        tags.put("Eventos de trazabilidad", "Cadena de eventos encadenados por hash y su verificación (R15).");
        tags.put("Registros blockchain", "Anclaje del último hash de la cadena en Ethereum Sepolia (R15).");
        return tags;
    }

    /** Descripción de cada código de error. */
    private static Map<Integer, String> errores() {
        Map<Integer, String> errores = new LinkedHashMap<>();
        errores.put(400, "Datos inválidos: campo obligatorio, formato o parámetro mal formado. "
                + "En la validación, 'errors' trae un elemento por campo.");
        errores.put(401, "No autenticado: falta el token, no es válido, venció o la cuenta está desactivada "
                + "(en el login: credenciales incorrectas).");
        errores.put(403, "Tu rol (o tu marca de administrador o director técnico) no permite esta acción.");
        errores.put(404, "No existe o no es visible para tu empresa o provincia (lo ajeno responde 404, no 403).");
        errores.put(409, "Regla de negocio o conflicto: el código va en 'regla' (R1–R15 o un código de la tabla).");
        errores.put(503, "La red blockchain (Sepolia) no respondió. Reintentá más tarde.");
        return errores;
    }
}
