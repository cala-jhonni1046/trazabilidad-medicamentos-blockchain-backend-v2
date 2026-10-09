package com.medichain.modules.dispensacion;

import com.medichain.exceptions.ReglaNegocioException;
import com.medichain.exceptions.ResourceNotFoundException;
import com.medichain.modules.auth.UsuarioAutenticado;
import com.medichain.modules.cuarentena.Bloqueo;
import com.medichain.modules.cuarentena.EvaluadorBloqueo;
import com.medichain.modules.empresa.Empresa;
import com.medichain.modules.trazabilidad.DatosEventos;
import com.medichain.modules.trazabilidad.RegistradorEventos;
import com.medichain.modules.trazabilidad.RegistradorEventosAparte;
import com.medichain.modules.trazabilidad.TipoEvento;
import com.medichain.modules.unidadtrazable.EstadoUnidad;
import com.medichain.modules.unidadtrazable.UnidadTrazable;
import com.medichain.modules.unidadtrazable.UnidadTrazableRepository;
import com.medichain.modules.usuario.RolUsuario;
import com.medichain.utils.Tiempo;
import com.medichain.utils.seguridad.UsuarioActual;
import com.medichain.utils.seguridad.VerificadorEmpresa;
import com.medichain.utils.seguridad.VerificadorUsuario;
import com.medichain.utils.validacion.DniUtil;
import com.medichain.utils.validacion.Gs1Util;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import java.util.UUID;

/**
 * Servicio DispensacionService en MediChain (R10, R11, R13, R14).
 * Dispensación escaneando GTIN + serie. Orden de los controles:
 * caja inexistente → 404; ROBADA → 409 R14 + SERIE_ROBADA; ya DISPENSADA
 * → 409 R11 + INTENTO_DUPLICADO (posible falsificación); no está en MI
 * farmacia → 404; en mi farmacia pero no EN_STOCK → TRANSICION_INVALIDA;
 * bloqueada → 409 R10. Los intentos van en transacción aparte.
 * R13: el DNI completo solo existe en el request; acá se enmascara y se
 * descarta. El evento DISPENSACION lleva solo caja y farmacia.
 * Anulación: la misma farmacia, dentro de las 2 h, con motivo.
 */
@Service
public class DispensacionService {

    private final DispensacionRepository repository;
    private final UnidadTrazableRepository unidadTrazableRepository;
    private final EvaluadorBloqueo evaluadorBloqueo;
    private final UsuarioActual usuarioActual;
    private final VerificadorEmpresa verificadorEmpresa;
    private final VerificadorUsuario verificadorUsuario;
    private final RegistradorEventos registradorEventos;
    private final RegistradorEventosAparte registradorEventosAparte;

    @Autowired
    public DispensacionService(DispensacionRepository repository, UnidadTrazableRepository unidadTrazableRepository,
                               EvaluadorBloqueo evaluadorBloqueo, UsuarioActual usuarioActual,
                               VerificadorEmpresa verificadorEmpresa, VerificadorUsuario verificadorUsuario,
                               RegistradorEventos registradorEventos,
                               RegistradorEventosAparte registradorEventosAparte) {
        this.repository = repository;
        this.unidadTrazableRepository = unidadTrazableRepository;
        this.evaluadorBloqueo = evaluadorBloqueo;
        this.usuarioActual = usuarioActual;
        this.verificadorEmpresa = verificadorEmpresa;
        this.verificadorUsuario = verificadorUsuario;
        this.registradorEventos = registradorEventos;
        this.registradorEventosAparte = registradorEventosAparte;
    }

