package com.medichain.modules.medicamento;

import com.medichain.exceptions.ResourceNotFoundException;
import com.medichain.modules.auth.UsuarioAutenticado;
import com.medichain.modules.empresa.Empresa;
import com.medichain.utils.seguridad.UsuarioActual;
import com.medichain.utils.seguridad.VerificadorEmpresa;
import com.medichain.utils.validacion.Gs1Util;
import com.medichain.modules.trazabilidad.DatosEventos;
import com.medichain.modules.trazabilidad.RegistradorEventos;
import com.medichain.modules.trazabilidad.TipoEvento;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/**
 * Servicio MedicamentoService en MediChain.
 * El catálogo de medicamentos es visible para todos los roles salvo
 * PACIENTE (lo controla @PreAuthorize), sin filtrado. El alta la hace un
 * laboratorio HABILITADO (R2); el laboratorio sale del token.
 */
@Service
public class MedicamentoService {

    private final MedicamentoRepository repository;
    private final UsuarioActual usuarioActual;
    private final VerificadorEmpresa verificadorEmpresa;
    private final RegistradorEventos registradorEventos;

    @Autowired
    public MedicamentoService(MedicamentoRepository repository, UsuarioActual usuarioActual,
                              VerificadorEmpresa verificadorEmpresa,
                              RegistradorEventos registradorEventos) {
        this.repository = repository;
        this.usuarioActual = usuarioActual;
        this.verificadorEmpresa = verificadorEmpresa;
        this.registradorEventos = registradorEventos;
    }

    /** Devuelve una página del catálogo de medicamentos. */
    @Transactional(readOnly = true)
    public Page<Medicamento> getAll(Pageable pageable) {
        return repository.findAll(pageable);
    }

    /** Busca un medicamento por id o lanza ResourceNotFoundException si no existe. */
    @Transactional(readOnly = true)
    public Medicamento getById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Medicamento no encontrado con id: " + id));
    }

    /**
     * Registra un medicamento del laboratorio del usuario (R2 contra la
     * base). Normaliza el GTIN a solo dígitos.
     */
    @Transactional
    public Medicamento create(Medicamento entity, MedicamentoRequestDTO dto) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        Empresa laboratorio = verificadorEmpresa.exigirHabilitada(actual.getEmpresaId());
        entity.setGtin(Gs1Util.normalizar(entity.getGtin()));
        entity.setLaboratorio(laboratorio);
        Medicamento guardado = repository.save(entity);
        registradorEventos.registrar(TipoEvento.MEDICAMENTO_REGISTRADO, "Medicamento", guardado.getId(),
                DatosEventos.medicamentoRegistrado(guardado), actual);
        return guardado;
    }
}
