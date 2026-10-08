package com.medichain.integracion;

import com.medichain.config.EjecutorComoUsuario;
import com.medichain.modules.bulto.Bulto;
import com.medichain.modules.bulto.BultoRequestDTO;
import com.medichain.modules.bulto.BultoService;
import com.medichain.modules.despachologistico.DespachoLogistico;
import com.medichain.modules.despachologistico.DespachoLogisticoRequestDTO;
import com.medichain.modules.despachologistico.DespachoLogisticoService;
import com.medichain.modules.empresa.Empresa;
import com.medichain.modules.empresa.EmpresaService;
import com.medichain.modules.empresa.RegistroEmpresaRequestDTO;
import com.medichain.modules.empresa.TipoEmpresa;
import com.medichain.modules.enlacecuit.EnlaceCuit;
import com.medichain.modules.enlacecuit.EnlaceCuitService;
import com.medichain.modules.inspectoranmat.InspectorAnmat;
import com.medichain.modules.inspectoranmat.InspectorAnmatMapper;
import com.medichain.modules.inspectoranmat.InspectorAnmatRequestDTO;
import com.medichain.modules.inspectoranmat.InspectorAnmatService;
import com.medichain.modules.lote.Lote;
import com.medichain.modules.lote.LoteRequestDTO;
import com.medichain.modules.lote.LoteService;
import com.medichain.modules.medicamento.Medicamento;
import com.medichain.modules.medicamento.MedicamentoMapper;
import com.medichain.modules.medicamento.MedicamentoRequestDTO;
import com.medichain.modules.medicamento.MedicamentoService;
import com.medichain.modules.recepcion.RecepcionRequestDTO;
import com.medichain.modules.recepcion.RecepcionService;
import com.medichain.modules.usuario.RolUsuario;
import com.medichain.modules.usuario.Usuario;
import com.medichain.modules.usuario.UsuarioRepository;
import com.medichain.utils.enums.Provincia;
import com.medichain.utils.validacion.CuitUtil;
import com.medichain.utils.validacion.Gs1Util;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixture EscenarioIntegracion de los tests de integración de MediChain.
 * Arma actores y datos con los SERVICES REALES, actuando como cada usuario
 * (EjecutorComoUsuario, igual que DatosDemo): se respetan las reglas de
 * negocio y cada paso deja su evento en la cadena, como en producción.
 * Todos los usuarios tienen la contraseña CLAVE (falsa, solo para tests).
 * Los CUIT, GLN y GTIN se generan válidos (dígito verificador) usando los
 * validadores del propio proyecto, y no se repiten dentro de la suite.
 */
@TestComponent
public class EscenarioIntegracion {

    /** Contraseña de todos los usuarios que crea el fixture (falsa, solo para tests). */
    public static final String CLAVE = "clave-falsa-de-integracion";

    private static final AtomicInteger CONTADOR = new AtomicInteger(100);

    private final EjecutorComoUsuario ejecutor;
    private final UsuarioRepository usuarioRepository;
    private final InspectorAnmatService inspectorAnmatService;
    private final InspectorAnmatMapper inspectorAnmatMapper;
    private final EmpresaService empresaService;
    private final MedicamentoService medicamentoService;
    private final MedicamentoMapper medicamentoMapper;
    private final LoteService loteService;
    private final EnlaceCuitService enlaceCuitService;
    private final BultoService bultoService;
    private final DespachoLogisticoService despachoService;
    private final RecepcionService recepcionService;

    @Autowired
    public EscenarioIntegracion(EjecutorComoUsuario ejecutor, UsuarioRepository usuarioRepository,
                                InspectorAnmatService inspectorAnmatService, InspectorAnmatMapper inspectorAnmatMapper,
                                EmpresaService empresaService, MedicamentoService medicamentoService,
                                MedicamentoMapper medicamentoMapper, LoteService loteService,
                                EnlaceCuitService enlaceCuitService, BultoService bultoService,
                                DespachoLogisticoService despachoService, RecepcionService recepcionService) {
        this.ejecutor = ejecutor;
        this.usuarioRepository = usuarioRepository;
        this.inspectorAnmatService = inspectorAnmatService;
        this.inspectorAnmatMapper = inspectorAnmatMapper;
        this.empresaService = empresaService;
        this.medicamentoService = medicamentoService;
        this.medicamentoMapper = medicamentoMapper;
        this.loteService = loteService;
        this.enlaceCuitService = enlaceCuitService;
        this.bultoService = bultoService;
        this.despachoService = despachoService;
        this.recepcionService = recepcionService;
    }

