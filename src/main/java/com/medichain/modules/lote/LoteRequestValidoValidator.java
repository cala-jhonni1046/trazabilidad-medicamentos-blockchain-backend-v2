package com.medichain.modules.lote;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validador LoteRequestValidoValidator en MediChain.
 * Implementa @LoteRequestValido: exactamente una de "series" o "cantidad",
 * y fechaVencimiento posterior a fechaFabricacion.
 */
public class LoteRequestValidoValidator implements ConstraintValidator<LoteRequestValido, LoteRequestDTO> {

    /** Valida las reglas cruzadas entre campos del lote. */
    @Override
    public boolean isValid(LoteRequestDTO dto, ConstraintValidatorContext context) {
        if (dto == null) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        boolean valido = true;
        boolean conLista = dto.getSeries() != null;
        boolean conCantidad = dto.getCantidad() != null;
        if (conLista == conCantidad) {
            agregar(context, "series", "Enviá exactamente una de las dos: la lista 'series' o la 'cantidad' a generar");
            valido = false;
        }
        if (dto.getFechaFabricacion() != null && dto.getFechaVencimiento() != null
                && !dto.getFechaVencimiento().isAfter(dto.getFechaFabricacion())) {
            agregar(context, "fechaVencimiento", "La fecha de vencimiento debe ser posterior a la de fabricación");
            valido = false;
        }
        return valido;
    }

    /** Agrega un error asociado al campo dado. */
    private void agregar(ConstraintValidatorContext context, String campo, String mensaje) {
        context.buildConstraintViolationWithTemplate(mensaje).addPropertyNode(campo).addConstraintViolation();
    }
}
