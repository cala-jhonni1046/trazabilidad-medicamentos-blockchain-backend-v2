package com.medichain.modules.usuario;

import com.medichain.exceptions.ReglaNegocioException;
import com.medichain.exceptions.ResourceNotFoundException;
import com.medichain.modules.auth.UsuarioAutenticado;
import com.medichain.modules.empresa.Empresa;
import com.medichain.modules.empresa.TipoEmpresa;
import com.medichain.testutil.DatosDePrueba;
import com.medichain.utils.seguridad.UsuarioActual;
import com.medichain.utils.seguridad.VerificadorEmpresa;
import com.medichain.utils.seguridad.VerificadorUsuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test unitario UsuarioServiceTest en MediChain.
 * Prueba con Mockito el alta de usuarios con las reglas C1/D6/R2, el
 * hasheo de la contraseña y la pertenencia en getById.
 */
@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository repository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UsuarioActual usuarioActual;

    @Mock
    private VerificadorEmpresa verificadorEmpresa;

    @Mock
    private VerificadorUsuario verificadorUsuario;

    private UsuarioService service;
    private Empresa laboratorio;
    private UsuarioAutenticado adminLaboratorio;

    /** Arrange común: Service con mocks y un administrador de laboratorio habilitado. */
    @BeforeEach
    void setUp() {
        service = new UsuarioService(repository, passwordEncoder, usuarioActual, verificadorEmpresa, verificadorUsuario);
        laboratorio = DatosDePrueba.empresaHabilitada(TipoEmpresa.LABORATORIO);
        adminLaboratorio = DatosDePrueba.autenticado(RolUsuario.LABORATORIO, laboratorio);
    }

    /** Arma la entidad y el DTO de un usuario nuevo con el rol dado. */
    private Usuario nuevo(RolUsuario rol) {
        return new Usuario("nuevo@demo.com", null, "Ana", "Perez", "12345678", rol);
    }

    private UsuarioRequestDTO dto(RolUsuario rol) {
        return new UsuarioRequestDTO("nuevo@demo.com", "claveSecreta123", "Ana", "Perez", "12345678", rol);
    }

    @Test
    @DisplayName("El admin crea un empleado de su empresa: empresa del token y hash del encoder, nunca texto plano")
    void adminCreaEmpleadoDeSuEmpresa() {
        when(usuarioActual.obtener()).thenReturn(adminLaboratorio);
        when(verificadorEmpresa.exigirHabilitada(laboratorio.getId())).thenReturn(laboratorio);
        when(passwordEncoder.encode("claveSecreta123")).thenReturn("$2a$10$hashDeEjemplo");
        when(repository.save(any(Usuario.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        service.create(nuevo(RolUsuario.LABORATORIO), dto(RolUsuario.LABORATORIO));

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(repository).save(captor.capture());
        assertSame(laboratorio, captor.getValue().getEmpresa());
        assertEquals("$2a$10$hashDeEjemplo", captor.getValue().getPasswordHash());
        verify(verificadorUsuario).exigirAdminEmpresa(adminLaboratorio.getUsuarioId());
        verify(passwordEncoder, times(1)).encode("claveSecreta123");
    }

    @Test
    @DisplayName("C1 / R1: el admin de empresa no puede crear un INSPECTOR")
    void adminNoPuedeCrearInspector() {
        when(usuarioActual.obtener()).thenReturn(adminLaboratorio);

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> service.create(nuevo(RolUsuario.INSPECTOR), dto(RolUsuario.INSPECTOR)));
        assertEquals("R1", ex.getCodigoRegla());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("C1: el admin de un laboratorio no puede crear un empleado con rol FARMACIA ni PACIENTE")
    void adminNoPuedeCrearOtroRol() {
        when(usuarioActual.obtener()).thenReturn(adminLaboratorio);
        when(verificadorEmpresa.exigirHabilitada(laboratorio.getId())).thenReturn(laboratorio);

        assertEquals("ROL_NO_PERMITIDO", assertThrows(ReglaNegocioException.class,
                () -> service.create(nuevo(RolUsuario.FARMACIA), dto(RolUsuario.FARMACIA))).getCodigoRegla());
        assertEquals("ROL_NO_PERMITIDO", assertThrows(ReglaNegocioException.class,
                () -> service.create(nuevo(RolUsuario.PACIENTE), dto(RolUsuario.PACIENTE))).getCodigoRegla());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Un empleado que no es admin no puede crear usuarios (403)")
    void empleadoSinMarcaAdminNoCrea() {
        when(usuarioActual.obtener()).thenReturn(adminLaboratorio);
        when(verificadorUsuario.exigirAdminEmpresa(adminLaboratorio.getUsuarioId()))
                .thenThrow(new AccessDeniedException("Solo el administrador"));

        assertThrows(AccessDeniedException.class,
                () -> service.create(nuevo(RolUsuario.LABORATORIO), dto(RolUsuario.LABORATORIO)));
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("R2: el admin de una empresa suspendida no puede crear usuarios")
    void empresaSuspendidaNoCrea() {
        when(usuarioActual.obtener()).thenReturn(adminLaboratorio);
        when(verificadorEmpresa.exigirHabilitada(laboratorio.getId()))
                .thenThrow(new ReglaNegocioException("R2", "La empresa no está habilitada para operar"));

        assertEquals("R2", assertThrows(ReglaNegocioException.class,
                () -> service.create(nuevo(RolUsuario.LABORATORIO), dto(RolUsuario.LABORATORIO))).getCodigoRegla());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("C1: la Sede crea usuarios SEDE_CENTRAL pero no de empresa")
    void sedeSoloCreaSede() {
        UsuarioAutenticado sede = DatosDePrueba.autenticado(RolUsuario.SEDE_CENTRAL, null);
        when(usuarioActual.obtener()).thenReturn(sede);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hash");
        when(repository.save(any(Usuario.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        Usuario creado = service.create(nuevo(RolUsuario.SEDE_CENTRAL), dto(RolUsuario.SEDE_CENTRAL));
        assertEquals(RolUsuario.SEDE_CENTRAL, creado.getRol());

        assertEquals("ROL_NO_PERMITIDO", assertThrows(ReglaNegocioException.class,
                () -> service.create(nuevo(RolUsuario.LABORATORIO), dto(RolUsuario.LABORATORIO))).getCodigoRegla());
    }

    @Test
    @DisplayName("Ve lo suyo: un usuario puede consultarse a sí mismo")
    void veSuPropioUsuario() {
        when(usuarioActual.obtener()).thenReturn(adminLaboratorio);
        Usuario yo = nuevo(RolUsuario.LABORATORIO);
        yo.setId(adminLaboratorio.getUsuarioId());
        when(repository.findById(yo.getId())).thenReturn(Optional.of(yo));

        assertSame(yo, service.getById(yo.getId()));
    }

    @Test
    @DisplayName("No ve lo ajeno: un usuario de otra empresa responde 404")
    void noVeUsuarioDeOtraEmpresa() {
        when(usuarioActual.obtener()).thenReturn(adminLaboratorio);
        Usuario ajeno = nuevo(RolUsuario.FARMACIA);
        ajeno.setId(UUID.randomUUID());
        ajeno.setEmpresa(DatosDePrueba.empresaHabilitada(TipoEmpresa.FARMACIA));
        when(repository.findById(ajeno.getId())).thenReturn(Optional.of(ajeno));

        assertThrows(ResourceNotFoundException.class, () -> service.getById(ajeno.getId()));
    }

    /** DTO de registro público de paciente. */
    private RegistroPacienteRequestDTO registroPaciente() {
        RegistroPacienteRequestDTO dto = new RegistroPacienteRequestDTO();
        dto.setEmail("p@demo.com");
        dto.setPassword("clave-segura");
        dto.setNombre("Ana");
        dto.setApellido("Perez");
        dto.setDni("12345678");
        return dto;
    }

    @Test
    @DisplayName("Registro público de paciente: rol PACIENTE, sin empresa y contraseña hasheada")
    void registraPaciente() {
        when(passwordEncoder.encode("clave-segura")).thenReturn("hash-bcrypt");
        when(repository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        Usuario paciente = service.registrarPaciente(registroPaciente());

        assertEquals(RolUsuario.PACIENTE, paciente.getRol());
        assertEquals("hash-bcrypt", paciente.getPasswordHash());
        assertEquals(null, paciente.getEmpresa());
    }

    @Test
    @DisplayName("Registro de paciente con email existente → 409 REGISTRO_NO_COMPLETADO genérico")
    void registroPacienteEmailDuplicado() {
        when(repository.existsByEmail("p@demo.com")).thenReturn(true);

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> service.registrarPaciente(registroPaciente()));
        assertEquals("REGISTRO_NO_COMPLETADO", ex.getCodigoRegla());
        verify(repository, never()).save(any(Usuario.class));
    }
}
