package com.medichain.modules.empresa;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validador DirectorTecnicoSoloLaboratorioValidator en MediChain.
 * Implementa @DirectorTecnicoSoloLaboratorio y asocia el error al campo
 * adminEsDirectorTecnico.
 */
public class DirectorTecnicoSoloLaboratorioValidator
        implements ConstraintValidator<DirectorTecnicoSoloLaboratorio, RegistroEmpresaRequestDTO> {

    /** Válido si no se marca como DT, o si la empresa es un LABORATORIO. */
    @Override
    public boolean isValid(RegistroEmpresaRequestDTO dto, ConstraintValidatorContext context) {
        if (dto == null || !Boolean.TRUE.equals(dto.getAdminEsDirectorTecnico())
                || dto.getTipo() == TipoEmpresa.LABORATORIO) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("adminEsDirectorTecnico")
                .addConstraintViolation();
        return false;
    }
}
