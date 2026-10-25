package com.medichain.modules.medicamento;

import com.medichain.exceptions.ReglaNegocioException;
import com.medichain.modules.auth.UsuarioAutenticado;
import com.medichain.modules.empresa.Empresa;
import com.medichain.modules.empresa.TipoEmpresa;
import com.medichain.modules.trazabilidad.RegistradorEventos;
import com.medichain.modules.trazabilidad.TipoEvento;
import com.medichain.modules.usuario.RolUsuario;
import com.medichain.testutil.DatosDePrueba;
import com.medichain.exceptions.ResourceNotFoundException;
import com.medichain.utils.seguridad.UsuarioActual;
import com.medichain.utils.seguridad.VerificadorEmpresa;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test unitario MedicamentoServiceTest en MediChain.
 * Alta de un medicamento: el GTIN se normaliza, queda a nombre del
 * laboratorio del usuario y deja su evento; un GTIN ya registrado responde
 * 409 MEDICAMENTO_DUPLICADO sin guardar nada.
 */
@ExtendWith(MockitoExtension.class)
class MedicamentoServiceTest {

    private static final String GTIN = "07799000001010";

    @Mock
    private MedicamentoRepository repository;

    @Mock
    private UsuarioActual usuarioActual;

    @Mock
    private VerificadorEmpresa verificadorEmpresa;

    @Mock
    private RegistradorEventos registradorEventos;

    private final Empresa laboratorio = DatosDePrueba.empresaHabilitada(TipoEmpresa.LABORATORIO);

    /** Service autenticado como el laboratorio habilitado. */
    private MedicamentoService service() {
        UsuarioAutenticado actual = DatosDePrueba.autenticado(RolUsuario.LABORATORIO, laboratorio);
        when(usuarioActual.obtener()).thenReturn(actual);
        when(verificadorEmpresa.exigirHabilitada(laboratorio.getId())).thenReturn(laboratorio);
        return new MedicamentoService(repository, usuarioActual, verificadorEmpresa, registradorEventos);
    }

    private static Medicamento nuevo() {
        return new Medicamento(GTIN, "Cuyafen", "Ibuprofeno", "400 mg", "Comprimido", "Caja x 20");
    }

    @Test
    @DisplayName("Alta: queda a nombre del laboratorio del usuario y deja MEDICAMENTO_REGISTRADO")
    void altaCasoFeliz() {
        MedicamentoService servicio = service();
        when(repository.save(any(Medicamento.class))).thenAnswer(inv -> inv.getArgument(0));

        Medicamento guardado = servicio.create(nuevo(), new MedicamentoRequestDTO());

        assertSame(laboratorio, guardado.getLaboratorio());
        verify(registradorEventos).registrar(eq(TipoEvento.MEDICAMENTO_REGISTRADO), eq("Medicamento"), any(),
                anyMap(), any(UsuarioAutenticado.class));
    }

    @Test
    @DisplayName("GTIN ya registrado → 409 MEDICAMENTO_DUPLICADO, sin guardar ni registrar evento")
    void gtinRepetido() {
        MedicamentoService servicio = service();
        when(repository.existsByGtin(GTIN)).thenReturn(true);

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> servicio.create(nuevo(), new MedicamentoRequestDTO()));

