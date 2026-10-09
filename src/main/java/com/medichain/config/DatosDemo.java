package com.medichain.config;

import com.medichain.modules.bulto.Bulto;
import com.medichain.modules.bulto.BultoRequestDTO;
import com.medichain.modules.bulto.BultoService;
import com.medichain.modules.despachologistico.DespachoLogisticoRequestDTO;
import com.medichain.modules.despachologistico.DespachoLogisticoService;
import com.medichain.modules.empresa.Empresa;
import com.medichain.modules.empresa.EmpresaRepository;
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
import com.medichain.modules.reporteciudadano.MotivoReporte;
import com.medichain.modules.reporteciudadano.ReporteCiudadanoRequestDTO;
import com.medichain.modules.reporteciudadano.ReporteCiudadanoService;
import com.medichain.modules.usuario.RegistroPacienteRequestDTO;
import com.medichain.modules.usuario.RolUsuario;
import com.medichain.modules.usuario.Usuario;
import com.medichain.modules.usuario.UsuarioRepository;
import com.medichain.modules.usuario.UsuarioService;
import com.medichain.utils.enums.Provincia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * CommandLineRunner DatosDemo en MediChain.
 * Carga un escenario de demostración, SOLO con el perfil "demo"
 * (SPRING_PROFILES_ACTIVE=demo), todo en Mendoza y con nombres 100%
 * ficticios. Usa las MISMAS acciones que la API (Services reales, con
 * EjecutorComoUsuario para poner al actor en el contexto de seguridad),
 * así la cadena cuenta la historia completa y pasa por las mismas reglas:
 * <ol>
 *   <li>La Sede da de alta al inspector de Mendoza (ALTA_INSPECTOR).</li>
 *   <li>Se registran un laboratorio, una distribuidora y dos farmacias con
 *       su admin y un PDF mínimo (SOLICITUD_HABILITACION).</li>
 *   <li>El inspector toma y habilita cada una (SOLICITUD_TOMADA,
 *       HABILITACION_APROBADA).</li>
 *   <li>Se registra un paciente (sin evento).</li>
 *   <li>El admin del laboratorio (también DT) registra Cuyafen y su lote
 *       L2026-0001 con 20 cajas generadas, y lo libera (LOTE_LIBERADO).</li>
 *   <li>Registra la vacuna biológica ficticia Andivax y su lote L2026-0002 con
 *       10 series en lista, que queda PENDIENTE_LIBERACION para el inspector.</li>
 *   <li>Circuito A (Andino → Piedemonte → Los Álamos): propuesto por el DT,
 *       aceptado por las dos empresas, tomado y aprobado por el inspector.</li>
 *   <li>Circuito B (Andino → Piedemonte → Cerro Arco): solo propuesto, para
 *       mostrar en vivo la aceptación y la aprobación.</li>
 *   <li>Sobre el circuito A, dos bultos de L2026-0001: BUL-0001 en el viaje
 *       VJ-0001 del tramo 1, PROGRAMADO (para la salida y la ruptura de frío
 *       en vivo), y BUL-0002 ARMADO sin viaje (camino feliz).</li>
 *   <li>Reporte ciudadano REP-0001 del paciente, ABIERTO: una caja de Cuyafen
 *       con una serie que no existe (sospecha de falsificación), en Mendoza.</li>
 * </ol>
 * Idempotente (si el laboratorio de demo ya existe por CUIT, no carga
 * nada) y en una sola transacción. La contraseña de todos sale de
 * DEMO_PASSWORD; necesita el usuario SEDE_CENTRAL de DatosIniciales.
 */