    // ---------- Actores ----------

    /** La Sede Central inicial (la recrea IntegracionBase antes de cada test). */
    public Usuario sede() {
        return usuarioRepository.findFirstByRol(RolUsuario.SEDE_CENTRAL).orElseThrow();
    }

    /** Usuario por email. */
    public Usuario usuario(String email) {
        return usuarioRepository.findByEmail(email).orElseThrow();
    }

    /** La Sede da de alta un inspector ACTIVO de la provincia. */
    public InspectorAnmat inspector(Provincia provincia) {
        int n = CONTADOR.incrementAndGet();
        InspectorAnmatRequestDTO dto = new InspectorAnmatRequestDTO();
        dto.setLegajo("INSP-IT-" + n);
        dto.setDni(String.format("3%07d", n));
        dto.setProvincia(provincia);
        dto.setEmail("inspector-" + n + "@integracion.test");
        dto.setPassword(CLAVE);
        dto.setNombre("Inspector");
        dto.setApellido("Prueba " + n);
        return ejecutor.ejecutarComo(sede(), () -> inspectorAnmatService.create(inspectorAnmatMapper.toEntity(dto), dto));
    }

    /**
     * Registro público de una empresa (con su PDF) y habilitación por el
     * inspector (tomar + habilitar, con D1). El admin del laboratorio es
     * además director técnico. Email del admin: admin-N@integracion.test.
     */
    public Empresa empresaHabilitada(TipoEmpresa tipo, Provincia provincia, InspectorAnmat inspector) {
        Empresa empresa = empresaRegistrada(tipo, provincia);
        return habilitar(empresa, inspector);
    }

    /** El inspector toma la solicitud PENDIENTE y habilita la empresa. */
    public Empresa habilitar(Empresa empresa, InspectorAnmat inspector) {
        return ejecutor.ejecutarComo(inspector.getUsuario(), () -> {
            empresaService.tomar(empresa.getId());
            return empresaService.habilitar(empresa.getId());
        });
    }

    /** Registro público de una empresa: queda PENDIENTE en la bandeja de los inspectores. */
    public Empresa empresaRegistrada(TipoEmpresa tipo, Provincia provincia) {
        int n = CONTADOR.incrementAndGet();
        RegistroEmpresaRequestDTO dto = new RegistroEmpresaRequestDTO();
        dto.setTipo(tipo);
        dto.setCuit(cuitValido(n));
        dto.setRazonSocial(tipo + " de prueba " + n);
        dto.setGln(gs1Valido("779900" + String.format("%06d", n)));
        dto.setProvincia(provincia);
        dto.setLocalidad("Localidad " + n);
        dto.setDomicilio("Calle de prueba " + n);
        dto.setNumeroHabilitacion("HAB-IT-" + n);
        dto.setDirectorTecnico(tipo == TipoEmpresa.DISTRIBUIDOR ? null : "Farm. Prueba " + n);
        dto.setAdminEmail("admin-" + n + "@integracion.test");
        dto.setAdminPassword(CLAVE);
        dto.setAdminNombre("Admin");
        dto.setAdminApellido("Prueba " + n);
        dto.setAdminDni(String.format("2%07d", n));
        dto.setAdminEsDirectorTecnico(tipo == TipoEmpresa.LABORATORIO);
        byte[] pdf = ("%PDF-1.4\n% MediChain integracion " + n + "\n%%EOF\n").getBytes(StandardCharsets.UTF_8);
        return empresaService.registrar(dto, pdf, "habilitacion-" + n + ".pdf");
    }

    /** El admin (inicial) de una empresa creada por el fixture. */
    public Usuario admin(Empresa empresa) {
        return usuarioRepository.findAll().stream()
                .filter(u -> u.getEmpresa() != null && u.getEmpresa().getId().equals(empresa.getId()))
                .findFirst().orElseThrow();
    }