    /** Página de dispensaciones: SEDE e INSPECTOR todas; la farmacia, las suyas; el resto, ninguna. */
    @Transactional(readOnly = true)
    public Page<Dispensacion> getAll(Pageable pageable) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        return switch (actual.getRol()) {
            case SEDE_CENTRAL, INSPECTOR -> repository.findAll(pageable);
            case FARMACIA -> repository.findByFarmaciaId(actual.getEmpresaId(), pageable);
            case LABORATORIO, DISTRIBUIDOR, PACIENTE -> Page.empty(pageable);
        };
    }

    /** Busca una dispensación por id; si no es de la farmacia del usuario, 404. */
    @Transactional(readOnly = true)
    public Dispensacion getById(UUID id) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        Dispensacion dispensacion = buscar(id);
        boolean veTodo = actual.getRol() == RolUsuario.SEDE_CENTRAL || actual.getRol() == RolUsuario.INSPECTOR;
        if (!veTodo && !dispensacion.getFarmacia().getId().equals(actual.getEmpresaId())) {
            throw new ResourceNotFoundException("Dispensación no encontrada con id: " + id);
        }
        return dispensacion;
    }

    /** Dispensa una caja escaneada (GTIN + serie). Ver los controles en la descripción de la clase. */
    @Transactional
    public Dispensacion dispensar(DispensacionRequestDTO dto) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        Empresa farmacia = verificadorEmpresa.exigirHabilitada(actual.getEmpresaId());
        String gtin = Gs1Util.normalizar(dto.getGtin());
        Optional<UnidadTrazable> encontrada = unidadTrazableRepository.findByGtinAndSerie(gtin, dto.getSerie());
        if (encontrada.isEmpty()) {
            throw new ResourceNotFoundException("No existe una caja con GTIN " + gtin + " y serie " + dto.getSerie());
        }
        UnidadTrazable caja = encontrada.get();
        // Los intentos se registran ANTES de cualquier evento propio (condición de RegistradorEventosAparte).
        if (caja.getEstado() == EstadoUnidad.ROBADA) {
            registradorEventosAparte.registrar(TipoEvento.SERIE_ROBADA, "UnidadTrazable", caja.getId(),
                    DatosEventos.serieRobada(caja, "DISPENSACION"), actual.getUsuarioId(), farmacia.getId());
            throw new ReglaNegocioException("R14", "La caja fue reportada como robada: no se dispensa. Quedó registrado.");
        }
        if (caja.getEstado() == EstadoUnidad.DISPENSADA) {
            registradorEventosAparte.registrar(TipoEvento.INTENTO_DUPLICADO, "UnidadTrazable", caja.getId(),
                    DatosEventos.intentoDuplicado(caja, farmacia.getId()), actual.getUsuarioId(), farmacia.getId());
            throw new ReglaNegocioException("R11", "Esta caja ya fue dispensada: posible falsificación. Quedó registrado.");
        }
        if (!caja.estaEn(farmacia)) {
            throw new ResourceNotFoundException("La caja no está en el stock de tu farmacia");
        }
        if (caja.getEstado() != EstadoUnidad.EN_STOCK) {
            throw new ReglaNegocioException("TRANSICION_INVALIDA",
                    "La caja está en estado " + caja.getEstado() + ": no se puede dispensar");
        }
        Optional<Bloqueo> bloqueo = evaluadorBloqueo.bloqueoDeCaja(caja);
        if (bloqueo.isPresent()) {
            throw new ReglaNegocioException("R10", "La caja está bloqueada: " + bloqueo.get().getMensaje());
        }

        // R13: el DNI completo se enmascara acá y no se guarda en ningún lado.
        Dispensacion dispensacion = repository.save(new Dispensacion(caja, farmacia,
                verificadorUsuario.obtener(actual.getUsuarioId()), dto.getParticular(), dto.getObraSocial(),
                dto.getNumeroAfiliado(), dto.getNumeroReceta(), DniUtil.enmascarar(dto.getDni())));
        caja.dispensar();
        unidadTrazableRepository.save(caja);
        registradorEventos.registrar(TipoEvento.DISPENSACION, "Dispensacion", dispensacion.getId(),
                DatosEventos.dispensacion(dispensacion), actual);
        return dispensacion;
    }

    /** Anula una dispensación (R11): la misma farmacia, dentro de las 2 h, con motivo; la caja vuelve a EN_STOCK. */
    @Transactional
    public Dispensacion anular(UUID id, String motivo) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        Dispensacion dispensacion = buscar(id);
        if (!dispensacion.getFarmacia().getId().equals(actual.getEmpresaId())) {
            throw new ResourceNotFoundException("Dispensación no encontrada con id: " + id);
        }
        verificadorEmpresa.exigirHabilitada(actual.getEmpresaId());
        dispensacion.anular(motivo, Tiempo.ahora());
        UnidadTrazable caja = dispensacion.getUnidadTrazable();
        caja.anularDispensa();
        unidadTrazableRepository.save(caja);
        Dispensacion guardada = repository.save(dispensacion);
        registradorEventos.registrar(TipoEvento.ANULACION_DISPENSA, "Dispensacion", id,
                DatosEventos.anulacionDispensa(guardada, motivo), actual);
        return guardada;
    }

    /** Busca la dispensación o lanza 404. */
    private Dispensacion buscar(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dispensación no encontrada con id: " + id));
    }
}
