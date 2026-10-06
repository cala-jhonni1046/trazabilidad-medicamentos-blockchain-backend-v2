package com.medichain.modules.recepcion;

import com.medichain.exceptions.ReglaNegocioException;
import com.medichain.exceptions.ResourceNotFoundException;
import com.medichain.modules.auth.UsuarioAutenticado;
import com.medichain.modules.bulto.Bulto;
import com.medichain.modules.bulto.BultoRepository;
import com.medichain.modules.bulto.EstadoBulto;
import com.medichain.modules.cuarentena.AperturaCuarentenas;
import com.medichain.modules.cuarentena.EvaluadorBloqueo;
import com.medichain.modules.despachologistico.DespachoLogistico;
import com.medichain.modules.despachologistico.DespachoLogisticoRepository;
import com.medichain.modules.despachologistico.EstadoDespacho;
import com.medichain.modules.despachologistico.TramoDespacho;
import com.medichain.modules.empresa.Empresa;
import com.medichain.modules.telemetriatemperatura.TelemetriaTemperaturaRepository;
import com.medichain.modules.trazabilidad.DatosEventos;
import com.medichain.modules.trazabilidad.RegistradorEventos;
import com.medichain.modules.trazabilidad.RegistradorEventosAparte;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Servicio RecepcionService en MediChain (R8, R10, R7).
 * Recepción escaneando el CÓDIGO del bulto. Solo la empresa destino actual
 * del bulto (tramo 1: la distribuidora; tramo 2: la farmacia), con el viaje
 * EN_TRANSITO. El servidor decide si es conforme: precinto intacto,
 * cantidad igual a la del bulto y temperatura de llegada dentro del rango
 * de SU medicamento. Un bulto bloqueado (R10) se registra y se rechaza
 * automáticamente: llegó físicamente y su viaje tiene que poder terminar,
 * pero nunca entra a depósito ni a stock.
 * <ul>
 *   <li>Conforme: tramo 1 → bulto y cajas EN_DEPOSITO; tramo 2 → bulto
 *       RECIBIDO y cajas EN_STOCK. Evento BULTO_RECIBIDO.</li>
 *   <li>No conforme: bulto RECHAZADO, cajas RECHAZADA y cuarentena BULTO
 *       automática (salvo que ya estuviera bloqueado). Eventos
 *       BULTO_RECHAZADO (+ CUARENTENA).</li>
 *   <li>Código inexistente → 404 + BULTO_INEXISTENTE; bulto que la empresa
 *       ya recibió → 409 + BULTO_DUPLICADO. Ambos en transacción aparte.</li>
 *   <li>Si con esta recepción ningún bulto del viaje queda en curso, el
 *       viaje pasa a FINALIZADO (VIAJE_FINALIZADO con el resumen de temperatura).</li>
 * </ul>
 */
@Service
public class RecepcionService {

    private final RecepcionRepository repository;
    private final BultoRepository bultoRepository;
    private final DespachoLogisticoRepository despachoLogisticoRepository;
    private final UnidadTrazableRepository unidadTrazableRepository;
    private final TelemetriaTemperaturaRepository telemetriaTemperaturaRepository;
    private final EvaluadorBloqueo evaluadorBloqueo;
    private final AperturaCuarentenas aperturaCuarentenas;
    private final UsuarioActual usuarioActual;
    private final VerificadorEmpresa verificadorEmpresa;
    private final VerificadorUsuario verificadorUsuario;
    private final RegistradorEventos registradorEventos;
    private final RegistradorEventosAparte registradorEventosAparte;

