package com.medichain.modules.medicamento;

import com.medichain.exceptions.ReglaNegocioException;
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
 * Alcance de lectura por rol: la Sede y los inspectores ven todo el catálogo;
 * un laboratorio ve solo los suyos; una distribuidora o farmacia ve los de los
 * laboratorios con los que tiene un circuito APROBADO (PACIENTE no accede, lo
 * corta @PreAuthorize). Un medicamento fuera del alcance del rol responde 404
 * en getById, como el resto de los recursos de otra empresa. El alta la hace
 * un laboratorio HABILITADO (R2); el laboratorio sale del token.
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

    /** Devuelve una página del catálogo según el alcance del rol del usuario. */
    @Transactional(readOnly = true)
    public Page<Medicamento> getAll(Pageable pageable) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        return switch (actual.getRol()) {
            case SEDE_CENTRAL, INSPECTOR -> repository.findAll(pageable);
            case LABORATORIO -> repository.findByLaboratorioId(actual.getEmpresaId(), pageable);
            case DISTRIBUIDOR, FARMACIA -> repository.findVisiblesParaEmpresa(actual.getEmpresaId(), pageable);
            case PACIENTE -> Page.empty(pageable);
        };
    }

    /**
     * Busca un medicamento por id dentro del alcance del rol. Si no existe, o
     * existe pero está fuera del alcance (de otro laboratorio sin circuito con
     * la empresa), lanza ResourceNotFoundException (404), igual que cualquier
     * recurso de otra empresa.
     */
    @Transactional(readOnly = true)
    public Medicamento getById(UUID id) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        Medicamento medicamento = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Medicamento no encontrado con id: " + id));
        if (esVisiblePara(medicamento, actual)) {
            return medicamento;
        }
        throw new ResourceNotFoundException("Medicamento no encontrado con id: " + id);
    }

    /**
     * Visibilidad de lectura de un medicamento según el rol: la Sede y los
     * inspectores ven todo; el laboratorio, los suyos; la distribuidora o
     * farmacia, los de los laboratorios con circuito APROBADO con ella.
     */
    private boolean esVisiblePara(Medicamento medicamento, UsuarioAutenticado actual) {
        return switch (actual.getRol()) {
            case SEDE_CENTRAL, INSPECTOR -> true;
            case LABORATORIO -> medicamento.getLaboratorio().getId().equals(actual.getEmpresaId());
            case DISTRIBUIDOR, FARMACIA -> repository.existsVisibleParaEmpresa(medicamento.getId(), actual.getEmpresaId());
            case PACIENTE -> false;
        };
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
        if (repository.existsByGtin(entity.getGtin())) {
            // El índice uk_medicamentos_gtin frena igual una carrera (GlobalExceptionHandler → mismo código).
            throw new ReglaNegocioException("MEDICAMENTO_DUPLICADO",
                    "Ya existe un medicamento registrado con el GTIN " + entity.getGtin());
        }
        entity.setLaboratorio(laboratorio);
        Medicamento guardado = repository.save(entity);
        registradorEventos.registrar(TipoEvento.MEDICAMENTO_REGISTRADO, "Medicamento", guardado.getId(),
                DatosEventos.medicamentoRegistrado(guardado), actual);
        return guardado;
    }
}
