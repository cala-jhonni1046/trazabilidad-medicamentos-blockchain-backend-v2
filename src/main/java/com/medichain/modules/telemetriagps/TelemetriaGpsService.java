package com.medichain.modules.telemetriagps;

import com.medichain.exceptions.ReglaNegocioException;
import com.medichain.exceptions.ResourceNotFoundException;
import com.medichain.modules.despachologistico.EstadoDespacho;
import com.medichain.modules.auth.UsuarioAutenticado;
import com.medichain.modules.despachologistico.DespachoLogistico;
import com.medichain.modules.despachologistico.DespachoLogisticoRepository;
import com.medichain.modules.usuario.RolUsuario;
import com.medichain.utils.seguridad.UsuarioActual;
import com.medichain.utils.seguridad.VerificadorEmpresa;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/**
 * Servicio TelemetriaGpsService en MediChain.
 * Lecturas GPS (D3): SEDE e INSPECTOR ven todas; LABORATORIO y
 * DISTRIBUIDOR ven las de los despachos que originan, y solo la empresa
 * origen del despacho registra lecturas (R2).
 */
@Service
public class TelemetriaGpsService {

    private final TelemetriaGpsRepository repository;
    private final DespachoLogisticoRepository despachoLogisticoRepository;
    private final UsuarioActual usuarioActual;
    private final VerificadorEmpresa verificadorEmpresa;

    @Autowired
    public TelemetriaGpsService(TelemetriaGpsRepository repository,
                                DespachoLogisticoRepository despachoLogisticoRepository,
                                UsuarioActual usuarioActual, VerificadorEmpresa verificadorEmpresa) {
        this.repository = repository;
        this.despachoLogisticoRepository = despachoLogisticoRepository;
        this.usuarioActual = usuarioActual;
        this.verificadorEmpresa = verificadorEmpresa;
    }

    /** Devuelve una página de lecturas según el rol del usuario. */
    @Transactional(readOnly = true)
    public Page<TelemetriaGps> getAll(Pageable pageable) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        return switch (actual.getRol()) {
            case SEDE_CENTRAL, INSPECTOR -> repository.findAll(pageable);
            case LABORATORIO, DISTRIBUIDOR -> repository.findByDespachoOrigenId(actual.getEmpresaId(), pageable);
            case FARMACIA, PACIENTE -> Page.empty(pageable);
        };
    }

    /** Busca una lectura por id; si su despacho no es de la empresa del usuario, 404. */
    @Transactional(readOnly = true)
    public TelemetriaGps getById(UUID id) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        TelemetriaGps lectura = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TelemetriaGps no encontrada con id: " + id));
        boolean veTodo = actual.getRol() == RolUsuario.SEDE_CENTRAL || actual.getRol() == RolUsuario.INSPECTOR;
        if (!veTodo && !lectura.getDespacho().getOrigen().getId().equals(actual.getEmpresaId())) {
            throw new ResourceNotFoundException("TelemetriaGps no encontrada con id: " + id);
        }
        return lectura;
    }

    /** Registra una posición GPS (sin evento): solo la empresa origen (otra → 404) y con el viaje EN_TRANSITO. */
    @Transactional
    public TelemetriaGps create(TelemetriaGpsRequestDTO dto) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        verificadorEmpresa.exigirHabilitada(actual.getEmpresaId());
        UUID despachoId = dto.getDespachoId();
        DespachoLogistico despacho = despachoLogisticoRepository.findById(despachoId)
                .filter(d -> d.getOrigen().getId().equals(actual.getEmpresaId()))
                .orElseThrow(() -> new ResourceNotFoundException("Viaje no encontrado con id: " + despachoId));
        if (despacho.getEstado() != EstadoDespacho.EN_TRANSITO) {
            throw new ReglaNegocioException("TRANSICION_INVALIDA",
                    "Solo se registra GPS de un viaje EN_TRANSITO (estado " + despacho.getEstado() + ")");
        }
        return repository.save(new TelemetriaGps(dto.getSensorId(), dto.getLatitud(), dto.getLongitud(),
                dto.getLugar(), dto.getFechaHora().toInstant(), despacho));
    }
}
