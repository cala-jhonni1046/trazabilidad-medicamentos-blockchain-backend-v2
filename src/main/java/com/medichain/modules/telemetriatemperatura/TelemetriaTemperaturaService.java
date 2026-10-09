package com.medichain.modules.telemetriatemperatura;

import com.medichain.exceptions.ReglaNegocioException;
import com.medichain.exceptions.ResourceNotFoundException;
import com.medichain.modules.cuarentena.AperturaCuarentenas;
import com.medichain.modules.cuarentena.EvaluadorBloqueo;
import com.medichain.modules.cuarentena.MotivoBloqueo;
import com.medichain.modules.despachologistico.EstadoDespacho;
import com.medichain.modules.auth.UsuarioAutenticado;
import com.medichain.modules.bulto.Bulto;
import com.medichain.modules.despachologistico.DespachoLogistico;
import com.medichain.modules.despachologistico.DespachoLogisticoRepository;
import com.medichain.modules.usuario.RolUsuario;
import com.medichain.utils.seguridad.UsuarioActual;
import com.medichain.utils.seguridad.VerificadorEmpresa;
import com.medichain.modules.trazabilidad.DatosEventos;
import com.medichain.modules.trazabilidad.RegistradorEventos;
import com.medichain.modules.trazabilidad.TipoEvento;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Servicio TelemetriaTemperaturaService en MediChain.
 * Lecturas de temperatura (D3): SEDE e INSPECTOR ven todas; LABORATORIO
 * y DISTRIBUIDOR ven las de los despachos que originan, y solo la
 * empresa origen del despacho registra lecturas. fueraDeRango lo calcula
 * el servidor contra el rango de los medicamentos que viajan en el
 * despacho; el cliente nunca lo informa.
 */
@Service
public class TelemetriaTemperaturaService {

    private final TelemetriaTemperaturaRepository repository;
    private final DespachoLogisticoRepository despachoLogisticoRepository;
    private final UsuarioActual usuarioActual;
    private final VerificadorEmpresa verificadorEmpresa;
    private final RegistradorEventos registradorEventos;
    private final EvaluadorBloqueo evaluadorBloqueo;
    private final AperturaCuarentenas aperturaCuarentenas;

    @Autowired
    public TelemetriaTemperaturaService(TelemetriaTemperaturaRepository repository,
                                        DespachoLogisticoRepository despachoLogisticoRepository,
                                        UsuarioActual usuarioActual, VerificadorEmpresa verificadorEmpresa,
                                        RegistradorEventos registradorEventos,
                                        EvaluadorBloqueo evaluadorBloqueo, AperturaCuarentenas aperturaCuarentenas) {
        this.repository = repository;
        this.despachoLogisticoRepository = despachoLogisticoRepository;
        this.usuarioActual = usuarioActual;
        this.verificadorEmpresa = verificadorEmpresa;
        this.registradorEventos = registradorEventos;
        this.evaluadorBloqueo = evaluadorBloqueo;
        this.aperturaCuarentenas = aperturaCuarentenas;
    }

