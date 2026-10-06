package com.medichain.utils;

import com.medichain.modules.empresa.Empresa;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.core.TypeInformation;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Test unitario GlobalExceptionHandlerTest en MediChain.
 * Invoca los handlers directamente (sin Spring ni MockMvc) y verifica el
 * código HTTP y el ErrorResponseDTO que devuelven.
 */
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
