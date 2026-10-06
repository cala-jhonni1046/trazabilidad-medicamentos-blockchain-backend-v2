package com.medichain.modules.cuarentena;

import com.medichain.modules.bulto.Bulto;
import com.medichain.modules.despachologistico.DespachoLogistico;
import com.medichain.modules.trazabilidad.DatosEventos;
import com.medichain.modules.trazabilidad.RegistradorEventos;
import com.medichain.modules.trazabilidad.TipoEvento;
import com.medichain.utils.enums.Provincia;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio AperturaCuarentenas en MediChain.
 * Abre las cuarentenas AUTOMÁTICAS (las abre el sistema, no un usuario).
 * Alcance BULTO: recepción rechazada (R8). Alcance DESPACHO: ruptura de frío (R9, solo los bultos afectados)
 * y robo/extravío (R14, todos los bultos del viaje). La provincia de cada
 * medida es la del laboratorio de los lotes (quien dictamina un eventual
 * recall); si el viaje lleva bultos de laboratorios de distintas provincias,
 * se abre una medida por provincia. Cada medida emite CUARENTENA con actor
 * "sistema" (sin usuario ni empresa). Corre dentro de la transacción del
 * negocio que la dispara.
 */
@Service
public class AperturaCuarentenas {

    private final CuarentenaRepository repository;
    private final RegistradorEventos registradorEventos;

    @Autowired
    public AperturaCuarentenas(CuarentenaRepository repository, RegistradorEventos registradorEventos) {
        this.repository = repository;
        this.registradorEventos = registradorEventos;
    }

    /** Abre una cuarentena DESPACHO por provincia de laboratorio sobre los bultos dados. */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Cuarentena> abrirPorDespacho(MotivoBloqueo motivo, DespachoLogistico despacho,
                                             Collection<Bulto> bultos) {
        Map<Provincia, List<Bulto>> porProvincia = new LinkedHashMap<>();
        for (Bulto bulto : bultos) {
            Provincia provincia = bulto.getLote().getLaboratorio().getProvincia();
            porProvincia.computeIfAbsent(provincia, p -> new ArrayList<>()).add(bulto);
        }
        List<Cuarentena> abiertas = new ArrayList<>();
        for (Map.Entry<Provincia, List<Bulto>> grupo : porProvincia.entrySet()) {
            Cuarentena cuarentena = repository.save(
                    Cuarentena.automaticaDeDespacho(motivo, grupo.getKey(), despacho, grupo.getValue()));
            registradorEventos.registrar(TipoEvento.CUARENTENA, "Cuarentena", cuarentena.getId(),
                    DatosEventos.cuarentena(cuarentena), null, null);
            abiertas.add(cuarentena);
        }
        return abiertas;
    }

    /**
     * Abre la cuarentena automática de alcance BULTO por una recepción rechazada
     * (R8). Provincia: la del laboratorio del lote del bulto.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Cuarentena abrirPorBulto(Bulto bulto) {
        Cuarentena cuarentena = repository.save(
                Cuarentena.automaticaDeBulto(bulto.getLote().getLaboratorio().getProvincia(), bulto));
        registradorEventos.registrar(TipoEvento.CUARENTENA, "Cuarentena", cuarentena.getId(),
                DatosEventos.cuarentena(cuarentena), null, null);
        return cuarentena;
    }
}