@Component
@Profile("demo")
@Order(2)
public class DatosDemo implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DatosDemo.class);

    // CUIT y GLN calculados con los mismos algoritmos de CuitUtil y Gs1Util (pasan los validadores).
    private static final String CUIT_LABORATORIO = "30-71000001-4";
    private static final String CUIT_DISTRIBUIDORA = "30-71000002-2";
    private static final String CUIT_FARMACIA = "30-71000003-0";
    private static final String CUIT_FARMACIA_DOS = "30-71000004-9";
    private static final String GTIN_MEDICAMENTO = "07799000001010";
    private static final String GTIN_VACUNA = "07799000002024";
    private static final String EMAIL_ADMIN_LABORATORIO = "admin@laboratorio-andino.demo";

    private final UsuarioRepository usuarioRepository;
    private final EmpresaRepository empresaRepository;
    private final EmpresaService empresaService;
    private final InspectorAnmatService inspectorAnmatService;
    private final InspectorAnmatMapper inspectorAnmatMapper;
    private final UsuarioService usuarioService;
    private final MedicamentoService medicamentoService;
    private final MedicamentoMapper medicamentoMapper;
    private final LoteService loteService;
    private final EnlaceCuitService enlaceCuitService;
    private final BultoService bultoService;
    private final DespachoLogisticoService despachoLogisticoService;
    private final ReporteCiudadanoService reporteCiudadanoService;
    private final EjecutorComoUsuario ejecutor;
    private final String demoPassword;

    @Autowired
    public DatosDemo(UsuarioRepository usuarioRepository, EmpresaRepository empresaRepository,
                     EmpresaService empresaService, InspectorAnmatService inspectorAnmatService,
                     InspectorAnmatMapper inspectorAnmatMapper, UsuarioService usuarioService,
                     MedicamentoService medicamentoService, MedicamentoMapper medicamentoMapper,
                     LoteService loteService, EnlaceCuitService enlaceCuitService, BultoService bultoService,
                     DespachoLogisticoService despachoLogisticoService,
                     ReporteCiudadanoService reporteCiudadanoService,
                     EjecutorComoUsuario ejecutor,
                     @Value("${medichain.demo.password}") String demoPassword) {
        this.usuarioRepository = usuarioRepository;
        this.empresaRepository = empresaRepository;
        this.empresaService = empresaService;
        this.inspectorAnmatService = inspectorAnmatService;
        this.inspectorAnmatMapper = inspectorAnmatMapper;
        this.usuarioService = usuarioService;
        this.medicamentoService = medicamentoService;
        this.medicamentoMapper = medicamentoMapper;
        this.loteService = loteService;
        this.enlaceCuitService = enlaceCuitService;
        this.bultoService = bultoService;
        this.despachoLogisticoService = despachoLogisticoService;
        this.reporteCiudadanoService = reporteCiudadanoService;
        this.ejecutor = ejecutor;
        this.demoPassword = demoPassword;
    }

    /** Carga el escenario de demostración si corresponde. */
    @Override
    @Transactional
    public void run(String... args) {
        if (demoPassword == null || demoPassword.isBlank()) {
            logger.warn("Perfil demo activo pero falta DEMO_PASSWORD: no se cargan los datos de demo.");
            return;
        }
        if (empresaRepository.existsByCuit(CUIT_LABORATORIO)) {
            logger.info("Los datos de demo ya están cargados: no se duplican.");
            return;
        }
        Optional<Usuario> sede = usuarioRepository.findFirstByRol(RolUsuario.SEDE_CENTRAL);
        if (sede.isEmpty()) {
            logger.warn("No hay usuario SEDE_CENTRAL (definí ADMIN_EMAIL y ADMIN_PASSWORD): no se cargan los datos de demo.");
            return;
        }
        List<String> creados = new ArrayList<>();

        // 1. La Sede da de alta al inspector de Mendoza.
        InspectorAnmatRequestDTO inspectorDto = new InspectorAnmatRequestDTO();
        inspectorDto.setLegajo("INSP-MZA-001");
        inspectorDto.setDni("30111001");
        inspectorDto.setProvincia(Provincia.MENDOZA);
        inspectorDto.setEmail("inspector.mendoza@medichain.demo");
        inspectorDto.setPassword(demoPassword);
        inspectorDto.setNombre("Rocío");
        inspectorDto.setApellido("Quiroga");
        InspectorAnmat inspector = ejecutor.ejecutarComo(sede.get(),
                () -> inspectorAnmatService.create(inspectorAnmatMapper.toEntity(inspectorDto), inspectorDto));
        creados.add(inspectorDto.getEmail() + " (INSPECTOR)");

        // 2. Registro público de las cuatro empresas, cada una con su admin y su PDF.
        List<Empresa> empresas = new ArrayList<>();
        empresas.add(registrar(TipoEmpresa.LABORATORIO, CUIT_LABORATORIO, "Laboratorio Andino Cuyano S.A.",
                "7799000000013", "Godoy Cruz", "Calle Las Viñas 100", "HAB-MZA-001", "Farm. Elena Sosa",
                EMAIL_ADMIN_LABORATORIO, "Elena", "Sosa", "30111002", true, creados));
        empresas.add(registrar(TipoEmpresa.DISTRIBUIDOR, CUIT_DISTRIBUIDORA, "Distribuidora Piedemonte S.R.L.",
                "7799000000020", "Luján de Cuyo", "Ruta del Vino 2500", "HAB-MZA-002", null,
                "admin@distribuidora-piedemonte.demo", "Pablo", "Funes", "30111003", false, creados));
        empresas.add(registrar(TipoEmpresa.FARMACIA, CUIT_FARMACIA, "Farmacia Los Álamos",
                "7799000000037", "Mendoza", "Av. de los Álamos 450", "HAB-MZA-003", "Farm. Martín Videla",
                "admin@farmacia-losalamos.demo", "Martín", "Videla", "30111004", false, creados));
        empresas.add(registrar(TipoEmpresa.FARMACIA, CUIT_FARMACIA_DOS, "Farmacia Cerro Arco",
                "7799000000044", "Las Heras", "Calle Precordillera 77", "HAB-MZA-004", "Farm. Lucía Ortiz",
                "admin@farmacia-cerroarco.demo", "Lucía", "Ortiz", "30111005", false, creados));

        // 3. El inspector toma y habilita cada solicitud (mismo camino que la API, con D1).
        ejecutor.ejecutarComo(inspector.getUsuario(), () -> {
            for (Empresa empresa : empresas) {
                empresaService.tomar(empresa.getId());
                empresaService.habilitar(empresa.getId());
            }
        });

        // 4. Paciente (registro público, sin evento).
        RegistroPacienteRequestDTO pacienteDto = new RegistroPacienteRequestDTO();
        pacienteDto.setEmail("paciente@medichain.demo");
        pacienteDto.setPassword(demoPassword);
        pacienteDto.setNombre("Tomás");
        pacienteDto.setApellido("Aguirre");
        pacienteDto.setDni("30111006");
        Usuario paciente = usuarioService.registrarPaciente(pacienteDto);
        creados.add(pacienteDto.getEmail() + " (PACIENTE)");

        // 5. El admin del laboratorio registra el medicamento y el lote.
        Usuario adminLaboratorio = usuario(EMAIL_ADMIN_LABORATORIO);
        Lote loteComun = ejecutor.ejecutarComo(adminLaboratorio, () -> {
            MedicamentoRequestDTO medicamentoDto = new MedicamentoRequestDTO();
            medicamentoDto.setGtin(GTIN_MEDICAMENTO);
            medicamentoDto.setNombreComercial("Cuyafen");
            medicamentoDto.setPrincipioActivo("Ibuprofeno");
            medicamentoDto.setConcentracion("400 mg");
            medicamentoDto.setFormaFarmaceutica("Comprimido");
            medicamentoDto.setPresentacion("Caja x 20");
            medicamentoDto.setTemperaturaMinima(new BigDecimal("15.00"));
            medicamentoDto.setTemperaturaMaxima(new BigDecimal("30.00"));
            medicamentoDto.setBiologico(false);
            Medicamento medicamento = medicamentoService.create(medicamentoMapper.toEntity(medicamentoDto), medicamentoDto);

            // Lote común con 20 cajas generadas por el servidor (L20260001S000001…), liberado por el DT.
            LoteRequestDTO loteDto = new LoteRequestDTO();
            loteDto.setCodigo("L2026-0001");
            loteDto.setFechaFabricacion(LocalDate.of(2026, 1, 15));
            loteDto.setFechaVencimiento(LocalDate.of(2028, 1, 15));
            loteDto.setCantidad(20);
            loteDto.setMedicamentoId(medicamento.getId());
            Lote lote = loteService.registrar(loteDto);
            loteService.liberar(lote.getId());

            // Vacuna biológica ficticia: su lote queda PENDIENTE_LIBERACION para que lo libere el inspector.
            MedicamentoRequestDTO vacunaDto = new MedicamentoRequestDTO();
            vacunaDto.setGtin(GTIN_VACUNA);
            vacunaDto.setNombreComercial("Andivax");
            vacunaDto.setPrincipioActivo("Vacuna antigripal (ficticia)");
            vacunaDto.setConcentracion("0,5 ml");
            vacunaDto.setFormaFarmaceutica("Suspensión inyectable");
            vacunaDto.setPresentacion("Jeringa prellenada x 1");
            vacunaDto.setTemperaturaMinima(new BigDecimal("2.00"));
            vacunaDto.setTemperaturaMaxima(new BigDecimal("8.00"));
            vacunaDto.setBiologico(true);
            Medicamento vacuna = medicamentoService.create(medicamentoMapper.toEntity(vacunaDto), vacunaDto);

            // Lote biológico con 10 series en lista explícita (como las imprime el laboratorio en el DataMatrix).
            List<String> seriesVacuna = new ArrayList<>();
            for (int i = 1; i <= 10; i++) {
                seriesVacuna.add(String.format("AVX26%06d", i));
            }
            LoteRequestDTO loteVacunaDto = new LoteRequestDTO();
            loteVacunaDto.setCodigo("L2026-0002");
            loteVacunaDto.setFechaFabricacion(LocalDate.of(2026, 8, 1));
            loteVacunaDto.setFechaVencimiento(LocalDate.of(2027, 8, 1));
            loteVacunaDto.setSeries(seriesVacuna);
            loteVacunaDto.setMedicamentoId(vacuna.getId());
            loteService.registrar(loteVacunaDto);
            return lote;
        });

        // 6. Circuito A completo (APROBADO) y circuito B solo propuesto.
        EnlaceCuit circuitoA = ejecutor.ejecutarComo(adminLaboratorio,
                () -> enlaceCuitService.proponer(CUIT_DISTRIBUIDORA, CUIT_FARMACIA));
        ejecutor.ejecutarComo(usuario("admin@distribuidora-piedemonte.demo"),
                () -> enlaceCuitService.aceptar(circuitoA.getId()));
        ejecutor.ejecutarComo(usuario("admin@farmacia-losalamos.demo"),
                () -> enlaceCuitService.aceptar(circuitoA.getId()));
        ejecutor.ejecutarComo(inspector.getUsuario(), () -> {
            enlaceCuitService.tomar(circuitoA.getId());
            enlaceCuitService.aprobar(circuitoA.getId());
        });
        ejecutor.ejecutarComo(adminLaboratorio,
                () -> enlaceCuitService.proponer(CUIT_DISTRIBUIDORA, CUIT_FARMACIA_DOS));

        // 7. Bultos sobre el circuito A: BUL-0001 viaja en VJ-0001 (PROGRAMADO, para la ruptura de frío
        //    en vivo) y BUL-0002 queda ARMADO sin viaje (camino feliz en vivo y en 7e).
        ejecutor.ejecutarComo(adminLaboratorio, () -> {
            BultoRequestDTO primero = new BultoRequestDTO();
            primero.setCircuitoId(circuitoA.getId());
            primero.setLoteId(loteComun.getId());
            primero.setPrecinto("PRE-000001");
            List<String> seriesPrimero = new ArrayList<>();
            for (int i = 1; i <= 10; i++) {
                seriesPrimero.add(String.format("L20260001S%06d", i));
            }
            primero.setSeries(seriesPrimero);
            Bulto bultoUno = bultoService.armar(primero);

            BultoRequestDTO segundo = new BultoRequestDTO();
            segundo.setCircuitoId(circuitoA.getId());
            segundo.setLoteId(loteComun.getId());
            segundo.setPrecinto("PRE-000002");
            segundo.setCantidad(10);
            bultoService.armar(segundo);

            DespachoLogisticoRequestDTO viaje = new DespachoLogisticoRequestDTO();
            viaje.setPatente("AA000AA");
            viaje.setChofer("Chofer Demo");
            viaje.setFechaEstimadaEntrega(OffsetDateTime.now().plusDays(2));
            viaje.setBultos(List.of(bultoUno.getCodigo()));
            despachoLogisticoService.crear(viaje);
        });

        // 8. Reporte ciudadano REP-0001 del paciente (ABIERTO): compró una caja de Cuyafen cuya serie no existe.
        ejecutor.ejecutarComo(paciente, () -> {
            ReporteCiudadanoRequestDTO reporte = new ReporteCiudadanoRequestDTO();
            reporte.setGtin(GTIN_MEDICAMENTO);
            reporte.setSerie("FALSA00099");
            reporte.setMotivo(MotivoReporte.SOSPECHA_FALSIFICACION);
            reporte.setProvincia(Provincia.MENDOZA);
            reporte.setDescripcion("Compré la caja en una feria; al verificarla dice que no existe. (Texto ficticio de demo.)");
            reporteCiudadanoService.reportar(reporte);
        });

        StringBuilder resumen = new StringBuilder("Datos de demo cargados (contraseña: la de DEMO_PASSWORD):");
        for (String creado : creados) {
            resumen.append("\n  - ").append(creado);
        }
        logger.info(resumen.toString());
    }

    /** Busca un usuario de demo por email. */
    private Usuario usuario(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Falta el usuario de demo " + email));
    }

    /** Registra una empresa con su admin por el mismo camino que POST /api/registro/empresas. */
    private Empresa registrar(TipoEmpresa tipo, String cuit, String razonSocial, String gln, String localidad,
                              String domicilio, String numeroHabilitacion, String directorTecnico,
                              String adminEmail, String adminNombre, String adminApellido, String adminDni,
                              boolean adminEsDirectorTecnico, List<String> creados) {
        RegistroEmpresaRequestDTO dto = new RegistroEmpresaRequestDTO();
        dto.setTipo(tipo);
        dto.setCuit(cuit);
        dto.setRazonSocial(razonSocial);
        dto.setGln(gln);
        dto.setProvincia(Provincia.MENDOZA);
        dto.setLocalidad(localidad);
        dto.setDomicilio(domicilio);
        dto.setNumeroHabilitacion(numeroHabilitacion);
        dto.setDirectorTecnico(directorTecnico);
        dto.setAdminEmail(adminEmail);
        dto.setAdminPassword(demoPassword);
        dto.setAdminNombre(adminNombre);
        dto.setAdminApellido(adminApellido);
        dto.setAdminDni(adminDni);
        dto.setAdminEsDirectorTecnico(adminEsDirectorTecnico);
        // PDF mínimo pero real (empieza con "%PDF-"), distinto por empresa.
        byte[] pdf = ("%PDF-1.4\n% MediChain demo: habilitacion de " + razonSocial + "\n%%EOF\n")
                .getBytes(StandardCharsets.UTF_8);
        Empresa empresa = empresaService.registrar(dto, pdf, "habilitacion-" + cuit + ".pdf");
        creados.add(adminEmail + " (" + tipo + (adminEsDirectorTecnico ? ", admin y DT" : ", admin") + ")");
        return empresa;
    }
}
