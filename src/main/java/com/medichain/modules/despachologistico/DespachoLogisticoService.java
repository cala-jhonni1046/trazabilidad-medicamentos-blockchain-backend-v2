package com.medichain.modules.despachologistico;

import com.medichain.exceptions.ReglaNegocioException;
import com.medichain.exceptions.ResourceNotFoundException;
import com.medichain.modules.auth.UsuarioAutenticado;
import com.medichain.modules.bulto.Bulto;
import com.medichain.modules.bulto.BultoRepository;
import com.medichain.modules.bulto.EstadoBulto;
import com.medichain.modules.cuarentena.AperturaCuarentenas;
import com.medichain.modules.cuarentena.Bloqueo;
import com.medichain.modules.cuarentena.EvaluadorBloqueo;
import com.medichain.modules.cuarentena.MotivoBloqueo;
import com.medichain.modules.empresa.Empresa;
import com.medichain.modules.trazabilidad.DatosEventos;
import com.medichain.modules.trazabilidad.RegistradorEventos;
import com.medichain.modules.trazabilidad.TipoEvento;
import com.medichain.modules.unidadtrazable.UnidadTrazable;
import com.medichain.modules.unidadtrazable.UnidadTrazableRepository;
import com.medichain.modules.usuario.RolUsuario;
import com.medichain.utils.seguridad.UsuarioActual;
import com.medichain.utils.seguridad.VerificadorEmpresa;
import com.medichain.utils.seguridad.VerificadorUsuario;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Servicio DespachoLogisticoService en MediChain (viajes, R7, R10, R14).
 * <ul>
 *   <li>Crear: el tramo lo decide el rol. Tramo 1 (LABORATORIO): bultos
 *       ARMADO propios, sin viaje, cuyos circuitos tienen TODOS la misma
 *       distribuidora. Tramo 2 (DISTRIBUIDOR): bultos EN_DEPOSITO en su
 *       depósito y de circuitos donde es la distribuidora (varias paradas).
 *       Ningún bulto bloqueado (R10).</li>
 *   <li>Salida, cancelación y robo: solo la empresa origen (otra → 404).
 *       La salida vuelve a verificar R10 (pudo abrirse una cuarentena).</li>
 * </ul>
 * Visibilidad: SEDE e INSPECTOR todo; LABORATORIO los que origina;
 * DISTRIBUIDOR los que origina y los del tramo 1 que vienen a su depósito;
 * FARMACIA los del tramo 2 con una parada en ella.
 */
@Service
public class DespachoLogisticoService {

    private final DespachoLogisticoRepository repository;
    private final BultoRepository bultoRepository;
    private final UnidadTrazableRepository unidadTrazableRepository;
    private final EvaluadorBloqueo evaluadorBloqueo;
    private final AperturaCuarentenas aperturaCuarentenas;
    private final UsuarioActual usuarioActual;
    private final VerificadorEmpresa verificadorEmpresa;
    private final VerificadorUsuario verificadorUsuario;
    private final RegistradorEventos registradorEventos;

    @Autowired
    public DespachoLogisticoService(DespachoLogisticoRepository repository, BultoRepository bultoRepository,
                                    UnidadTrazableRepository unidadTrazableRepository,
                                    EvaluadorBloqueo evaluadorBloqueo, AperturaCuarentenas aperturaCuarentenas,
                                    UsuarioActual usuarioActual, VerificadorEmpresa verificadorEmpresa,
                                    VerificadorUsuario verificadorUsuario, RegistradorEventos registradorEventos) {
        this.repository = repository;
        this.bultoRepository = bultoRepository;
        this.unidadTrazableRepository = unidadTrazableRepository;
        this.evaluadorBloqueo = evaluadorBloqueo;
        this.aperturaCuarentenas = aperturaCuarentenas;
        this.usuarioActual = usuarioActual;
        this.verificadorEmpresa = verificadorEmpresa;
        this.verificadorUsuario = verificadorUsuario;
        this.registradorEventos = registradorEventos;
    }

