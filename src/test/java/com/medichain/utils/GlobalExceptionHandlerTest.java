package com.medichain.utils;

import com.medichain.modules.empresa.Empresa;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.core.TypeInformation;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test unitario GlobalExceptionHandlerTest en MediChain.
 * Invoca los handlers directamente (sin Spring ni MockMvc) y verifica el
 * código HTTP y el ErrorResponseDTO que devuelven.
 */
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("PropertyReferenceException (sort por campo inexistente) → 400 con el nombre del campo")
    void propertyReferenceExceptionDevuelve400ConElCampo() {
        // Arrange: lo que lanza Spring Data cuando Swagger manda sort=["string"].
        PropertyReferenceException ex = new PropertyReferenceException(
                "string", TypeInformation.of(Empresa.class), List.of());

        // Act
        ResponseEntity<ErrorResponseDTO> respuesta = handler.handlePropertyReferenceException(ex);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, respuesta.getStatusCode());
        ErrorResponseDTO cuerpo = respuesta.getBody();
        assertNotNull(cuerpo);
        assertEquals(400, cuerpo.getStatus());
        assertEquals("Campo de ordenamiento inválido: string", cuerpo.getMessage());
        assertNull(cuerpo.getRegla());
        assertNull(cuerpo.getErrors());
    }

    @Test
    @DisplayName("Conflicto de @Version (cualquier OptimisticLockingFailureException) → 409 CONFLICTO_VERSION")
    void conflictoDeVersionTieneCodigo() {
        ResponseEntity<ErrorResponseDTO> respuesta = handler.handleOptimisticLockingFailureException(
                new ObjectOptimisticLockingFailureException(Empresa.class, UUID.randomUUID()));

        assertEquals(HttpStatus.CONFLICT, respuesta.getStatusCode());
        assertEquals("CONFLICTO_VERSION", respuesta.getBody().getRegla());
    }

    @Test
    @DisplayName("Unicidad con traducción propia: GTIN → MEDICAMENTO_DUPLICADO; legajo o DNI → INSPECTOR_DUPLICADO; CUIT → EMPRESA_DUPLICADA")
    void unicidadConCodigoPropio() {
        assertEquals("MEDICAMENTO_DUPLICADO", regla(violacion("uk_medicamentos_gtin", "23505", "07799000001010")));
        assertEquals("INSPECTOR_DUPLICADO", regla(violacion("uk_inspectores_anmat_dni", "23505", "30111222")));
        assertEquals("INSPECTOR_DUPLICADO", regla(violacion("uk_inspectores_anmat_legajo", "23505", "INSP-1")));
        assertEquals("EMPRESA_DUPLICADA", regla(violacion("uk_empresas_cuit", "23505", "30-71000001-4")));
        assertEquals("REGISTRO_NO_COMPLETADO", regla(violacion("uk_usuarios_email", "23505", "a@b.c")));
    }

    @Test
    @DisplayName("Unicidad sin traducción → DATO_DUPLICADO; FK, CHECK o NOT NULL → RESTRICCION_DE_DATOS")
    void codigosGenericos() {
        assertEquals("DATO_DUPLICADO", regla(violacion("uk_tabla_nueva", "23505", "x")));
        assertEquals("RESTRICCION_DE_DATOS", regla(violacion("fk_bultos_lote_id", "23503", "x")));
        assertEquals("RESTRICCION_DE_DATOS", regla(violacion("ck_bultos_estado", "23514", "x")));
        assertEquals("RESTRICCION_DE_DATOS", regla(new DataIntegrityViolationException("sin causa SQL")));
    }

    @Test
    @DisplayName("El log de una violación tiene el nombre de la restricción, nunca el valor duplicado (DNI)")
    void logSinElValor(CapturedOutput salida) {
        handler.handleDataIntegrityViolationException(violacion("uk_inspectores_anmat_dni", "23505", "30111222"));

        assertTrue(salida.getAll().contains("uk_inspectores_anmat_dni"), "el log identifica la restricción");
        assertFalse(salida.getAll().contains("30111222"), "el log no tiene el DNI");
    }

    /** Violación como la arma Hibernate: con el nombre de la restricción y el SQLException de PostgreSQL. */
    private static DataIntegrityViolationException violacion(String restriccion, String estadoSql, String valor) {
        SQLException errorSql = new SQLException("ERROR: duplicate key value violates unique constraint \""
                + restriccion + "\"  Detail: Key (columna)=(" + valor + ") already exists.", estadoSql);
        return new DataIntegrityViolationException("could not execute statement",
                new ConstraintViolationException("could not execute statement", errorSql, restriccion));
    }

    private String regla(DataIntegrityViolationException violacion) {
        ResponseEntity<ErrorResponseDTO> respuesta = handler.handleDataIntegrityViolationException(violacion);
        assertEquals(HttpStatus.CONFLICT, respuesta.getStatusCode());
        return respuesta.getBody().getRegla();
    }

    @Test
    @DisplayName("InvalidDataAccessApiUsageException → 400 con mensaje genérico, sin detalles internos")
    void invalidDataAccessApiUsageExceptionDevuelve400Generico() {
        InvalidDataAccessApiUsageException ex = new InvalidDataAccessApiUsageException(
                "detalle interno de Hibernate que no debe llegar al cliente");

        ResponseEntity<ErrorResponseDTO> respuesta = handler.handleInvalidDataAccessApiUsageException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, respuesta.getStatusCode());
        assertNotNull(respuesta.getBody());
        assertEquals("Parámetro de consulta inválido", respuesta.getBody().getMessage());
    }
}
