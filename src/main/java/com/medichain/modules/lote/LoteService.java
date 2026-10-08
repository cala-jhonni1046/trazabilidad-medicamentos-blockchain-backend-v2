package com.medichain.modules.lote;

import com.medichain.exceptions.ReglaNegocioException;
import com.medichain.exceptions.ResourceNotFoundException;
import com.medichain.modules.auth.UsuarioAutenticado;
import com.medichain.modules.inspectoranmat.InspectorAnmat;
import com.medichain.modules.inspectoranmat.InspectorAnmatRepository;
import com.medichain.modules.medicamento.Medicamento;
import com.medichain.modules.medicamento.MedicamentoRepository;
import com.medichain.modules.trazabilidad.DatosEventos;
import com.medichain.modules.trazabilidad.HashUtil;
import com.medichain.modules.trazabilidad.RegistradorEventos;
import com.medichain.modules.trazabilidad.TipoEvento;
import com.medichain.modules.unidadtrazable.UnidadTrazable;
import com.medichain.modules.unidadtrazable.UnidadTrazableRepository;
import com.medichain.modules.usuario.RolUsuario;
import com.medichain.modules.usuario.Usuario;
import com.medichain.utils.RestriccionUnica;
import com.medichain.utils.seguridad.UsuarioActual;
import com.medichain.utils.seguridad.VerificadorEmpresa;
import com.medichain.utils.seguridad.VerificadorUsuario;
import com.medichain.utils.validacion.SerieUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Servicio LoteService en MediChain.
 * Lotes y sus cajas:
 * <ul>
 *   <li>Registro (R2, R3): el lote nace con TODAS sus cajas en una sola
 *       operación atómica. Si alguna serie es inválida, está repetida en la
 *       lista o ya existe para el mismo GTIN, no se crea nada y se registra
 *       INTENTO_SERIE_INVALIDA en una transacción aparte (RegistroIntentos).</li>
 *   <li>Liberación (R4): lote común → DT de su laboratorio; biológico →
 *       inspector ACTIVO de la provincia del laboratorio (D1, contra la base).
 *       Un lote vencido no se libera (R10).</li>
 * </ul>
 * Visibilidad: SEDE e INSPECTOR ven todo; el laboratorio, sus lotes;
 * DISTRIBUIDOR y FARMACIA, recién cuando un bulto del lote salió hacia
 * ellos (viaje del tramo 1 o 2 que ya salió).
 * TODO más adelante: la Sede asigna lotes biológicos de provincias sin inspectores.
 */
@Service
public class LoteService {

    /** Tamaño de las tandas de consulta de series existentes (IN de hasta 1000 valores). */
    private static final int TANDA_CONSULTA = 1000;
    private static final int MUESTRA_MAXIMA = 10;

    private final LoteRepository repository;
    private final MedicamentoRepository medicamentoRepository;
    private final UnidadTrazableRepository unidadTrazableRepository;
    private final InspectorAnmatRepository inspectorAnmatRepository;
    private final UsuarioActual usuarioActual;
    private final VerificadorEmpresa verificadorEmpresa;
    private final VerificadorUsuario verificadorUsuario;
    private final RegistroIntentos registroIntentos;
    private final RegistradorEventos registradorEventos;

    @Autowired
    public LoteService(LoteRepository repository, MedicamentoRepository medicamentoRepository,
                       UnidadTrazableRepository unidadTrazableRepository,
                       InspectorAnmatRepository inspectorAnmatRepository, UsuarioActual usuarioActual,
                       VerificadorEmpresa verificadorEmpresa, VerificadorUsuario verificadorUsuario,
                       RegistroIntentos registroIntentos, RegistradorEventos registradorEventos) {
        this.repository = repository;
        this.medicamentoRepository = medicamentoRepository;
        this.unidadTrazableRepository = unidadTrazableRepository;
        this.inspectorAnmatRepository = inspectorAnmatRepository;
        this.usuarioActual = usuarioActual;
        this.verificadorEmpresa = verificadorEmpresa;
        this.verificadorUsuario = verificadorUsuario;
        this.registroIntentos = registroIntentos;
        this.registradorEventos = registradorEventos;
    }