    /** Inspector de la provincia + laboratorio, distribuidora y farmacia HABILITADA. */
    public Actores actores(Provincia provincia) {
        InspectorAnmat inspector = inspector(provincia);
        Empresa laboratorio = empresaHabilitada(TipoEmpresa.LABORATORIO, provincia, inspector);
        Empresa distribuidora = empresaHabilitada(TipoEmpresa.DISTRIBUIDOR, provincia, inspector);
        Empresa farmacia = empresaHabilitada(TipoEmpresa.FARMACIA, provincia, inspector);
        return new Actores(inspector, laboratorio, admin(laboratorio), distribuidora, admin(distribuidora),
                farmacia, admin(farmacia));
    }

    // ---------- Productos y circuito ----------

    /** Medicamento común (15–30 °C) del laboratorio. */
    public Medicamento medicamento(Actores actores) {
        int n = CONTADOR.incrementAndGet();
        MedicamentoRequestDTO dto = new MedicamentoRequestDTO();
        dto.setGtin(gs1Valido("0779900" + String.format("%06d", n)));
        dto.setNombreComercial("Medicamento " + n);
        dto.setPrincipioActivo("Principio " + n);
        dto.setConcentracion("500 mg");
        dto.setFormaFarmaceutica("Comprimido");
        dto.setPresentacion("Caja x 10");
        dto.setTemperaturaMinima(new BigDecimal("15.00"));
        dto.setTemperaturaMaxima(new BigDecimal("30.00"));
        dto.setBiologico(false);
        return ejecutor.ejecutarComo(actores.adminLaboratorio(),
                () -> medicamentoService.create(medicamentoMapper.toEntity(dto), dto));
    }

    /** Lote con series generadas por el servidor, PENDIENTE_LIBERACION. */
    public Lote lote(Actores actores, Medicamento medicamento, String codigo, int cantidad) {
        return lote(actores, medicamento, codigo, cantidad, null);
    }

    /**
     * Lote PENDIENTE_LIBERACION: con "series" (lista del laboratorio) o, si
     * es null, con "cantidad" series generadas por el servidor.
     */
    public Lote lote(Actores actores, Medicamento medicamento, String codigo, int cantidad, List<String> series) {
        LoteRequestDTO dto = new LoteRequestDTO();
        dto.setCodigo(codigo);
        dto.setFechaFabricacion(LocalDate.now().minusMonths(1));
        dto.setFechaVencimiento(LocalDate.now().plusYears(2));
        if (series == null) {
            dto.setCantidad(cantidad);
        } else {
            dto.setSeries(series);
        }
        dto.setMedicamentoId(medicamento.getId());
        return ejecutor.ejecutarComo(actores.adminLaboratorio(), () -> loteService.registrar(dto));
    }

    /** Lote liberado por el director técnico del laboratorio. */
    public Lote loteLiberado(Actores actores, Medicamento medicamento, String codigo, int cantidad) {
        Lote lote = lote(actores, medicamento, codigo, cantidad);
        return ejecutor.ejecutarComo(actores.adminLaboratorio(), () -> loteService.liberar(lote.getId()));
    }

    /** Circuito laboratorio → distribuidora → farmacia APROBADO (propuesto, aceptado por las dos, aprobado). */
    public EnlaceCuit circuitoAprobado(Actores actores) {
        EnlaceCuit circuito = ejecutor.ejecutarComo(actores.adminLaboratorio(), () -> enlaceCuitService.proponer(
                actores.distribuidora().getCuit(), actores.farmacia().getCuit()));
        ejecutor.ejecutarComo(actores.adminDistribuidora(), () -> enlaceCuitService.aceptar(circuito.getId()));
        ejecutor.ejecutarComo(actores.adminFarmacia(), () -> enlaceCuitService.aceptar(circuito.getId()));
        return ejecutor.ejecutarComo(actores.inspector().getUsuario(), () -> {
            enlaceCuitService.tomar(circuito.getId());
            return enlaceCuitService.aprobar(circuito.getId());
        });
    }

    // ---------- Logística ----------

