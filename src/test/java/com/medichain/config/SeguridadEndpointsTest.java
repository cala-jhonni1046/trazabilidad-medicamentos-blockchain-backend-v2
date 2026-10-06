package com.medichain.config;

import com.medichain.exceptions.ReglaNegocioException;
import com.medichain.modules.registroblockchain.ErrorBlockchainException;
import com.medichain.modules.registroblockchain.EstadoAnclajeResponseDTO;
import com.medichain.modules.registroblockchain.RegistroBlockchainController;
import com.medichain.modules.registroblockchain.RegistroBlockchainMapper;
import com.medichain.modules.registroblockchain.RegistroBlockchainResponseDTO;
import com.medichain.modules.registroblockchain.RegistroBlockchainService;
import com.medichain.modules.auth.JwtService;
import com.medichain.modules.auth.UsuarioAutenticado;
import com.medichain.modules.dispensacion.DispensacionController;
import com.medichain.modules.dispensacion.DispensacionMapper;
import com.medichain.modules.dispensacion.DispensacionService;
import com.medichain.modules.empresa.EmpresaController;
import com.medichain.modules.empresa.EmpresaMapper;
import com.medichain.modules.empresa.Empresa;
import com.medichain.modules.empresa.RegistroEmpresaRequestDTO;
import com.medichain.modules.empresa.TipoEmpresa;
import com.medichain.modules.auth.RegistroController;
import com.medichain.modules.inspectoranmat.InspectorAnmatController;
import com.medichain.modules.enlacecuit.EnlaceCuitController;
import com.medichain.modules.lote.LoteController;
import com.medichain.modules.bulto.BultoController;
import com.medichain.modules.recepcion.RecepcionController;
import com.medichain.modules.cuarentena.CuarentenaController;
import com.medichain.modules.cuarentena.CuarentenaMapper;
import com.medichain.modules.cuarentena.CuarentenaService;
import com.medichain.modules.reporteciudadano.ReporteCiudadanoController;
import com.medichain.modules.reporteciudadano.ReporteCiudadanoMapper;
import com.medichain.modules.reporteciudadano.ReporteCiudadanoService;
import com.medichain.utils.seguridad.UsuarioActual;
import com.medichain.modules.recepcion.RecepcionMapper;
import com.medichain.modules.recepcion.RecepcionService;
import com.medichain.modules.unidadtrazable.EstadoVerificacion;
import com.medichain.modules.unidadtrazable.VerificacionPublicaController;
import com.medichain.modules.unidadtrazable.VerificacionPublicaResponseDTO;
import com.medichain.modules.unidadtrazable.VerificacionPublicaService;
import com.medichain.modules.bulto.BultoMapper;
import com.medichain.modules.bulto.BultoService;
import com.medichain.modules.despachologistico.DespachoLogisticoController;
import com.medichain.modules.despachologistico.DespachoLogisticoMapper;
import com.medichain.modules.despachologistico.DespachoLogisticoService;
import com.medichain.modules.lote.LoteMapper;
import com.medichain.modules.lote.LoteService;
import com.medichain.modules.unidadtrazable.UnidadTrazableController;
import com.medichain.modules.unidadtrazable.UnidadTrazableMapper;
import com.medichain.modules.unidadtrazable.UnidadTrazableService;
import com.medichain.modules.enlacecuit.EnlaceCuitMapper;
import com.medichain.modules.enlacecuit.EnlaceCuitService;
import com.medichain.modules.inspectoranmat.InspectorAnmatMapper;
import com.medichain.modules.inspectoranmat.InspectorAnmatService;
import com.medichain.modules.usuario.RegistroPacienteRequestDTO;
import com.medichain.modules.usuario.Usuario;
import com.medichain.modules.usuario.UsuarioService;
import com.medichain.utils.enums.Provincia;
import org.springframework.mock.web.MockMultipartFile;
import com.medichain.modules.empresa.EmpresaService;
import com.medichain.modules.usuario.RolUsuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test de capa web SeguridadEndpointsTest en MediChain.
 * Levanta solo la capa MVC (sin base de datos) con la configuración de
 * seguridad real y verifica 401 sin token, 403 para un rol no permitido
 * (@PreAuthorize) y 200 para un rol permitido. Los Services se simulan.
 */
