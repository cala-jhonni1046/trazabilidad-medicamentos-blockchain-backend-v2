package com.medichain.modules.inspectoranmat;

import com.medichain.exceptions.ReglaNegocioException;
import com.medichain.exceptions.ResourceNotFoundException;
import com.medichain.modules.auth.UsuarioAutenticado;
import com.medichain.modules.empresa.Empresa;
import com.medichain.modules.empresa.EmpresaRepository;
import com.medichain.modules.empresa.EstadoHabilitacion;
import com.medichain.modules.usuario.RolUsuario;
import com.medichain.modules.usuario.Usuario;
import com.medichain.modules.usuario.UsuarioRepository;
import com.medichain.utils.seguridad.UsuarioActual;
import com.medichain.utils.seguridad.VerificadorUsuario;
import com.medichain.modules.trazabilidad.DatosEventos;
import com.medichain.modules.trazabilidad.RegistradorEventos;
import com.medichain.modules.trazabilidad.TipoEvento;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Servicio InspectorAnmatService en MediChain.
 * Lectura (SEDE_CENTRAL e INSPECTOR, sin filtrado) y alta de inspectores.
 * Por R1 el alta la hace solo la Sede (lo controla @PreAuthorize) y crea
 * en la misma transacción la cuenta de usuario con rol INSPECTOR: es el
 * único camino para crear inspectores. usuarioAlta sale del token.
 * Baja y reactivación (R1, solo Sede): la baja deja inactiva la cuenta
 * (no puede hacer login) y devuelve a la bandeja las solicitudes que el
 * inspector tenía tomadas; la reactivación no se las devuelve.
 */
@Service
public class InspectorAnmatService {

    private final InspectorAnmatRepository repository;
    private final UsuarioRepository usuarioRepository;
    private final EmpresaRepository empresaRepository;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioActual usuarioActual;
    private final VerificadorUsuario verificadorUsuario;
    private final RegistradorEventos registradorEventos;

    @Autowired
    public InspectorAnmatService(InspectorAnmatRepository repository, UsuarioRepository usuarioRepository,
                                 PasswordEncoder passwordEncoder, UsuarioActual usuarioActual,
                                 VerificadorUsuario verificadorUsuario,
                                 RegistradorEventos registradorEventos, EmpresaRepository empresaRepository) {
        this.repository = repository;
        this.empresaRepository = empresaRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.usuarioActual = usuarioActual;
        this.verificadorUsuario = verificadorUsuario;
        this.registradorEventos = registradorEventos;
    }

    /** Devuelve una página de inspectores (todos: lo ven solo SEDE e INSPECTOR). */
    @Transactional(readOnly = true)
    public Page<InspectorAnmat> getAll(EstadoInspector estado, Pageable pageable) {
        return repository.findPorEstado(estado, pageable);
    }

    /** Busca un inspector por id o lanza ResourceNotFoundException si no existe. */
    @Transactional(readOnly = true)
    public InspectorAnmat getById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("InspectorAnmat no encontrado con id: " + id));
    }

    /**
     * Crea la cuenta INSPECTOR (contraseña hasheada) y el inspector
     * asociado. usuarioAlta es el usuario de la Sede autenticado.
     */
    @Transactional
    public InspectorAnmat create(InspectorAnmat entity, InspectorAnmatRequestDTO dto) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        Usuario usuarioAlta = verificadorUsuario.obtener(actual.getUsuarioId());
        // Legajo y DNI son únicos: se avisa antes de crear la cuenta (el índice frena igual una carrera).
        // El mensaje no repite el DNI.
        if (repository.existsByLegajo(entity.getLegajo())) {
            throw new ReglaNegocioException("INSPECTOR_DUPLICADO", "Ya existe un inspector con ese legajo");
        }
        if (repository.existsByDni(entity.getDni())) {
            throw new ReglaNegocioException("INSPECTOR_DUPLICADO", "Ya existe un inspector con ese DNI");
        }

        Usuario cuenta = new Usuario(dto.getEmail(), passwordEncoder.encode(dto.getPassword()),
                dto.getNombre(), dto.getApellido(), dto.getDni(), RolUsuario.INSPECTOR);
        Usuario cuentaGuardada = usuarioRepository.save(cuenta);

        entity.setUsuario(cuentaGuardada);
        entity.setUsuarioAlta(usuarioAlta);
        InspectorAnmat guardado = repository.save(entity);
        registradorEventos.registrar(TipoEvento.ALTA_INSPECTOR, "InspectorAnmat", guardado.getId(),
                DatosEventos.altaInspector(guardado), actual);
        return guardado;
    }

    /**
     * Da de baja al inspector (ACTIVO → BAJA): su cuenta queda inactiva y
     * las solicitudes PENDIENTE que tenía vuelven a la bandeja sin revisor.
     * BAJA_INSPECTOR lleva cuántas y cuáles empresas se liberaron.
     */
    @Transactional
    public InspectorAnmat darDeBaja(UUID id) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        InspectorAnmat inspector = getById(id);
        inspector.darDeBaja();
        inspector.getUsuario().setActivo(false);
        usuarioRepository.save(inspector.getUsuario());
        List<UUID> liberadas = new ArrayList<>();
        for (Empresa empresa : empresaRepository.findByEstadoAndInspectorRevisorId(EstadoHabilitacion.PENDIENTE, id)) {
            empresa.liberarRevisor();
            empresaRepository.save(empresa);
            liberadas.add(empresa.getId());
        }
        InspectorAnmat guardado = repository.save(inspector);
        registradorEventos.registrar(TipoEvento.BAJA_INSPECTOR, "InspectorAnmat", id,
                DatosEventos.bajaInspector(guardado, liberadas), actual);
        return guardado;
    }

    /** Reactiva al inspector (BAJA → ACTIVO) y su cuenta; no recupera las solicitudes liberadas. */
    @Transactional
    public InspectorAnmat reactivar(UUID id) {
        UsuarioAutenticado actual = usuarioActual.obtener();
        InspectorAnmat inspector = getById(id);
        inspector.reactivar();
        inspector.getUsuario().setActivo(true);
        usuarioRepository.save(inspector.getUsuario());
        InspectorAnmat guardado = repository.save(inspector);
        registradorEventos.registrar(TipoEvento.REACTIVACION_INSPECTOR, "InspectorAnmat", id,
                DatosEventos.reactivacionInspector(guardado), actual);
        return guardado;
    }
}