    @Autowired
    public RecepcionService(RecepcionRepository repository, BultoRepository bultoRepository,
                            DespachoLogisticoRepository despachoLogisticoRepository,
                            UnidadTrazableRepository unidadTrazableRepository,
                            TelemetriaTemperaturaRepository telemetriaTemperaturaRepository,
                            EvaluadorBloqueo evaluadorBloqueo, AperturaCuarentenas aperturaCuarentenas,
                            UsuarioActual usuarioActual, VerificadorEmpresa verificadorEmpresa,
                            VerificadorUsuario verificadorUsuario, RegistradorEventos registradorEventos,
                            RegistradorEventosAparte registradorEventosAparte) {
        this.repository = repository;
        this.bultoRepository = bultoRepository;
        this.despachoLogisticoRepository = despachoLogisticoRepository;
        this.unidadTrazableRepository = unidadTrazableRepository;
        this.telemetriaTemperaturaRepository = telemetriaTemperaturaRepository;
        this.evaluadorBloqueo = evaluadorBloqueo;
        this.aperturaCuarentenas = aperturaCuarentenas;
        this.usuarioActual = usuarioActual;
        this.verificadorEmpresa = verificadorEmpresa;
        this.verificadorUsuario = verificadorUsuario;
        this.registradorEventos = registradorEventos;
        this.registradorEventosAparte = registradorEventosAparte;
    }