    /** Devuelve una página de viajes según el rol del usuario. */
    @Transactional(readOnly = true)
    public Page<DespachoLogistico> getAll(Pageable pageable) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        return switch (actual.getRol()) {
            case SEDE_CENTRAL, INSPECTOR -> repository.findAll(pageable);
            case LABORATORIO -> repository.findByOrigenId(actual.getEmpresaId(), pageable);
            case DISTRIBUIDOR -> repository.findVisiblesParaDistribuidor(actual.getEmpresaId(), pageable);
            case FARMACIA -> repository.findVisiblesParaFarmacia(actual.getEmpresaId(), pageable);
            case PACIENTE -> Page.empty(pageable);
        };
    }

    /** Busca un viaje por id; si el usuario no puede verlo, 404. */
    @Transactional(readOnly = true)
    public DespachoLogistico getById(UUID id) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        DespachoLogistico viaje = buscar(id);
        if (!puedeVer(actual, viaje)) {
            throw new ResourceNotFoundException("Viaje no encontrado con id: " + id);
        }
        return viaje;
    }

    /** Crea un viaje PROGRAMADO con sus bultos (R7, R10). Código VJ-0001 por secuencia. */
    @Transactional
    public DespachoLogistico crear(DespachoLogisticoRequestDTO dto) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        Empresa origen = verificadorEmpresa.exigirHabilitada(actual.getEmpresaId());
        TramoDespacho tramo = actual.getRol() == RolUsuario.LABORATORIO
                ? TramoDespacho.LAB_A_DISTRIBUIDOR
                : TramoDespacho.DISTRIBUIDOR_A_FARMACIA;
        List<Bulto> bultos = buscarBultos(dto.getBultos());
        if (tramo == TramoDespacho.LAB_A_DISTRIBUIDOR) {
            validarTramoUno(bultos, origen);
        } else {
            validarTramoDos(bultos, origen);
        }
        exigirNoBloqueados(bultos, "crear el viaje");

        String codigo = String.format("VJ-%04d", repository.siguienteNumeroCodigo());
        DespachoLogistico viaje = new DespachoLogistico(codigo, tramo, dto.getPatente(), dto.getChofer(),
                dto.getFechaEstimadaEntrega(), origen, verificadorUsuario.obtener(actual.getUsuarioId()));
        for (Bulto bulto : bultos) {
            viaje.agregarBulto(bulto);
        }
        DespachoLogistico guardado = repository.save(viaje);
        bultoRepository.saveAll(bultos);
        registradorEventos.registrar(TipoEvento.VIAJE_CREADO, "DespachoLogistico", guardado.getId(),
                DatosEventos.viajeCreado(guardado), actual);
        return guardado;
    }

    /**
     * Registra la salida (PROGRAMADO → EN_TRANSITO): bultos y cajas pasan a
     * EN_TRANSITO. Si algún bulto quedó bloqueado desde la creación → R10 y
     * no sale nada.
     */
    @Transactional
    public DespachoLogistico salida(UUID id) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        DespachoLogistico viaje = delOrigen(id, actual);
        List<Bulto> bultos = new ArrayList<>(viaje.getBultos());
        // R10 se verifica antes de cambiar nada (solo tiene sentido si el viaje todavía no salió).
        if (viaje.getEstado() == EstadoDespacho.PROGRAMADO) {
            exigirNoBloqueados(bultos, "registrar la salida");
        }
        viaje.registrarSalida();
        for (Bulto bulto : bultos) {
            bulto.salir();
        }
        List<UnidadTrazable> cajas = unidadTrazableRepository.findByBultoIdIn(idsDe(bultos));
        for (UnidadTrazable caja : cajas) {
            caja.salir();
        }
        bultoRepository.saveAll(bultos);
        unidadTrazableRepository.saveAll(cajas);
        DespachoLogistico guardado = repository.save(viaje);
        registradorEventos.registrar(TipoEvento.VIAJE_SALIDA, "DespachoLogistico", id,
                DatosEventos.viajeSalida(guardado), actual);
        return guardado;
    }

    /** Cancela un viaje PROGRAMADO, con motivo: sus bultos quedan sin viaje (siguen ARMADO / EN_DEPOSITO). */
    @Transactional
    public DespachoLogistico cancelar(UUID id, String motivo) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        DespachoLogistico viaje = delOrigen(id, actual);
        viaje.cancelar(motivo);
        bultoRepository.saveAll(viaje.getBultos());
        DespachoLogistico guardado = repository.save(viaje);
        registradorEventos.registrar(TipoEvento.VIAJE_CANCELADO, "DespachoLogistico", id,
                DatosEventos.viajeCancelado(guardado, motivo), actual);
        return guardado;
    }

    /**
     * Robo o extravío (R14): viaje ROBADO, bultos ROBADO, cajas ROBADA y
     * cuarentena DESPACHO automática (motivo ROBO) sobre todos sus bultos.
     */
    @Transactional
    public DespachoLogistico robo(UUID id, String motivo) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        DespachoLogistico viaje = delOrigen(id, actual);
        viaje.robar(motivo);
        List<Bulto> bultos = new ArrayList<>(viaje.getBultos());
        for (Bulto bulto : bultos) {
            bulto.robar();
        }
        List<UnidadTrazable> cajas = unidadTrazableRepository.findByBultoIdIn(idsDe(bultos));
        for (UnidadTrazable caja : cajas) {
            caja.robar();
        }
        bultoRepository.saveAll(bultos);
        unidadTrazableRepository.saveAll(cajas);
        DespachoLogistico guardado = repository.save(viaje);
        registradorEventos.registrar(TipoEvento.ROBO_EXTRAVIO, "DespachoLogistico", id,
                DatosEventos.roboExtravio(guardado, cajas.size(), motivo), actual);
        aperturaCuarentenas.abrirPorDespacho(MotivoBloqueo.ROBO, guardado, bultos);
        return guardado;
    }

    /** Busca los bultos por código; repetidos → 400-like R7; alguno inexistente → 404. */
    private List<Bulto> buscarBultos(List<String> codigos) {
        Set<String> unicos = new LinkedHashSet<>(codigos);
        if (unicos.size() != codigos.size()) {
            throw new ReglaNegocioException("R7", "La lista de bultos tiene códigos repetidos");
        }
        List<Bulto> bultos = bultoRepository.findByCodigoIn(unicos);
        if (bultos.size() != unicos.size()) {
            Set<String> encontrados = new HashSet<>();
            bultos.forEach(b -> encontrados.add(b.getCodigo()));
            List<String> faltan = unicos.stream().filter(c -> !encontrados.contains(c)).toList();
            throw new ResourceNotFoundException("Bultos no encontrados: " + faltan);
        }
        return bultos;
    }

    /**
     * Tramo 1 (R7): bultos ARMADO de MI laboratorio (ajenos → 404), sin
     * viaje, con circuito APROBADO, y todos hacia la MISMA distribuidora.
     */
    private void validarTramoUno(List<Bulto> bultos, Empresa laboratorio) {
        UUID distribuidoraId = null;
        for (Bulto bulto : bultos) {
            if (!bulto.getLote().getLaboratorio().getId().equals(laboratorio.getId())) {
                throw new ResourceNotFoundException("Bulto no encontrado: " + bulto.getCodigo());
            }
            if (bulto.getEstado() != EstadoBulto.ARMADO) {
                throw new ReglaNegocioException("R7", "El bulto " + bulto.getCodigo() + " no está ARMADO (estado "
                        + bulto.getEstado() + ")");
            }
            exigirSinViajeYCircuitoAprobado(bulto);
            UUID suDistribuidora = bulto.getDestino().getDistribuidor().getId();
            if (distribuidoraId == null) {
                distribuidoraId = suDistribuidora;
            } else if (!distribuidoraId.equals(suDistribuidora)) {
                throw new ReglaNegocioException("R7", "Un viaje del tramo 1 va a UNA sola distribuidora: el bulto "
                        + bulto.getCodigo() + " es de otra");
            }
        }
    }

    /**
     * Tramo 2 (R7): bultos EN_DEPOSITO en MI depósito, de circuitos donde soy
     * la distribuidora, sin viaje. Las paradas son sus farmacias.
     */
    private void validarTramoDos(List<Bulto> bultos, Empresa distribuidora) {
        for (Bulto bulto : bultos) {
            boolean esMio = bulto.getDestino().getDistribuidor().getId().equals(distribuidora.getId());
            boolean enMiDeposito = bulto.getEstado() == EstadoBulto.EN_DEPOSITO && bulto.getUbicacion() != null
                    && bulto.getUbicacion().getId().equals(distribuidora.getId());
            if (!esMio || !enMiDeposito) {
                throw new ReglaNegocioException("R7", "El bulto " + bulto.getCodigo()
                        + " no está EN_DEPOSITO en tu depósito para un circuito tuyo");
            }
            exigirSinViajeYCircuitoAprobado(bulto);
        }
    }

    /** El bulto no debe estar en otro viaje y su circuito debe seguir APROBADO (R6). */
    private void exigirSinViajeYCircuitoAprobado(Bulto bulto) {
        if (bulto.getViajeActual() != null) {
            throw new ReglaNegocioException("R7", "El bulto " + bulto.getCodigo() + " ya está en el viaje "
                    + bulto.getViajeActual().getCodigo());
        }
        if (!bulto.getDestino().estaAprobado()) {
            throw new ReglaNegocioException("R6", "El circuito del bulto " + bulto.getCodigo() + " no está APROBADO");
        }
    }

    /** R10: si algún bulto está bloqueado, 409 con los códigos y el motivo. */
    private void exigirNoBloqueados(List<Bulto> bultos, String accion) {
        Map<UUID, Bloqueo> porId = evaluadorBloqueo.bloqueosDeBultos(bultos);
        Map<String, String> bloqueados = new LinkedHashMap<>();
        for (Bulto bulto : bultos) {
            if (porId.containsKey(bulto.getId())) {
                bloqueados.put(bulto.getCodigo(), porId.get(bulto.getId()).getMensaje());
            }
        }
        if (!bloqueados.isEmpty()) {
            throw new ReglaNegocioException("R10", "No se puede " + accion + ": hay bultos bloqueados " + bloqueados);
        }
    }

    /** Viaje cuya empresa origen es la del usuario; si no, 404. */
    private DespachoLogistico delOrigen(UUID id, UsuarioAutenticado actual) {
        DespachoLogistico viaje = buscar(id);
        if (!viaje.getOrigen().getId().equals(actual.getEmpresaId())) {
            throw new ResourceNotFoundException("Viaje no encontrado con id: " + id);
        }
        return viaje;
    }

    /** Busca el viaje o lanza 404. */
    private DespachoLogistico buscar(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Viaje no encontrado con id: " + id));
    }

    /** Ids de los bultos. */
    private List<UUID> idsDe(List<Bulto> bultos) {
        return bultos.stream().map(Bulto::getId).toList();
    }

    /** Reglas de visibilidad del viaje según el rol. */
    private boolean puedeVer(UsuarioAutenticado actual, DespachoLogistico viaje) {
        UUID empresaId = actual.getEmpresaId();
        boolean esOrigen = viaje.getOrigen().getId().equals(empresaId);
        return switch (actual.getRol()) {
            case SEDE_CENTRAL, INSPECTOR -> true;
            case LABORATORIO -> esOrigen;
            case DISTRIBUIDOR -> esOrigen || (viaje.getTramo() == TramoDespacho.LAB_A_DISTRIBUIDOR
                    && viaje.getBultos().stream().anyMatch(b -> b.getDestino().getDistribuidor().getId().equals(empresaId)));
            case FARMACIA -> viaje.getTramo() == TramoDespacho.DISTRIBUIDOR_A_FARMACIA
                    && viaje.getBultos().stream().anyMatch(b -> b.getDestino().getFarmacia().getId().equals(empresaId));
            case PACIENTE -> false;
        };
    }
}
