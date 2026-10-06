package com.medichain.modules.dispensacion;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validador DispensacionRequestValidaValidator en MediChain.
 * Implementa @DispensacionRequestValida: particular u obra social + afiliado.
 */
public class DispensacionRequestValidaValidator
        implements ConstraintValidator<DispensacionRequestValida, DispensacionRequestDTO> {

    /** Valida la regla de cobertura. */
    @Override
    public boolean isValid(DispensacionRequestDTO dto, ConstraintValidatorContext context) {
        if (dto == null || dto.getParticular() == null) {
            return true;
        }
        boolean conObraSocial = tieneTexto(dto.getObraSocial());
        boolean conAfiliado = tieneTexto(dto.getNumeroAfiliado());
        context.disableDefaultConstraintViolation();
        if (dto.getParticular() && (conObraSocial || conAfiliado)) {
            agregar(context, "obraSocial", "Si es particular, no se informan obra social ni número de afiliado");
            return false;
        }
        if (!dto.getParticular() && (!conObraSocial || !conAfiliado)) {
            agregar(context, conObraSocial ? "numeroAfiliado" : "obraSocial",
                    "Con obra social hay que informar la obra social y el número de afiliado (o marcar particular)");
            return false;
        }
        return true;
    }

    /** Indica si el texto tiene contenido. */
    private boolean tieneTexto(String texto) {
        return texto != null && !texto.isBlank();
    }

    /** Agrega un error asociado al campo dado. */
    private void agregar(ConstraintValidatorContext context, String campo, String mensaje) {
        context.buildConstraintViolationWithTemplate(mensaje).addPropertyNode(campo).addConstraintViolation();
    }
}