    /** Devuelve una página de lecturas según el rol del usuario. */
    @Transactional(readOnly = true)
    public Page<TelemetriaTemperatura> getAll(Pageable pageable) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        return switch (actual.getRol()) {
            case SEDE_CENTRAL, INSPECTOR -> repository.findAll(pageable);
            // La empresa ve las lecturas de sus viajes y las de los viajes que recibe (receptora de ese tramo).
            case LABORATORIO, DISTRIBUIDOR, FARMACIA -> repository.findVisiblesParaEmpresa(actual.getEmpresaId(), pageable);
            case PACIENTE -> Page.empty(pageable);
        };
    }

    /** Busca una lectura por id; si su despacho no es de la empresa del usuario, 404. */
    @Transactional(readOnly = true)
    public TelemetriaTemperatura getById(UUID id) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        TelemetriaTemperatura lectura = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TelemetriaTemperatura no encontrada con id: " + id));
        if (!veLaTemperatura(actual, lectura.getDespacho())) {
            throw new ResourceNotFoundException("TelemetriaTemperatura no encontrada con id: " + id);
        }
        return lectura;
    }

    /**
     * Lecturas de temperatura de un viaje, para su gráfico. Las ve la Sede, los
     * inspectores, la empresa origen y la receptora de ese tramo (distribuidora
     * en el tramo 1, farmacia en el tramo 2). Viaje inexistente o ajeno → 404.
     */
    @Transactional(readOnly = true)
    public Page<TelemetriaTemperatura> lecturasDelViaje(UUID viajeId, Pageable pageable) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        DespachoLogistico viaje = despachoLogisticoRepository.findById(viajeId)
                .filter(d -> veLaTemperatura(actual, d))
                .orElseThrow(() -> new ResourceNotFoundException("Viaje no encontrado con id: " + viajeId));
        return repository.findDelViaje(viaje.getId(), pageable);
    }

    /** Regla de visibilidad de la temperatura de un viaje: Sede, inspectores, origen y receptora del tramo. */
    private boolean veLaTemperatura(UsuarioAutenticado actual, DespachoLogistico viaje) {
        if (actual.getRol() == RolUsuario.SEDE_CENTRAL || actual.getRol() == RolUsuario.INSPECTOR) {
            return true;
        }
        if (actual.getEmpresaId() == null) {
            return false;
        }
        if (viaje.getOrigen().getId().equals(actual.getEmpresaId())) {
            return true;
        }
        return viaje.paradas().stream().anyMatch(parada -> parada.getId().equals(actual.getEmpresaId()));
    }

    /**
     * Registra una lectura (R9). Solo la empresa origen del viaje (otra → 404)
     * y solo con el viaje EN_TRANSITO (si no, TRANSICION_INVALIDA). El
     * servidor evalúa CADA bulto contra el rango de SU medicamento: si alguno
     * queda fuera, emite RUPTURA_FRIO y abre la cuarentena automática
     * DESPACHO solo sobre los bultos afectados que todavía no tienen una
     * medida vigente. Una lectura fuera de rango sin bultos nuevos afectados
     * se guarda como telemetría, sin eventos.
     */
    @Transactional
    public TelemetriaTemperatura create(TelemetriaTemperaturaRequestDTO dto) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        verificadorEmpresa.exigirHabilitada(actual.getEmpresaId());
        DespachoLogistico despacho = viajePropioEnTransito(dto.getDespachoId(), actual);
        List<Bulto> afectados = bultosFueraDeRango(despacho, dto.getTemperatura());
        // fueraDeRango lo calcula el servidor (cada bulto contra el rango de su medicamento), no el cliente.
        TelemetriaTemperatura guardada = repository.save(new TelemetriaTemperatura(dto.getSensorId(),
                dto.getTemperatura(), !afectados.isEmpty(), dto.getFechaHora().toInstant(), despacho));
        if (afectados.isEmpty()) {
            return guardada;
        }
        Set<UUID> yaEnCuarentena = evaluadorBloqueo.bultosConMedidaVigente(
                afectados.stream().map(Bulto::getId).toList());
        List<Bulto> nuevos = afectados.stream().filter(b -> !yaEnCuarentena.contains(b.getId())).toList();
        // D4 / R9: solo entra a la cadena la ruptura que afecta bultos nuevos; las repetidas quedan como telemetría.
        if (!nuevos.isEmpty()) {
            registradorEventos.registrar(TipoEvento.RUPTURA_FRIO, "DespachoLogistico", despacho.getId(),
                    DatosEventos.rupturaFrio(guardada, nuevos), actual);
            aperturaCuarentenas.abrirPorDespacho(MotivoBloqueo.RUPTURA_FRIO, despacho, nuevos);
        }
        return guardada;
    }

    /** Viaje de la empresa del usuario (otro → 404) que esté EN_TRANSITO (si no, TRANSICION_INVALIDA). */
    private DespachoLogistico viajePropioEnTransito(UUID despachoId, UsuarioAutenticado actual) {
        DespachoLogistico despacho = despachoLogisticoRepository.findById(despachoId)
                .filter(d -> d.getOrigen().getId().equals(actual.getEmpresaId()))
                .orElseThrow(() -> new ResourceNotFoundException("Viaje no encontrado con id: " + despachoId));
        if (despacho.getEstado() != EstadoDespacho.EN_TRANSITO) {
            throw new ReglaNegocioException("TRANSICION_INVALIDA",
                    "Solo se registra telemetría de un viaje EN_TRANSITO (estado " + despacho.getEstado() + ")");
        }
        return despacho;
    }

    /** Bultos del viaje cuyo medicamento no admite la temperatura leída (cada uno con su propio rango). */
    private List<Bulto> bultosFueraDeRango(DespachoLogistico despacho, BigDecimal temperatura) {
        List<Bulto> afectados = new ArrayList<>();
        for (Bulto bulto : despacho.getBultos()) {
            if (!bulto.getLote().getMedicamento().estaEnRango(temperatura)) {
                afectados.add(bulto);
            }
        }
        return afectados;
    }
}
