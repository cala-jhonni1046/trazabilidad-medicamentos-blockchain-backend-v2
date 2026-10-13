package com.medichain.modules.tablero;

import com.medichain.modules.cuarentena.CuarentenaRepository;
import com.medichain.modules.cuarentena.CuarentenaService;
import com.medichain.modules.cuarentena.EstadoCuarentena;
import com.medichain.modules.empresa.EmpresaRepository;
import com.medichain.modules.empresa.EmpresaService;
import com.medichain.modules.empresa.EstadoHabilitacion;
import com.medichain.modules.enlacecuit.EnlaceCuitRepository;
import com.medichain.modules.enlacecuit.EnlaceCuitService;
import com.medichain.modules.enlacecuit.EstadoEnlaceCuit;
import com.medichain.modules.inspectoranmat.EstadoInspector;
import com.medichain.modules.inspectoranmat.InspectorAnmatRepository;
import com.medichain.modules.lote.LoteService;
import com.medichain.modules.registroblockchain.EstadoAnclaje;
import com.medichain.modules.registroblockchain.RegistroBlockchainRepository;
import com.medichain.modules.reporteciudadano.ReporteCiudadanoService;
import com.medichain.modules.trazabilidad.CadenaEstado;
import com.medichain.modules.trazabilidad.CadenaEstadoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio TableroService en MediChain.
 * Totales para los tableros, en una sola consulta del frontend:
 * <ul>
 *   <li>Resumen de la Sede: cada total sale de un {@code count} del repositorio de su
 *       módulo; los eventos, del último número de la cadena (la numeración no tiene
 *       huecos, así no se cuenta la tabla más grande).</li>
 *   <li>Bandeja del inspector: el total de cada una de sus cinco bandejas, pidiendo una
 *       página de 1 elemento a los mismos services que las listan. Así el número es
 *       exactamente el de la lista, con la misma regla D1 (inspector ACTIVO de su
 *       provincia) y sin repetir ninguna consulta.</li>
 * </ul>
 * No hay entidad ni repositorio propios: solo agrupa lo de otros módulos.
 */
@Service
public class TableroService {

    private final EmpresaRepository empresaRepository;
    private final InspectorAnmatRepository inspectorRepository;
    private final EnlaceCuitRepository circuitoRepository;
    private final CuarentenaRepository cuarentenaRepository;
    private final RegistroBlockchainRepository registroBlockchainRepository;
    private final CadenaEstadoRepository cadenaEstadoRepository;
    private final EmpresaService empresaService;
    private final EnlaceCuitService circuitoService;
    private final LoteService loteService;
    private final CuarentenaService cuarentenaService;
    private final ReporteCiudadanoService reporteService;

    @Autowired
    public TableroService(EmpresaRepository empresaRepository, InspectorAnmatRepository inspectorRepository,
                          EnlaceCuitRepository circuitoRepository, CuarentenaRepository cuarentenaRepository,
                          RegistroBlockchainRepository registroBlockchainRepository,
                          CadenaEstadoRepository cadenaEstadoRepository, EmpresaService empresaService,
                          EnlaceCuitService circuitoService, LoteService loteService,
                          CuarentenaService cuarentenaService, ReporteCiudadanoService reporteService) {
        this.empresaRepository = empresaRepository;
        this.inspectorRepository = inspectorRepository;
        this.circuitoRepository = circuitoRepository;
        this.cuarentenaRepository = cuarentenaRepository;
        this.registroBlockchainRepository = registroBlockchainRepository;
        this.cadenaEstadoRepository = cadenaEstadoRepository;
        this.empresaService = empresaService;
        this.circuitoService = circuitoService;
        this.loteService = loteService;
        this.cuarentenaService = cuarentenaService;
        this.reporteService = reporteService;
    }

    /** Totales del tablero de la Sede (el controller restringe el rol). */
    @Transactional(readOnly = true)
    public TableroResumenResponseDTO resumen() {
        TableroResumenResponseDTO dto = new TableroResumenResponseDTO();
        dto.setEmpresas(empresaRepository.count());
        dto.setEmpresasPendientes(empresaRepository.countByEstado(EstadoHabilitacion.PENDIENTE));
        dto.setInspectoresActivos(inspectorRepository.countByEstado(EstadoInspector.ACTIVO));
        dto.setCircuitosAprobados(circuitoRepository.countByEstado(EstadoEnlaceCuit.APROBADO));
        dto.setCuarentenasActivas(cuarentenaRepository.countByEstado(EstadoCuarentena.ACTIVA));
        dto.setEventos(cadenaEstadoRepository.findFirstByNombre(CadenaEstado.PRINCIPAL)
                .map(CadenaEstado::getUltimoNumero).orElse(0L));
        dto.setAnclajesConfirmados(registroBlockchainRepository.countByEstado(EstadoAnclaje.CONFIRMADO));
        return dto;
    }

    /** Pendientes de cada bandeja del inspector que consulta (el service de cada una aplica D1). */
    @Transactional(readOnly = true)
    public BandejaInspectorResponseDTO bandejaInspector() {
        PageRequest unaFila = PageRequest.of(0, 1);
        BandejaInspectorResponseDTO dto = new BandejaInspectorResponseDTO();
        dto.setEmpresas(empresaService.bandeja(unaFila).getTotalElements());
        dto.setCircuitos(circuitoService.bandeja(unaFila).getTotalElements());
        dto.setLotesBiologicos(loteService.bandejaLiberacion(unaFila).getTotalElements());
        dto.setCuarentenas(cuarentenaService.bandeja(unaFila).getTotalElements());
        dto.setReportes(reporteService.bandeja(unaFila).getTotalElements());
        return dto;
    }
}
