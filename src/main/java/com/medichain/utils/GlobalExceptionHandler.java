package com.medichain.utils;

import com.medichain.exceptions.CredencialesInvalidasException;
import com.medichain.exceptions.DocumentoInvalidoException;
import com.medichain.exceptions.ReglaNegocioException;
import com.medichain.exceptions.ResourceNotFoundException;
import com.medichain.modules.registroblockchain.ErrorBlockchainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manejador global de excepciones GlobalExceptionHandler en MediChain.
 * Traduce cada excepción a un código HTTP y a un ErrorResponseDTO en
 * JSON. Nunca expone detalles internos (SQL, tablas, stack traces): el
 * detalle técnico va solo al log.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Errores de Bean Validation → 400 con la lista de campos. BindException
     * cubre el cuerpo JSON (MethodArgumentNotValidException es su subclase)
     * y los formularios multipart (@ModelAttribute del registro público).
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidationExceptions(BindException ex) {
        List<ErrorResponseDTO.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorResponseDTO.FieldError(error.getField(), error.getDefaultMessage()))
                .collect(Collectors.toList());

        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
                HttpStatus.BAD_REQUEST.value(),
                "Error de validación",
                errors);
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /** Parámetros de la URL con formato inválido (@RequestParam validado) → 400 con el mensaje de cada uno. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponseDTO> handleHandlerMethodValidationException(HandlerMethodValidationException ex) {
        List<ErrorResponseDTO.FieldError> errors = ex.getParameterValidationResults().stream()
                .flatMap(resultado -> resultado.getResolvableErrors().stream()
                        .map(error -> new ErrorResponseDTO.FieldError(
                                resultado.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .collect(Collectors.toList());
        ErrorResponseDTO errorResponse = new ErrorResponseDTO(HttpStatus.BAD_REQUEST.value(), "Error de validación", errors);
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /** Restricciones de Bean Validation fuera del cuerpo (por ejemplo parámetros) → 400. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponseDTO> handleConstraintViolationException(ConstraintViolationException ex) {
        List<ErrorResponseDTO.FieldError> errors = ex.getConstraintViolations().stream()
                .map(v -> new ErrorResponseDTO.FieldError(v.getPropertyPath().toString(), v.getMessage()))
                .collect(Collectors.toList());
        ErrorResponseDTO errorResponse = new ErrorResponseDTO(HttpStatus.BAD_REQUEST.value(), "Error de validación", errors);
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /** Falta un parámetro obligatorio de la URL → 400. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponseDTO> handleMissingServletRequestParameterException(MissingServletRequestParameterException ex) {
        ErrorResponseDTO errorResponse = new ErrorResponseDTO(HttpStatus.BAD_REQUEST.value(),
                "Falta el parámetro obligatorio: " + ex.getParameterName());
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /** Documento que no es un PDF válido → 400. */
    @ExceptionHandler(DocumentoInvalidoException.class)
    public ResponseEntity<ErrorResponseDTO> handleDocumentoInvalidoException(DocumentoInvalidoException ex) {
        ErrorResponseDTO errorResponse = new ErrorResponseDTO(HttpStatus.BAD_REQUEST.value(), ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /** Archivo que supera el límite de multipart (5 MB) → 400. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponseDTO> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex) {
        ErrorResponseDTO errorResponse = new ErrorResponseDTO(HttpStatus.BAD_REQUEST.value(),
                "El documento no puede superar 5 MB");
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /** Falta una parte obligatoria del formulario multipart → 400. */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponseDTO> handleMissingServletRequestPartException(MissingServletRequestPartException ex) {
        ErrorResponseDTO errorResponse = new ErrorResponseDTO(HttpStatus.BAD_REQUEST.value(),
                "Falta la parte obligatoria: " + ex.getRequestPartName());
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /** Método HTTP no soportado por la ruta (por ejemplo POST donde solo hay GET) → 405. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponseDTO> handleMethodNotSupportedException(HttpRequestMethodNotSupportedException ex) {
        ErrorResponseDTO errorResponse = new ErrorResponseDTO(HttpStatus.METHOD_NOT_ALLOWED.value(),
                "Método " + ex.getMethod() + " no permitido en esta ruta");
        return new ResponseEntity<>(errorResponse, HttpStatus.METHOD_NOT_ALLOWED);
    }

    /** Ruta inexistente → 404 (antes caía en el 500 genérico). */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleNoResourceFoundException(NoResourceFoundException ex) {
        ErrorResponseDTO errorResponse = new ErrorResponseDTO(HttpStatus.NOT_FOUND.value(), "Ruta inexistente");
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    /** Recurso inexistente → 404. */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleResourceNotFoundException(ResourceNotFoundException ex) {
        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    /** Login fallido → 401 con el mensaje único "Credenciales inválidas". */
    @ExceptionHandler(CredencialesInvalidasException.class)
    public ResponseEntity<ErrorResponseDTO> handleCredencialesInvalidasException(CredencialesInvalidasException ex) {
        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
                HttpStatus.UNAUTHORIZED.value(),
                ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
    }

    /** Regla de negocio incumplida → 409 con el código de regla. */
    @ExceptionHandler(ReglaNegocioException.class)
    public ResponseEntity<ErrorResponseDTO> handleReglaNegocioException(ReglaNegocioException ex) {
        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
                HttpStatus.CONFLICT.value(),
                ex.getMessage(),
                ex.getCodigoRegla());
        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }

    /** Violación de unique/FK en la base → 409 sin nombres de tablas ni constraints. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponseDTO> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        logger.warn("Violación de integridad de datos: {}", ex.getMostSpecificCause().getMessage());
        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
                HttpStatus.CONFLICT.value(),
                "El dato ya existe o viola una restricción");
        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }

    /** Conflicto de @Version (edición concurrente) → 409. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponseDTO> handleOptimisticLockingFailureException(ObjectOptimisticLockingFailureException ex) {
        logger.warn("Conflicto de concurrencia optimista: {}", ex.getMessage());
        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
                HttpStatus.CONFLICT.value(),
                "El registro fue modificado por otro usuario; volvé a cargarlo");
        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }

    /** JSON mal formado o ilegible → 400. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDTO> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        logger.warn("Cuerpo de solicitud inválido: {}", ex.getMessage());
        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
                HttpStatus.BAD_REQUEST.value(),
                "El cuerpo de la solicitud no es válido");
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /** Parámetro con tipo inválido (p. ej. UUID mal formado) → 400. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponseDTO> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        String paramName = ex.getName() != null ? ex.getName() : "parámetro";
        logger.warn("Tipo de parámetro inválido '{}': {}", paramName, ex.getValue());
        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
                HttpStatus.BAD_REQUEST.value(),
                "Parámetro inválido: " + paramName);
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /** Ordenamiento (sort) por una propiedad inexistente → 400 con el nombre del campo. */
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ErrorResponseDTO> handlePropertyReferenceException(PropertyReferenceException ex) {
        String propertyName = ex.getPropertyName() != null ? ex.getPropertyName() : "desconocido";
        logger.warn("Campo de ordenamiento inválido: {}", propertyName);
        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
                HttpStatus.BAD_REQUEST.value(),
                "Campo de ordenamiento inválido: " + propertyName);
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /** Uso inválido de la capa de datos por un parámetro de consulta → 400 genérico. */
    @ExceptionHandler(InvalidDataAccessApiUsageException.class)
    public ResponseEntity<ErrorResponseDTO> handleInvalidDataAccessApiUsageException(InvalidDataAccessApiUsageException ex) {
        logger.warn("Parámetro de consulta inválido", ex);
        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
                HttpStatus.BAD_REQUEST.value(),
                "Parámetro de consulta inválido");
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Rol sin permiso (@PreAuthorize) o marca de usuario faltante → 403.
     * Sin este handler, el de Exception lo convertiría en 500.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDTO> handleAccessDeniedException(AccessDeniedException ex) {
        logger.warn("Acceso denegado: {}", ex.getMessage());
        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
                HttpStatus.FORBIDDEN.value(),
                "No tenés permiso para realizar esta acción");
        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }

    /** Request sin usuario autenticado que llegó hasta un Service → 401. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponseDTO> handleAuthenticationException(AuthenticationException ex) {
        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
                HttpStatus.UNAUTHORIZED.value(),
                "No autenticado: falta el token o no es válido");
        return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
    }

    /**
     * La red blockchain no respondió (o el nodo rechazó la operación) → 503
     * con mensaje fijo. El detalle, ya saneado (sin URL del RPC ni clave),
     * va solo al log. El negocio no depende de esto: solo lo usan los
     * endpoints de anclaje.
     */
    @ExceptionHandler(ErrorBlockchainException.class)
    public ResponseEntity<ErrorResponseDTO> handleErrorBlockchainException(ErrorBlockchainException ex) {
        logger.warn("Error de blockchain: {}", ex.getMessage());
        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "No se pudo comunicar con la red blockchain. Reintentá en unos minutos.");
        return new ResponseEntity<>(errorResponse, HttpStatus.SERVICE_UNAVAILABLE);
    }

    /** Cualquier otra excepción → 500 con mensaje fijo; el detalle va solo al log. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleGeneralException(Exception ex) {
        logger.error("Error interno del servidor", ex);
        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Error interno del servidor");
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