        assertEquals("MEDICAMENTO_DUPLICADO", ex.getCodigoRegla());
        verify(repository, never()).save(any());
        verify(registradorEventos, never()).registrar(any(), any(), any(), anyMap(), any(UsuarioAutenticado.class));
    }

    // ---------- Alcance de lectura por rol ----------

    /** Service autenticado como el rol y la empresa dados (sin los stubs del alta). */
    private MedicamentoService serviceComo(RolUsuario rol, Empresa empresa) {
        when(usuarioActual.obtener()).thenReturn(DatosDePrueba.autenticado(rol, empresa));
        return new MedicamentoService(repository, usuarioActual, verificadorEmpresa, registradorEventos);
    }

    private static Medicamento conId(Empresa laboratorio) {
        Medicamento m = nuevo();
        m.setId(UUID.randomUUID());
        m.setLaboratorio(laboratorio);
        return m;
    }

    @Test
    @DisplayName("getAll: SEDE ve todo (findAll); LAB solo los suyos; DIST los de sus circuitos APROBADOS")
    void getAllPorRol() {
        Pageable pageable = Pageable.ofSize(20);
        Empresa distribuidora = DatosDePrueba.empresaHabilitada(TipoEmpresa.DISTRIBUIDOR);
        Page<Medicamento> vacia = new PageImpl<>(List.of());
        lenient().when(repository.findAll(pageable)).thenReturn(vacia);
        lenient().when(repository.findByLaboratorioId(laboratorio.getId(), pageable)).thenReturn(vacia);
        lenient().when(repository.findVisiblesParaEmpresa(distribuidora.getId(), pageable)).thenReturn(vacia);

        serviceComo(RolUsuario.SEDE_CENTRAL, null).getAll(pageable);
        verify(repository).findAll(pageable);

        serviceComo(RolUsuario.LABORATORIO, laboratorio).getAll(pageable);
        verify(repository).findByLaboratorioId(laboratorio.getId(), pageable);

        serviceComo(RolUsuario.DISTRIBUIDOR, distribuidora).getAll(pageable);
        verify(repository).findVisiblesParaEmpresa(distribuidora.getId(), pageable);
    }

    @Test
    @DisplayName("getById: el laboratorio ve el suyo pero uno ajeno → 404")
    void getByIdLaboratorio() {
        Empresa otro = DatosDePrueba.empresaHabilitada(TipoEmpresa.LABORATORIO);
        Medicamento propio = conId(laboratorio);
        Medicamento ajeno = conId(otro);
        when(repository.findById(propio.getId())).thenReturn(Optional.of(propio));
        when(repository.findById(ajeno.getId())).thenReturn(Optional.of(ajeno));

        assertSame(propio, serviceComo(RolUsuario.LABORATORIO, laboratorio).getById(propio.getId()));
        assertThrows(ResourceNotFoundException.class,
                () -> serviceComo(RolUsuario.LABORATORIO, laboratorio).getById(ajeno.getId()));
    }

    @Test
    @DisplayName("getById: la farmacia ve el de un laboratorio con circuito APROBADO; sin circuito → 404")
    void getByIdFarmacia() {
        Empresa farmacia = DatosDePrueba.empresaHabilitada(TipoEmpresa.FARMACIA);
        Empresa laboratorioConCircuito = DatosDePrueba.empresaHabilitada(TipoEmpresa.LABORATORIO);
        Empresa laboratorioSinCircuito = DatosDePrueba.empresaHabilitada(TipoEmpresa.LABORATORIO);
        Medicamento visible = conId(laboratorioConCircuito);
        Medicamento ajeno = conId(laboratorioSinCircuito);
        when(repository.findById(visible.getId())).thenReturn(Optional.of(visible));
        when(repository.findById(ajeno.getId())).thenReturn(Optional.of(ajeno));
        when(repository.existsVisibleParaEmpresa(visible.getId(), farmacia.getId())).thenReturn(true);
        when(repository.existsVisibleParaEmpresa(ajeno.getId(), farmacia.getId())).thenReturn(false);

        assertSame(visible, serviceComo(RolUsuario.FARMACIA, farmacia).getById(visible.getId()));
        assertThrows(ResourceNotFoundException.class,
                () -> serviceComo(RolUsuario.FARMACIA, farmacia).getById(ajeno.getId()));
    }

    @Test
    @DisplayName("getById: un id inexistente → 404 (para cualquiera)")
    void getByIdInexistente() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> serviceComo(RolUsuario.SEDE_CENTRAL, null).getById(id));
    }
}