    /** Devuelve una página de lotes según el rol del usuario. */
    @Transactional(readOnly = true)
    public Page<Lote> getAll(Pageable pageable) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        return switch (actual.getRol()) {
            case SEDE_CENTRAL, INSPECTOR -> repository.findAll(pageable);
            case LABORATORIO -> repository.findByMedicamentoLaboratorioId(actual.getEmpresaId(), pageable);
            case DISTRIBUIDOR -> repository.findVisiblesParaDistribuidor(actual.getEmpresaId(), pageable);
            case FARMACIA -> repository.findVisiblesParaFarmacia(actual.getEmpresaId(), pageable);
            case PACIENTE -> Page.empty(pageable);
        };
    }

    /** Busca un lote por id; si el usuario no puede verlo, 404. */
    @Transactional(readOnly = true)
    public Lote getById(UUID id) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        Lote lote = buscar(id);
        if (!puedeVer(actual, lote)) {
            throw new ResourceNotFoundException("Lote no encontrado con id: " + id);
        }
        return lote;
    }

    /** Cajas de un lote, paginadas: laboratorio dueño, SEDE e INSPECTOR (los demás, 404). */
    @Transactional(readOnly = true)
    public Page<UnidadTrazable> unidades(UUID id, Pageable pageable) {
        Lote lote = getById(id);
        return unidadTrazableRepository.findByLoteId(lote.getId(), pageable);
    }

    /** Bandeja del inspector: lotes biológicos PENDIENTE_LIBERACION de laboratorios de su provincia. */
    @Transactional(readOnly = true)
    public Page<Lote> bandejaLiberacion(Pageable pageable) {
        InspectorAnmat inspector = inspectorActivo(usuarioActual.obtener());
        return repository.findBandejaLiberacion(inspector.getProvincia(), pageable);
    }

    /**
     * Registra un lote con todas sus cajas (operación atómica). Validaciones,
     * en orden: laboratorio HABILITADA (R2), medicamento propio (404), código
     * único en el laboratorio (LOTE_DUPLICADO), y las series (R3): formato,
     * repetidas dentro de la lista y ya existentes para el mismo GTIN.
     * Ante cualquier problema de series se registra INTENTO_SERIE_INVALIDA
     * en una transacción separada y se responde 409 R3 sin crear nada.
     * Carreras con otra alta simultánea: los índices únicos de la base
     * deciden y se traducen a LOTE_DUPLICADO (mismo código) o R3 + intento
     * (mismas series del mismo GTIN), nunca a un 409 genérico.
     */
    @Transactional
    public Lote registrar(LoteRequestDTO dto) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        verificadorEmpresa.exigirHabilitada(actual.getEmpresaId());
        UUID medicamentoId = dto.getMedicamentoId();
        Medicamento medicamento = medicamentoRepository.findById(medicamentoId)
                .filter(m -> m.getLaboratorio().getId().equals(actual.getEmpresaId()))
                .orElseThrow(() -> new ResourceNotFoundException("Medicamento no encontrado con id: " + medicamentoId));
        if (repository.existsByLaboratorioIdAndCodigo(actual.getEmpresaId(), dto.getCodigo())) {
            throw new ReglaNegocioException("LOTE_DUPLICADO",
                    "Tu laboratorio ya tiene un lote con el código " + dto.getCodigo());
        }

        boolean generadas = dto.getSeries() == null;
        List<String> series = generadas ? generarSeries(dto.getCodigo(), dto.getCantidad()) : dto.getSeries();
        // Todas las validaciones de series ocurren ANTES de registrar eventos en esta transacción
        // (condición para que RegistroIntentos, con REQUIRES_NEW, no espere el bloqueo de la cadena).
        validarSeries(dto.getCodigo(), medicamento, series, actual);

        Lote lote;
        try {
            // saveAndFlush: si otro alta del mismo código entró al mismo tiempo (las dos pasaron el control
            // de arriba), el índice ux_lote_laboratorio_codigo la rechaza ACÁ → LOTE_DUPLICADO, no un 409 genérico.
            lote = repository.saveAndFlush(new Lote(dto.getCodigo(), dto.getFechaFabricacion(),
                    dto.getFechaVencimiento(), series.size(), medicamento));
        } catch (DataIntegrityViolationException e) {
            if (RestriccionUnica.es(e, "ux_lote_laboratorio_codigo")) {
                throw new ReglaNegocioException("LOTE_DUPLICADO",
                        "Tu laboratorio ya tiene un lote con el código " + dto.getCodigo());
            }
            throw e;
        }
        List<UnidadTrazable> cajas = new ArrayList<>(series.size());
        for (String serie : series) {
            cajas.add(new UnidadTrazable(serie, lote));
        }
        try {
            // Inserción en tandas (hibernate.jdbc.batch_size): los ids UUID los genera Hibernate, sin IDENTITY.
            // saveAllAndFlush: una carrera de series del mismo GTIN la rechaza ux_unidad_gtin_serie ACÁ, antes de
            // registrar el evento propio (si no, el intento con REQUIRES_NEW esperaría el bloqueo de la cadena).
            unidadTrazableRepository.saveAllAndFlush(cajas);
        } catch (DataIntegrityViolationException e) {
            if (RestriccionUnica.es(e, "ux_unidad_gtin_serie")) {
                List<String> existentes = registroIntentos.registrarChoqueDeSeries(dto.getCodigo(), medicamento,
                        series, HashUtil.seriesHash(series), actual);
                throw new ReglaNegocioException("R3", "El lote no se registró: " + existentes.size()
                        + " series ya existen para el GTIN " + medicamento.getGtin() + " (otro lote las registró al "
                        + "mismo tiempo). Ejemplos: " + existentes.subList(0, Math.min(MUESTRA_MAXIMA, existentes.size())));
            }
            throw e;
        }

        registradorEventos.registrar(TipoEvento.LOTE_REGISTRADO, "Lote", lote.getId(),
                DatosEventos.loteRegistrado(lote, HashUtil.seriesHash(series), generadas ? "GENERADAS" : "LISTA"),
                actual);
        return lote;
    }

    /**
     * Libera el lote (R4): común → DT de su laboratorio; biológico →
     * inspector ACTIVO de la provincia del laboratorio. Un lote vencido no
     * se libera (R10). La transición la valida Lote.liberar.
     */
    @Transactional
    public Lote liberar(UUID id) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        Lote lote = buscar(id);
        boolean biologico = Boolean.TRUE.equals(lote.getMedicamento().getBiologico());
        Usuario quien;
        String liberadoComo;
        UUID inspectorId = null;
        if (actual.getRol() == RolUsuario.INSPECTOR) {
            InspectorAnmat inspector = inspectorActivo(actual);
            if (lote.getLaboratorio().getProvincia() != inspector.getProvincia()) {
                throw new ResourceNotFoundException("Lote no encontrado con id: " + id);
            }
            if (!biologico) {
                throw new ReglaNegocioException("R4", "Un lote de un medicamento común lo libera el director técnico "
                        + "de su laboratorio, no un inspector");
            }
            quien = inspector.getUsuario();
            liberadoComo = "INSPECTOR";
            inspectorId = inspector.getId();
        } else {
            if (!lote.getLaboratorio().getId().equals(actual.getEmpresaId())) {
                throw new ResourceNotFoundException("Lote no encontrado con id: " + id);
            }
            if (biologico) {
                throw new ReglaNegocioException("R4", "Un lote de un medicamento biológico lo libera un inspector "
                        + "de la provincia del laboratorio, no el director técnico");
            }
            quien = verificadorUsuario.exigirDirectorTecnico(actual.getUsuarioId());
            verificadorEmpresa.exigirHabilitada(actual.getEmpresaId());
            liberadoComo = "DIRECTOR_TECNICO";
        }
        if (lote.estaVencido()) {
            throw new ReglaNegocioException("R10", "El lote está vencido: no se puede liberar");
        }
        lote.liberar(quien);
        Lote guardado = repository.save(lote);
        registradorEventos.registrar(TipoEvento.LOTE_LIBERADO, "Lote", id,
                DatosEventos.loteLiberado(guardado, liberadoComo, inspectorId), actual);
        return guardado;
    }

    /** Genera las series: código del lote sin guiones + "S" + 6 dígitos (L2026-0002 → L20260002S000001). */
    private List<String> generarSeries(String codigoLote, int cantidad) {
        String prefijo = codigoLote.replace("-", "") + "S";
        List<String> series = new ArrayList<>(cantidad);
        for (int i = 1; i <= cantidad; i++) {
            series.add(prefijo + String.format("%06d", i));
        }
        return series;
    }

    /**
     * R3: formato (SerieUtil), repetidas dentro de la lista y ya existentes
     * para el mismo GTIN. Si hay problemas, registra el intento (transacción
     * aparte) y lanza 409 R3 con el resumen.
     */
    private void validarSeries(String codigoLote, Medicamento medicamento, List<String> series,
                               UsuarioAutenticado actual) {
        Set<String> problemas = new LinkedHashSet<>();
        Set<String> vistas = new HashSet<>();
        List<String> validasUnicas = new ArrayList<>();
        int invalidas = 0;
        int duplicadas = 0;
        for (String serie : series) {
            if (!SerieUtil.esValida(serie)) {
                invalidas++;
                problemas.add(String.valueOf(serie));
            } else if (!vistas.add(serie)) {
                duplicadas++;
                problemas.add(serie);
            } else {
                validasUnicas.add(serie);
            }
        }
        int existentes = 0;
        for (int desde = 0; desde < validasUnicas.size(); desde += TANDA_CONSULTA) {
            List<String> tanda = validasUnicas.subList(desde, Math.min(desde + TANDA_CONSULTA, validasUnicas.size()));
            List<String> yaExisten = unidadTrazableRepository.findSeriesExistentes(medicamento.getGtin(), tanda);
            existentes += yaExisten.size();
            problemas.addAll(yaExisten);
        }
        if (invalidas + duplicadas + existentes == 0) {
            return;
        }
        List<String> muestra = new ArrayList<>(problemas).subList(0, Math.min(MUESTRA_MAXIMA, problemas.size()));
        registroIntentos.registrarIntentoSerieInvalida(codigoLote, medicamento, series.size(), invalidas, duplicadas,
                existentes, new ArrayList<>(muestra), HashUtil.seriesHash(sinNulos(series)), actual);
        throw new ReglaNegocioException("R3", "El lote no se registró: " + invalidas + " series con formato inválido, "
                + duplicadas + " repetidas en la lista y " + existentes + " ya existentes para el GTIN "
                + medicamento.getGtin() + ". Ejemplos: " + muestra);
    }

    /** Reemplaza nulls por "null" para poder calcular el hash de una lista recibida con elementos vacíos. */
    private List<String> sinNulos(List<String> series) {
        List<String> copia = new ArrayList<>(series.size());
        for (String serie : series) {
            copia.add(String.valueOf(serie));
        }
        return copia;
    }

    /** Busca el lote o lanza 404. */
    private Lote buscar(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lote no encontrado con id: " + id));
    }

    /** D1: inspector ACTIVO de la cuenta autenticada, leído de la base (no del token). */
    private InspectorAnmat inspectorActivo(UsuarioAutenticado actual) {
        return inspectorAnmatRepository.findByUsuarioId(actual.getUsuarioId())
                .filter(InspectorAnmat::estaActivo)
                .orElseThrow(() -> new AccessDeniedException("La cuenta no corresponde a un inspector activo"));
    }

    /** SEDE e INSPECTOR ven todo; el laboratorio, sus lotes; DIST y FARM, los que ya salieron hacia ellos. */
    private boolean puedeVer(UsuarioAutenticado actual, Lote lote) {
        return switch (actual.getRol()) {
            case SEDE_CENTRAL, INSPECTOR -> true;
            case LABORATORIO -> lote.getLaboratorio().getId().equals(actual.getEmpresaId());
            case DISTRIBUIDOR -> repository.esVisibleParaDistribuidor(lote.getId(), actual.getEmpresaId());
            case FARMACIA -> repository.esVisibleParaFarmacia(lote.getId(), actual.getEmpresaId());
            case PACIENTE -> false;
        };
    }
}
