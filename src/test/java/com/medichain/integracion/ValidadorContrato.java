package com.medichain.integracion;

import tools.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/**
 * Utilidad de prueba ValidadorContrato en MediChain.
 * Valida una respuesta JSON real contra el esquema que el contrato OpenAPI
 * (/api-docs) documenta para esa operación y ese código HTTP:
 * <ul>
 *   <li>cada propiedad "required" está presente y no es null;</li>
 *   <li>cada valor de un enum está en su lista;</li>
 *   <li>recorre objetos, listas y referencias ($ref, oneOf/anyOf/allOf).</li>
 * </ul>
 * No valida tipos primitivos ni formatos: lo que importa al frontend es que
 * lo marcado como obligatorio venga siempre y que los enums sean los publicados.
 */
public final class ValidadorContrato {

    private final JsonNode contrato;

    /** Crea el validador sobre el contrato completo (/api-docs). */
    public ValidadorContrato(JsonNode contrato) {
        this.contrato = contrato;
    }

    /**
     * Errores de la respuesta de una operación (por operationId) con el código
     * HTTP dado contra su esquema documentado. Vacía si cumple.
     */
    public List<String> validar(String operationId, int codigo, JsonNode respuesta) {
        JsonNode operacion = operacion(operationId);
        JsonNode documentada = operacion.path("responses").path(String.valueOf(codigo));
        if (documentada.isMissingNode()) {
            return List.of(operationId + ": el código " + codigo + " no está documentado");
        }
        JsonNode esquema = documentada.path("content").path("application/json").path("schema");
        if (esquema.isMissingNode()) {
            return List.of(operationId + " " + codigo + ": la respuesta no tiene esquema application/json");
        }
        List<String> errores = new ArrayList<>();
        validar(respuesta, esquema, operationId + " " + codigo, errores);
        return errores;
    }

    /** Busca una operación por su operationId. */
    public JsonNode operacion(String operationId) {
        for (JsonNode ruta : contrato.path("paths")) {
            for (JsonNode operacion : ruta) {
                if (operationId.equals(operacion.path("operationId").asString())) {
                    return operacion;
                }
            }
        }
        throw new IllegalArgumentException("No existe la operación " + operationId + " en el contrato");
    }

    /** Valida un valor contra un esquema, acumulando los errores con la ruta del valor. */
    private void validar(JsonNode valor, JsonNode esquema, String ruta, List<String> errores) {
        esquema = resolver(esquema);
        for (String combinador : List.of("oneOf", "anyOf", "allOf")) {
            if (esquema.has(combinador)) {
                if (valor == null || valor.isNull()) {
                    return;
                }
                for (JsonNode alternativa : esquema.get(combinador)) {
                    if (!"null".equals(alternativa.path("type").asString())) {
                        validar(valor, alternativa, ruta, errores);
                    }
                }
                return;
            }
        }
        if (valor == null || valor.isNull()) {
            return;
        }
        if (esquema.has("enum")) {
            boolean valido = false;
            for (JsonNode permitido : esquema.get("enum")) {
                if (!permitido.isNull() && permitido.asString().equals(valor.asString())) {
                    valido = true;
                }
            }
            if (!valido) {
                errores.add(ruta + ": '" + valor.asString() + "' no está en el enum publicado");
            }
            return;
        }
        if (esquema.has("items") && valor.isArray()) {
            int i = 0;
            for (JsonNode elemento : valor) {
                validar(elemento, esquema.get("items"), ruta + "[" + i++ + "]", errores);
            }
            return;
        }
        if (esquema.has("properties") && valor.isObject()) {
            for (JsonNode requerida : esquema.path("required")) {
                String nombre = requerida.asString();
                if (!valor.has(nombre) || valor.get(nombre).isNull()) {
                    errores.add(ruta + "." + nombre + ": es required y vino " + (valor.has(nombre) ? "null" : "ausente"));
                }
            }
            for (String nombre : esquema.get("properties").propertyNames()) {
                if (valor.has(nombre)) {
                    validar(valor.get(nombre), esquema.get("properties").get(nombre), ruta + "." + nombre, errores);
                }
            }
        }
    }

    /** Sigue las referencias #/components/schemas/X hasta el esquema real. */
    private JsonNode resolver(JsonNode esquema) {
        while (esquema.has("$ref")) {
            String referencia = esquema.get("$ref").asString();
            String nombre = referencia.substring(referencia.lastIndexOf('/') + 1);
            esquema = contrato.path("components").path("schemas").path(nombre);
            if (esquema.isMissingNode()) {
                throw new IllegalStateException("Referencia rota en el contrato: " + referencia);
            }
        }
        return esquema;
    }
}