@WebMvcTest(controllers = {EmpresaController.class, DispensacionController.class, RegistroController.class,
        InspectorAnmatController.class, EnlaceCuitController.class, LoteController.class,
        UnidadTrazableController.class, BultoController.class, DespachoLogisticoController.class,
        RecepcionController.class, VerificacionPublicaController.class, CuarentenaController.class,
        ReporteCiudadanoController.class, RegistroBlockchainController.class})
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class SeguridadEndpointsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private EmpresaService empresaService;

    @MockitoBean
    private EmpresaMapper empresaMapper;

    @MockitoBean
    private DispensacionService dispensacionService;

    @MockitoBean
    private DispensacionMapper dispensacionMapper;

    @MockitoBean
    private UsuarioService usuarioService;

    @MockitoBean
    private InspectorAnmatService inspectorAnmatService;

    @MockitoBean
    private InspectorAnmatMapper inspectorAnmatMapper;

    @MockitoBean
    private EnlaceCuitService enlaceCuitService;

    @MockitoBean
    private EnlaceCuitMapper enlaceCuitMapper;

    @MockitoBean
    private LoteService loteService;

    @MockitoBean
    private LoteMapper loteMapper;

    @MockitoBean
    private UnidadTrazableService unidadTrazableService;

    @MockitoBean
    private UnidadTrazableMapper unidadTrazableMapper;

    @MockitoBean
    private BultoService bultoService;

    @MockitoBean
    private BultoMapper bultoMapper;

    @MockitoBean
    private DespachoLogisticoService despachoLogisticoService;

    @MockitoBean
    private DespachoLogisticoMapper despachoLogisticoMapper;

    @MockitoBean
    private RecepcionService recepcionService;

    @MockitoBean
    private RecepcionMapper recepcionMapper;

    @MockitoBean
    private VerificacionPublicaService verificacionPublicaService;

    @MockitoBean
    private CuarentenaService cuarentenaService;

    @MockitoBean
    private CuarentenaMapper cuarentenaMapper;

    @MockitoBean
    private ReporteCiudadanoService reporteCiudadanoService;

    @MockitoBean
    private ReporteCiudadanoMapper reporteCiudadanoMapper;

    @MockitoBean
    private UsuarioActual usuarioActual;

    @MockitoBean
    private RegistroBlockchainService registroBlockchainService;

    @MockitoBean
    private RegistroBlockchainMapper registroBlockchainMapper;

    /** Autenticación como la que deja JwtAuthenticationFilter para el rol dado. */
    private Authentication como(RolUsuario rol) {
        UsuarioAutenticado usuario = new UsuarioAutenticado(UUID.randomUUID(), "u@demo.com", rol, UUID.randomUUID(), null);
        return new UsernamePasswordAuthenticationToken(usuario, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + rol.name())));
    }

    @Test
    @DisplayName("GET /api/empresas sin token → 401 con ErrorResponseDTO")
    void sinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/empresas"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("GET /api/empresas como LABORATORIO → 200 (rol permitido)")
    void laboratorioListaEmpresas() throws Exception {
        when(empresaService.getAll(any(Pageable.class))).thenReturn(Page.empty());
        mockMvc.perform(get("/api/empresas").with(authentication(como(RolUsuario.LABORATORIO))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("D4: GET /api/dispensaciones como PACIENTE → 403")
    void pacienteNoListaDispensaciones() throws Exception {
        mockMvc.perform(get("/api/dispensaciones").with(authentication(como(RolUsuario.PACIENTE))))
                .andExpect(status().isForbidden());
    }

    // ---------- Acciones: solo el rol correcto ----------

    @Test
    @DisplayName("POST /api/empresas ya no existe (el alta es POST /api/registro/empresas) → 405, no 500")
    void altaPorSedeYaNoExiste() throws Exception {
        mockMvc.perform(post("/api/empresas").with(authentication(como(RolUsuario.SEDE_CENTRAL)))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("Tomar/habilitar como LABORATORIO o SEDE → 403 (solo INSPECTOR)")
    void habilitarSoloInspector() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/api/empresas/" + id + "/tomar").with(authentication(como(RolUsuario.LABORATORIO))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/empresas/" + id + "/habilitar").with(authentication(como(RolUsuario.SEDE_CENTRAL))))
                .andExpect(status().isForbidden());
        verify(empresaService, never()).habilitar(any());
    }

    @Test
    @DisplayName("Suspender / asignar como INSPECTOR → 403 (solo SEDE_CENTRAL)")
    void suspenderSoloSede() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/api/empresas/" + id + "/suspender").with(authentication(como(RolUsuario.INSPECTOR)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"x\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/empresas/" + id + "/asignar").with(authentication(como(RolUsuario.INSPECTOR)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"inspectorId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Rechazar sin motivo → 400 (motivo obligatorio)")
    void rechazarSinMotivoDevuelve400() throws Exception {
        mockMvc.perform(post("/api/empresas/" + UUID.randomUUID() + "/rechazar")
                        .with(authentication(como(RolUsuario.INSPECTOR)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"  \"}"))
                .andExpect(status().isBadRequest());
        verify(empresaService, never()).rechazar(any(), any());
    }

    @Test
    @DisplayName("Baja de inspector como INSPECTOR → 403 (R1: solo la Sede)")
    void bajaInspectorSoloSede() throws Exception {
        mockMvc.perform(post("/api/inspectores-anmat/" + UUID.randomUUID() + "/baja")
                        .with(authentication(como(RolUsuario.INSPECTOR))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Acciones sin token → 401")
    void accionesSinTokenDevuelven401() throws Exception {
        mockMvc.perform(post("/api/empresas/" + UUID.randomUUID() + "/tomar"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- Registro público ----------

    /** Formulario multipart válido de una farmacia con su PDF. */
    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder formularioEmpresa() {
        MockMultipartFile pdf = new MockMultipartFile("documento", "hab.pdf", "application/pdf",
                "%PDF-1.4 prueba".getBytes());
        return (org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder) multipart("/api/registro/empresas")
                .file(pdf)
                .param("tipo", "FARMACIA").param("cuit", "30-71000003-0").param("razonSocial", "Farmacia Nueva")
                .param("gln", "7799000000037").param("provincia", "MENDOZA").param("localidad", "Mendoza")
                .param("domicilio", "Calle 1").param("adminEmail", "admin@nueva.demo")
                .param("adminPassword", "clave-segura").param("adminNombre", "Ana")
                .param("adminApellido", "Perez").param("adminDni", "12345678");
    }

    @Test
    @DisplayName("Registro de empresa SIN token → 201 con CUIT y estado, sin ids internos")
    void registroEmpresaPublico() throws Exception {
        Empresa empresa = new Empresa(TipoEmpresa.FARMACIA, "30-71000003-0", "Farmacia Nueva", Provincia.MENDOZA,
                "Mendoza", "Calle 1");
        empresa.setId(UUID.randomUUID());
        when(empresaService.registrar(any(RegistroEmpresaRequestDTO.class), any(byte[].class), anyString()))
                .thenReturn(empresa);

        mockMvc.perform(formularioEmpresa())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.identificador").value("30-71000003-0"))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.id").doesNotExist());
    }

    @Test
    @DisplayName("Registro de empresa sin PDF → 400")
    void registroEmpresaSinDocumento() throws Exception {
        mockMvc.perform(multipart("/api/registro/empresas")
                        .param("tipo", "FARMACIA").param("cuit", "30-71000003-0").param("razonSocial", "F")
                        .param("gln", "7799000000037").param("provincia", "MENDOZA").param("localidad", "M")
                        .param("domicilio", "C").param("adminEmail", "a@b.com").param("adminPassword", "clave-segura")
                        .param("adminNombre", "A").param("adminApellido", "B").param("adminDni", "12345678"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Registro: adminEsDirectorTecnico en una FARMACIA → 400 (solo LABORATORIO)")
    void registroDirectorTecnicoSoloLaboratorio() throws Exception {
        mockMvc.perform(formularioEmpresa().param("adminEsDirectorTecnico", "true"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("adminEsDirectorTecnico"));
        verify(empresaService, never()).registrar(any(), any(), any());
    }

    @Test
    @DisplayName("Registro de paciente SIN token → 201 con email y estado, sin ids internos")
    void registroPacientePublico() throws Exception {
        Usuario paciente = new Usuario("p@demo.com", "hash", "Ana", "Perez", "12345678", RolUsuario.PACIENTE);
        paciente.setId(UUID.randomUUID());
        when(usuarioService.registrarPaciente(any(RegistroPacienteRequestDTO.class))).thenReturn(paciente);

        mockMvc.perform(post("/api/registro/pacientes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"p@demo.com\",\"password\":\"clave-segura\",\"nombre\":\"Ana\","
                                + "\"apellido\":\"Perez\",\"dni\":\"12345678\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.identificador").value("p@demo.com"))
                .andExpect(jsonPath("$.id").doesNotExist());
    }

    // ---------- Circuitos (/api/circuitos) ----------

    @Test
    @DisplayName("Proponer circuito como FARMACIA → 403 (solo LABORATORIO)")
    void proponerSoloLaboratorio() throws Exception {
        mockMvc.perform(post("/api/circuitos").with(authentication(como(RolUsuario.FARMACIA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cuitDistribuidor\":\"30-71000002-2\",\"cuitFarmacia\":\"30-71000003-0\"}"))
                .andExpect(status().isForbidden());
        verify(enlaceCuitService, never()).proponer(any(), any());
    }

    @Test
    @DisplayName("Proponer con CUIT inválido → 400 con el campo")
    void proponerCuitInvalido() throws Exception {
        mockMvc.perform(post("/api/circuitos").with(authentication(como(RolUsuario.LABORATORIO)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cuitDistribuidor\":\"30-71000002-9\",\"cuitFarmacia\":\"30-71000003-0\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("cuitDistribuidor"));
    }

    @Test
    @DisplayName("Aceptar como LABORATORIO o INSPECTOR → 403 (solo DISTRIBUIDOR / FARMACIA)")
    void aceptarSoloEmpresasInvitadas() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/api/circuitos/" + id + "/aceptar").with(authentication(como(RolUsuario.LABORATORIO))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/circuitos/" + id + "/aceptar").with(authentication(como(RolUsuario.INSPECTOR))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Tomar/aprobar circuito como SEDE o FARMACIA → 403 (solo INSPECTOR); asignar como INSPECTOR → 403")
    void accionesDeInspectorYSede() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/api/circuitos/" + id + "/tomar").with(authentication(como(RolUsuario.SEDE_CENTRAL))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/circuitos/" + id + "/aprobar").with(authentication(como(RolUsuario.FARMACIA))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/circuitos/" + id + "/asignar").with(authentication(como(RolUsuario.INSPECTOR)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"inspectorId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Suspender circuito como LABORATORIO → 403 (solo INSPECTOR o SEDE)")
    void suspenderCircuitoSoloInspectorOSede() throws Exception {
        mockMvc.perform(post("/api/circuitos/" + UUID.randomUUID() + "/suspender")
                        .with(authentication(como(RolUsuario.LABORATORIO)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Buscar empresa por CUIT como FARMACIA → 403 (solo LABORATORIO)")
    void buscarPorCuitSoloLaboratorio() throws Exception {
        mockMvc.perform(get("/api/empresas/por-cuit").param("cuit", "30-71000003-0").param("tipo", "FARMACIA")
                        .with(authentication(como(RolUsuario.FARMACIA))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("La ruta vieja /api/enlaces-cuit ya no existe")
    void rutaViejaNoExiste() throws Exception {
        mockMvc.perform(get("/api/enlaces-cuit").with(authentication(como(RolUsuario.LABORATORIO))))
                .andExpect(status().isNotFound());
    }

    // ---------- Lotes ----------

    @Test
    @DisplayName("Liberar un lote como FARMACIA o SEDE → 403 (solo LABORATORIO o INSPECTOR)")
    void liberarSoloLaboratorioOInspector() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/api/lotes/" + id + "/liberar").with(authentication(como(RolUsuario.FARMACIA))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/lotes/" + id + "/liberar").with(authentication(como(RolUsuario.SEDE_CENTRAL))))
                .andExpect(status().isForbidden());
        verify(loteService, never()).liberar(any());
    }

    @Test
    @DisplayName("Registrar lote con series y cantidad juntas → 400 en 'series'")
    void registrarLoteConAmbasFormas() throws Exception {
        mockMvc.perform(post("/api/lotes").with(authentication(como(RolUsuario.LABORATORIO)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"L2026-0003\",\"fechaFabricacion\":\"2026-01-01\","
                                + "\"fechaVencimiento\":\"2030-01-01\",\"medicamentoId\":\"" + UUID.randomUUID() + "\","
                                + "\"cantidad\":5,\"series\":[\"A1\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("series"));
        verify(loteService, never()).registrar(any());
    }

    @Test
    @DisplayName("Cajas de un lote como FARMACIA → 403; el alta suelta de cajas ya no existe → 405")
    void cajasDeUnLote() throws Exception {
        mockMvc.perform(get("/api/lotes/" + UUID.randomUUID() + "/unidades").with(authentication(como(RolUsuario.FARMACIA))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/unidades-trazables").with(authentication(como(RolUsuario.LABORATORIO)))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }

    // ---------- Bultos y viajes ----------

    @Test
    @DisplayName("Armar o desarmar bulto como DISTRIBUIDOR → 403 (solo LABORATORIO)")
    void bultoSoloLaboratorio() throws Exception {
        mockMvc.perform(post("/api/bultos/" + UUID.randomUUID() + "/desarmar").with(authentication(como(RolUsuario.DISTRIBUIDOR))))
                .andExpect(status().isForbidden());
        verify(bultoService, never()).desarmar(any());
    }

    @Test
    @DisplayName("Crear viaje o registrar salida como FARMACIA → 403 (solo LABORATORIO o DISTRIBUIDOR)")
    void viajeSoloEmpresasOrigen() throws Exception {
        mockMvc.perform(post("/api/viajes/" + UUID.randomUUID() + "/salida").with(authentication(como(RolUsuario.FARMACIA))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/viajes/" + UUID.randomUUID() + "/robo").with(authentication(como(RolUsuario.INSPECTOR)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Cancelar viaje sin motivo → 400; la ruta vieja /api/despachos-logisticos ya no existe")
    void cancelarSinMotivoYRutaVieja() throws Exception {
        mockMvc.perform(post("/api/viajes/" + UUID.randomUUID() + "/cancelar").with(authentication(como(RolUsuario.LABORATORIO)))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/despachos-logisticos").with(authentication(como(RolUsuario.LABORATORIO))))
                .andExpect(status().isNotFound());
    }

    // ---------- 7e: recepción, dispensación y verificación pública ----------

    @Test
    @DisplayName("Verificación pública SIN token → 200")
    void verificacionPublicaSinToken() throws Exception {
        VerificacionPublicaResponseDTO respuesta = new VerificacionPublicaResponseDTO();
        respuesta.setEstado(EstadoVerificacion.APTA);
        when(verificacionPublicaService.verificar(anyString(), anyString())).thenReturn(respuesta);

        mockMvc.perform(get("/api/verificacion").param("gtin", "07799000001010").param("serie", "L20260001S000011"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APTA"));
    }

    @Test
    @DisplayName("Verificación con GTIN inválido o serie mal formada → 400 (sin llegar al Service)")
    void verificacionConFormatoInvalido() throws Exception {
        mockMvc.perform(get("/api/verificacion").param("gtin", "07799000001011").param("serie", "S1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/verificacion").param("gtin", "07799000001010").param("serie", "CON-GUION"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/verificacion").param("gtin", "07799000001010"))
                .andExpect(status().isBadRequest());
        verify(verificacionPublicaService, never()).verificar(any(), any());
    }

    @Test
    @DisplayName("Recibir como LABORATORIO o dispensar como DISTRIBUIDOR → 403")
    void recibirYDispensarPorRol() throws Exception {
        mockMvc.perform(post("/api/recepciones").with(authentication(como(RolUsuario.LABORATORIO)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoBulto\":\"BUL-0002\",\"precintoIntacto\":true,\"cantidadVerificada\":10,\"temperatura\":20}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/dispensaciones").with(authentication(como(RolUsuario.DISTRIBUIDOR)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gtin\":\"07799000001010\",\"serie\":\"S1\",\"particular\":true,\"numeroReceta\":\"R1\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Dispensar con obra social sin afiliado → 400 en el campo; DNI con puntos → 400")
    void dispensarValidaciones() throws Exception {
        mockMvc.perform(post("/api/dispensaciones").with(authentication(como(RolUsuario.FARMACIA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gtin\":\"07799000001010\",\"serie\":\"S1\",\"particular\":false,"
                                + "\"obraSocial\":\"OSEP\",\"numeroReceta\":\"R1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("numeroAfiliado"));
        mockMvc.perform(post("/api/dispensaciones").with(authentication(como(RolUsuario.FARMACIA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gtin\":\"07799000001010\",\"serie\":\"S1\",\"particular\":true,"
                                + "\"numeroReceta\":\"R1\",\"dni\":\"30.111.006\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("dni"));
        verify(dispensacionService, never()).dispensar(any());
    }

    // ---------- 7f: dictamen y reportes ----------

    @Test
    @DisplayName("Levantar o convertir en recall como LABORATORIO o SEDE → 403 (R12: solo un inspector)")
    void dictamenSoloInspector() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/api/cuarentenas/" + id + "/levantar").with(authentication(como(RolUsuario.LABORATORIO)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fundamento\":\"x\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/cuarentenas/" + id + "/recall").with(authentication(como(RolUsuario.SEDE_CENTRAL)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fundamento\":\"x\"}"))
                .andExpect(status().isForbidden());
        verify(cuarentenaService, never()).levantar(any(), any());
    }

    @Test
    @DisplayName("Abrir una cuarentena manual con un motivo de sistema (RUPTURA_FRIO) → 400 en el campo motivo")
    void cuarentenaManualConMotivoDeSistema() throws Exception {
        mockMvc.perform(post("/api/cuarentenas").with(authentication(como(RolUsuario.INSPECTOR)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loteId\":\"" + UUID.randomUUID() + "\",\"motivo\":\"RUPTURA_FRIO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("motivo"));
        verify(cuarentenaService, never()).abrir(any());
    }

    @Test
    @DisplayName("Reportar como FARMACIA → 403 (solo PACIENTE); cerrar un reporte como PACIENTE → 403")
    void reportesPorRol() throws Exception {
        mockMvc.perform(post("/api/reportes-ciudadanos").with(authentication(como(RolUsuario.FARMACIA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gtin\":\"07799000001010\",\"serie\":\"S1\",\"motivo\":\"OTRO\",\"provincia\":\"MENDOZA\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/reportes-ciudadanos/" + UUID.randomUUID() + "/cerrar").with(authentication(como(RolUsuario.PACIENTE)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"conclusion\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    // ---------- Anclaje en blockchain (paso 8) ----------

    @Test
    @DisplayName("POST /api/registros-blockchain/anclar: solo SEDE (202); INSPECTOR y LABORATORIO → 403; sin token → 401")
    void anclarSoloSede() throws Exception {
        mockMvc.perform(post("/api/registros-blockchain/anclar"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/registros-blockchain/anclar").with(authentication(como(RolUsuario.INSPECTOR))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/registros-blockchain/anclar").with(authentication(como(RolUsuario.LABORATORIO))))
                .andExpect(status().isForbidden());
        verify(registroBlockchainService, never()).anclarAhora();

        RegistroBlockchainResponseDTO dto = new RegistroBlockchainResponseDTO();
        dto.setTransactionHash("0x" + "ab".repeat(32));
        when(registroBlockchainMapper.toResponseDTO(any())).thenReturn(dto);
        mockMvc.perform(post("/api/registros-blockchain/anclar").with(authentication(como(RolUsuario.SEDE_CENTRAL))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.transactionHash").value(dto.getTransactionHash()));
    }

    @Test
    @DisplayName("GET /api/registros-blockchain/estado: SEDE e INSPECTOR → 200; FARMACIA y PACIENTE → 403")
    void estadoDelAnclajeSedeEInspector() throws Exception {
        when(registroBlockchainService.estado()).thenReturn(new EstadoAnclajeResponseDTO());
        mockMvc.perform(get("/api/registros-blockchain/estado").with(authentication(como(RolUsuario.SEDE_CENTRAL))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/registros-blockchain/estado").with(authentication(como(RolUsuario.INSPECTOR))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/registros-blockchain/estado").with(authentication(como(RolUsuario.FARMACIA))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/registros-blockchain/estado").with(authentication(como(RolUsuario.PACIENTE))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Anclar con el anclaje deshabilitado → 409 con regla ANCLAJE_DESHABILITADO")
    void anclarDeshabilitado() throws Exception {
        when(registroBlockchainService.anclarAhora()).thenThrow(new ReglaNegocioException("ANCLAJE_DESHABILITADO", "x"));
        mockMvc.perform(post("/api/registros-blockchain/anclar").with(authentication(como(RolUsuario.SEDE_CENTRAL))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.regla").value("ANCLAJE_DESHABILITADO"));
    }

    @Test
    @DisplayName("Sepolia no responde → 503 con mensaje fijo, sin el detalle del error")
    void sepoliaNoResponde() throws Exception {
        when(registroBlockchainService.anclarAhora()).thenThrow(
                new ErrorBlockchainException("No se pudo consultar el nonce: timeout en eth-sepolia.g.alchemy.com"));
        String cuerpo = mockMvc.perform(post("/api/registros-blockchain/anclar")
                        .with(authentication(como(RolUsuario.SEDE_CENTRAL))))
                .andExpect(status().isServiceUnavailable())
                .andReturn().getResponse().getContentAsString();
        assertFalse(cuerpo.contains("alchemy"), "el detalle va solo al log");
    }
}