    /** Página de recepciones: SEDE e INSPECTOR todas; una empresa, las suyas, las de sus viajes y sus lotes. */
    @Transactional(readOnly = true)
    public Page<Recepcion> getAll(Pageable pageable) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        return switch (actual.getRol()) {
            case SEDE_CENTRAL, INSPECTOR -> repository.findAll(pageable);
            case LABORATORIO, DISTRIBUIDOR, FARMACIA -> repository.findVisiblesParaEmpresa(actual.getEmpresaId(), pageable);
            case PACIENTE -> Page.empty(pageable);
        };
    }

    /** Busca una recepción por id; si el usuario no puede verla, 404. */
    @Transactional(readOnly = true)
    public Recepcion getById(UUID id) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        Recepcion recepcion = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recepción no encontrada con id: " + id));
        if (!puedeVer(actual, recepcion)) {
            throw new ResourceNotFoundException("Recepción no encontrada con id: " + id);
        }
        return recepcion;
    }

    /** Recibe un bulto escaneando su código (R8). Ver la descripción de la clase. */
    @Transactional
    public Recepcion recibir(RecepcionRequestDTO dto) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        Empresa receptora = verificadorEmpresa.exigirHabilitada(actual.getEmpresaId());

        // Los intentos se registran ANTES de cualquier evento propio (condición de RegistradorEventosAparte).
        Optional<Bulto> encontrado = bultoRepository.findByCodigo(dto.getCodigoBulto());
        if (encontrado.isEmpty()) {
            registradorEventosAparte.registrar(TipoEvento.BULTO_INEXISTENTE, "Empresa", receptora.getId(),
                    DatosEventos.bultoInexistente(dto.getCodigoBulto(), receptora.getId()),
                    actual.getUsuarioId(), receptora.getId());
            throw new ResourceNotFoundException("No existe un bulto con código " + dto.getCodigoBulto());
        }
        Bulto bulto = encontrado.get();
        if (repository.existsByBultoIdAndReceptoraId(bulto.getId(), receptora.getId())) {
            registradorEventosAparte.registrar(TipoEvento.BULTO_DUPLICADO, "Bulto", bulto.getId(),
                    DatosEventos.bultoDuplicado(bulto, receptora.getId()), actual.getUsuarioId(), receptora.getId());
            throw new ReglaNegocioException("TRANSICION_INVALIDA", "El bulto " + bulto.getCodigo()
                    + " ya fue recibido por tu empresa (estado " + bulto.getEstado() + ")");
        }
        Empresa destino = bulto.destinoActual();
        if (destino == null || !destino.getId().equals(receptora.getId())) {
            throw new ResourceNotFoundException("No existe un bulto con código " + dto.getCodigoBulto()
                    + " destinado a tu empresa");
        }
        DespachoLogistico viaje = bulto.getViajeActual();
        if (bulto.getEstado() != EstadoBulto.EN_TRANSITO || viaje == null || viaje.getEstado() != EstadoDespacho.EN_TRANSITO) {
            throw new ReglaNegocioException("TRANSICION_INVALIDA", "El bulto " + bulto.getCodigo()
                    + " no está en viaje (estado " + bulto.getEstado() + ")");
        }

        boolean bloqueado = !evaluadorBloqueo.bultosBloqueados(List.of(bulto)).isEmpty();
        List<MotivoRechazoRecepcion> motivos = motivosDeRechazo(bulto, dto, bloqueado);
        Recepcion recepcion = repository.save(new Recepcion(bulto, viaje, receptora,
                verificadorUsuario.obtener(actual.getUsuarioId()), dto.getTemperatura(), dto.getPrecintoIntacto(),
                dto.getCantidadVerificada(), motivos, dto.getObservacion()));

        List<UnidadTrazable> cajas = unidadTrazableRepository.findByBultoId(bulto.getId());
        if (recepcion.getConforme()) {
            boolean tramoUno = viaje.getTramo() == TramoDespacho.LAB_A_DISTRIBUIDOR;
            bulto.recibir(receptora);
            for (UnidadTrazable caja : cajas) {
                if (tramoUno) {
                    caja.recibirEnDeposito(receptora);
                } else {
                    caja.recibirEnFarmacia(receptora);
                }
            }
        } else {
            bulto.rechazar(receptora);
            for (UnidadTrazable caja : cajas) {
                caja.quedarRechazadaEn(receptora);
            }
        }
        bultoRepository.save(bulto);
        unidadTrazableRepository.saveAll(cajas);
        registradorEventos.registrar(recepcion.getConforme() ? TipoEvento.BULTO_RECIBIDO : TipoEvento.BULTO_RECHAZADO,
                "Bulto", bulto.getId(), DatosEventos.recepcion(recepcion), actual);
        // Un bulto ya bloqueado tiene su medida vigente: no se duplica la cuarentena.
        if (!recepcion.getConforme() && !bloqueado) {
            aperturaCuarentenas.abrirPorBulto(bulto);
        }
        finalizarViajeSiCorresponde(viaje, actual);
        return recepcion;
    }

    /** Motivos de rechazo calculados por el servidor (vacío = conforme). */
    private List<MotivoRechazoRecepcion> motivosDeRechazo(Bulto bulto, RecepcionRequestDTO dto, boolean bloqueado) {
        List<MotivoRechazoRecepcion> motivos = new ArrayList<>();
        if (bloqueado) {
            motivos.add(MotivoRechazoRecepcion.BULTO_BLOQUEADO);
        }
        if (!Boolean.TRUE.equals(dto.getPrecintoIntacto())) {
            motivos.add(MotivoRechazoRecepcion.PRECINTO_ROTO);
        }
        if (!bulto.getCantidad().equals(dto.getCantidadVerificada())) {
            motivos.add(MotivoRechazoRecepcion.CANTIDAD_DISTINTA);
        }
        if (!bulto.getLote().getMedicamento().estaEnRango(dto.getTemperatura())) {
            motivos.add(MotivoRechazoRecepcion.TEMPERATURA_FUERA_DE_RANGO);
        }
        return motivos;
    }

    /** Si ningún bulto del viaje sigue en curso, lo finaliza y emite VIAJE_FINALIZADO con el resumen. */
    private void finalizarViajeSiCorresponde(DespachoLogistico viaje, UsuarioAutenticado actual) {
        if (!viaje.finalizarSiCorresponde()) {
            return;
        }
        despachoLogisticoRepository.save(viaje);
        List<Recepcion> recepciones = repository.findByDespachoId(viaje.getId());
        int recibidos = (int) recepciones.stream().filter(Recepcion::getConforme).count();
        registradorEventos.registrar(TipoEvento.VIAJE_FINALIZADO, "DespachoLogistico", viaje.getId(),
                DatosEventos.viajeFinalizado(viaje, recibidos, recepciones.size() - recibidos,
                        telemetriaTemperaturaRepository.findByDespachoId(viaje.getId())), actual);
    }

    /** SEDE e INSPECTOR todo; una empresa: si la recibió, si originó el viaje o si el bulto es de sus lotes. */
    private boolean puedeVer(UsuarioAutenticado actual, Recepcion recepcion) {
        if (actual.getRol() == RolUsuario.SEDE_CENTRAL || actual.getRol() == RolUsuario.INSPECTOR) {
            return true;
        }
        UUID empresaId = actual.getEmpresaId();
        return recepcion.getReceptora().getId().equals(empresaId)
                || recepcion.getDespacho().getOrigen().getId().equals(empresaId)
                || recepcion.getBulto().getLote().getLaboratorio().getId().equals(empresaId);
    }
}