    /**
     * Lleva "cantidad" cajas del lote (liberado) hasta la farmacia: bulto,
     * viaje del tramo 1, recepción en la distribuidora, viaje del tramo 2 y
     * recepción en la farmacia. Las cajas quedan EN_STOCK. Devuelve el bulto.
     */
    public Bulto cajasEnFarmacia(Actores actores, EnlaceCuit circuito, Lote lote, int cantidad) {
        Bulto bulto = ejecutor.ejecutarComo(actores.adminLaboratorio(), () -> {
            BultoRequestDTO dto = new BultoRequestDTO();
            dto.setCircuitoId(circuito.getId());
            dto.setLoteId(lote.getId());
            dto.setPrecinto("PRE-IT-" + CONTADOR.incrementAndGet());
            dto.setCantidad(cantidad);
            return bultoService.armar(dto);
        });
        viajarYRecibir(actores.adminLaboratorio(), actores.adminDistribuidora(), bulto, cantidad);
        viajarYRecibir(actores.adminDistribuidora(), actores.adminFarmacia(), bulto, cantidad);
        return bulto;
    }

    /** Viaje con un bulto: lo crea y despacha "origen"; lo recibe conforme "destino". */
    private void viajarYRecibir(Usuario origen, Usuario destino, Bulto bulto, int cantidad) {
        DespachoLogistico viaje = ejecutor.ejecutarComo(origen, () -> {
            DespachoLogisticoRequestDTO dto = new DespachoLogisticoRequestDTO();
            dto.setPatente("AA" + String.format("%03d", CONTADOR.incrementAndGet() % 1000) + "BB");
            dto.setChofer("Chofer de prueba");
            dto.setFechaEstimadaEntrega(LocalDateTime.now().plusDays(2));
            dto.setBultos(List.of(bulto.getCodigo()));
            DespachoLogistico creado = despachoService.crear(dto);
            return despachoService.salida(creado.getId());
        });
        ejecutor.ejecutarComo(destino, () -> {
            RecepcionRequestDTO dto = new RecepcionRequestDTO();
            dto.setCodigoBulto(bulto.getCodigo());
            dto.setPrecintoIntacto(true);
            dto.setCantidadVerificada(cantidad);
            dto.setTemperatura(new BigDecimal("20"));
            return recepcionService.recibir(dto);
        });
        if (viaje == null) {
            throw new IllegalStateException("No se creó el viaje");
        }
    }

    // ---------- Identificadores válidos ----------

    /** CUIT válido (dígito verificador módulo 11), distinto para cada n: 30-7NNNNNNN-D. */
    public static String cuitValido(int n) {
        for (int base = 71_000_000 + n * 10; ; base++) {
            String cuerpo = "30" + base;
            for (int digito = 0; digito <= 9; digito++) {
                String cuit = cuerpo.substring(0, 2) + "-" + cuerpo.substring(2) + "-" + digito;
                if (CuitUtil.esValido(cuit)) {
                    return cuit;
                }
            }
        }
    }

    /** Código GS1 (GTIN o GLN) válido: el cuerpo dado + su dígito verificador. */
    public static String gs1Valido(String cuerpo) {
        for (int digito = 0; digito <= 9; digito++) {
            if (Gs1Util.esValido(cuerpo + digito)) {
                return cuerpo + digito;
            }
        }
        throw new IllegalStateException("Sin dígito verificador para " + cuerpo);
    }

    /** Actores de una provincia: inspector y tres empresas habilitadas con sus admins. */
    public static final class Actores {

        private final InspectorAnmat inspector;
        private final Empresa laboratorio;
        private final Usuario adminLaboratorio;
        private final Empresa distribuidora;
        private final Usuario adminDistribuidora;
        private final Empresa farmacia;
        private final Usuario adminFarmacia;

        Actores(InspectorAnmat inspector, Empresa laboratorio, Usuario adminLaboratorio, Empresa distribuidora,
                Usuario adminDistribuidora, Empresa farmacia, Usuario adminFarmacia) {
            this.inspector = inspector;
            this.laboratorio = laboratorio;
            this.adminLaboratorio = adminLaboratorio;
            this.distribuidora = distribuidora;
            this.adminDistribuidora = adminDistribuidora;
            this.farmacia = farmacia;
            this.adminFarmacia = adminFarmacia;
        }

        public InspectorAnmat inspector() {
            return inspector;
        }

        public Empresa laboratorio() {
            return laboratorio;
        }

        public Usuario adminLaboratorio() {
            return adminLaboratorio;
        }

        public Empresa distribuidora() {
            return distribuidora;
        }

        public Usuario adminDistribuidora() {
            return adminDistribuidora;
        }

        public Empresa farmacia() {
            return farmacia;
        }

        public Usuario adminFarmacia() {
            return adminFarmacia;
        }
    }
}
