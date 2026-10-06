package com.medichain.modules.unidadtrazable;

import com.medichain.exceptions.ResourceNotFoundException;
import com.medichain.modules.auth.UsuarioAutenticado;
import com.medichain.modules.empresa.Empresa;
import com.medichain.modules.trazabilidad.DatosEventos;
import com.medichain.modules.trazabilidad.RegistradorEventos;
import com.medichain.modules.trazabilidad.TipoEvento;
import com.medichain.utils.seguridad.UsuarioActual;
import com.medichain.utils.seguridad.VerificadorEmpresa;
import com.medichain.utils.validacion.Gs1Util;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/**
 * Servicio UnidadTrazableService en MediChain.
 * Unidades con filtrado por rol: SEDE e INSPECTOR ven todas; el
 * LABORATORIO ve las de sus lotes; DISTRIBUIDOR y FARMACIA ven las que
 * tienen en su poder y las de bultos que ya salieron hacia ellas. Solo lectura: las cajas
 * nacen con su lote (LoteService.registrar); no hay alta suelta.
 */
@Service
public class UnidadTrazableService {

    private final UnidadTrazableRepository repository;
    private final UsuarioActual usuarioActual;
    private final VerificadorEmpresa verificadorEmpresa;
    private final RegistradorEventos registradorEventos;

    @Autowired
    public UnidadTrazableService(UnidadTrazableRepository repository, UsuarioActual usuarioActual,
                                 VerificadorEmpresa verificadorEmpresa, RegistradorEventos registradorEventos) {
        this.repository = repository;
        this.usuarioActual = usuarioActual;
        this.verificadorEmpresa = verificadorEmpresa;
        this.registradorEventos = registradorEventos;
    }

    /** Devuelve una página de unidades según el rol del usuario. */
    @Transactional(readOnly = true)
    public Page<UnidadTrazable> getAll(Pageable pageable) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        return switch (actual.getRol()) {
            case SEDE_CENTRAL, INSPECTOR -> repository.findAll(pageable);
            case LABORATORIO -> repository.findByLoteMedicamentoLaboratorioId(actual.getEmpresaId(), pageable);
            case DISTRIBUIDOR -> repository.findVisiblesParaDistribuidor(actual.getEmpresaId(), pageable);
            case FARMACIA -> repository.findVisiblesParaFarmacia(actual.getEmpresaId(), pageable);
            case PACIENTE -> Page.empty(pageable);
        };
    }

    /** Busca una unidad por id; si no le corresponde al usuario, 404. */
    @Transactional(readOnly = true)
    public UnidadTrazable getById(UUID id) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        UnidadTrazable unidad = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("UnidadTrazable no encontrada con id: " + id));
        if (!puedeVer(actual, unidad)) {
            throw new ResourceNotFoundException("UnidadTrazable no encontrada con id: " + id);
        }
        return unidad;
    }

    /**
     * Devolución (R14): la farmacia que tiene la caja EN_STOCK la devuelve
     * (EN_STOCK → DEVUELTA); no vuelve a dispensarse. Caja que no está en su
     * farmacia → 404. Evento DEVOLUCION (motivo y hash de la observación).
     */
    @Transactional
    public UnidadTrazable devolver(DevolucionRequestDTO dto) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        Empresa farmacia = verificadorEmpresa.exigirHabilitada(actual.getEmpresaId());
        String gtin = Gs1Util.normalizar(dto.getGtin());
        UnidadTrazable caja = repository.findByGtinAndSerie(gtin, dto.getSerie())
                .filter(c -> c.estaEn(farmacia))
                .orElseThrow(() -> new ResourceNotFoundException("La caja no está en el stock de tu farmacia"));
        caja.devolver();
        UnidadTrazable guardada = repository.save(caja);
        registradorEventos.registrar(TipoEvento.DEVOLUCION, "UnidadTrazable", guardada.getId(),
                DatosEventos.devolucion(guardada, farmacia.getId(), dto.getMotivo(), dto.getObservacion()), actual);
        return guardada;
    }

    /** SEDE e INSPECTOR ven todo; LAB sus lotes; DIST y FARM lo que tienen o ya salió hacia ellas. */
    private boolean puedeVer(UsuarioAutenticado actual, UnidadTrazable unidad) {
        return switch (actual.getRol()) {
            case SEDE_CENTRAL, INSPECTOR -> true;
            case LABORATORIO -> unidad.getLote().getLaboratorio().getId()
                    .equals(actual.getEmpresaId());
            case DISTRIBUIDOR -> repository.esVisibleParaDistribuidor(unidad.getId(), actual.getEmpresaId());
            case FARMACIA -> repository.esVisibleParaFarmacia(unidad.getId(), actual.getEmpresaId());
            case PACIENTE -> false;
        };
    }
}
