package com.medichain.utils.seguridad;

import com.medichain.exceptions.ReglaNegocioException;
import com.medichain.modules.empresa.Empresa;
import com.medichain.modules.empresa.EmpresaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/**
 * Verificador VerificadorEmpresa en MediChain.
 * Aplica la regla R2 ("solo operan empresas HABILITADA") contra la BASE
 * DE DATOS, no contra el token: una empresa suspendida hace 5 minutos
 * todavía tiene tokens válidos por 8 horas. Cada Service lo llama al
 * comienzo de toda escritura hecha por un usuario de empresa.
 */
@Component
public class VerificadorEmpresa {

    private final EmpresaRepository empresaRepository;

    @Autowired
    public VerificadorEmpresa(EmpresaRepository empresaRepository) {
        this.empresaRepository = empresaRepository;
    }

    /**
     * Devuelve la empresa si existe y está HABILITADA. Una empresa null
     * (usuario sin empresa, D6), inexistente o en otro estado lanza
     * ReglaNegocioException("R2") → 409.
     */
    @Transactional(readOnly = true)
    public Empresa exigirHabilitada(UUID empresaId) {
        if (empresaId == null) {
            throw new ReglaNegocioException("R2", "El usuario no pertenece a ninguna empresa habilitada");
        }
        Empresa empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new ReglaNegocioException("R2", "La empresa no está habilitada para operar"));
        if (!empresa.estaHabilitada()) {
            throw new ReglaNegocioException("R2", "La empresa no está habilitada para operar");
        }
        return empresa;
    }
}
