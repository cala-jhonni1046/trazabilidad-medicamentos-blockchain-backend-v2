package com.medichain.testutil;

import com.medichain.modules.auth.UsuarioAutenticado;
import com.medichain.modules.empresa.Empresa;
import com.medichain.modules.empresa.TipoEmpresa;
import com.medichain.modules.bulto.Bulto;
import com.medichain.modules.despachologistico.DespachoLogistico;
import com.medichain.modules.despachologistico.TramoDespacho;
import com.medichain.modules.enlacecuit.EnlaceCuit;
import com.medichain.modules.inspectoranmat.InspectorAnmat;
import com.medichain.modules.lote.Lote;
import com.medichain.modules.unidadtrazable.UnidadTrazable;
import com.medichain.modules.medicamento.Medicamento;
import com.medichain.modules.registroblockchain.ReciboTransaccion;
import com.medichain.modules.registroblockchain.RegistroBlockchain;
import com.medichain.modules.registroblockchain.TransaccionFirmada;
import com.medichain.modules.usuario.RolUsuario;
import com.medichain.modules.usuario.Usuario;
import com.medichain.utils.enums.Provincia;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Helper DatosDePrueba para los tests unitarios de MediChain.
 * Construye entidades mínimas con id asignado (como si vinieran de la
 * base) y usuarios autenticados, para no repetir ese armado en cada test.
 */
public final class DatosDePrueba {

    private DatosDePrueba() {
    }

    /** Empresa del tipo dado, con id aleatorio, en estado PENDIENTE. */
    public static Empresa empresa(TipoEmpresa tipo) {
        Empresa empresa = new Empresa(tipo, "30-71234567-1", "Empresa " + tipo, Provincia.CORDOBA,
                "Córdoba", "Calle 123");
        empresa.setId(UUID.randomUUID());
        return empresa;
    }

    /** Empresa del tipo dado ya HABILITADA (tomada y habilitada por un inspector de su provincia). */
    public static Empresa empresaHabilitada(TipoEmpresa tipo) {
        Empresa empresa = empresa(tipo);
        InspectorAnmat inspector = inspector(empresa.getProvincia());
        empresa.tomar(inspector);
        empresa.habilitar(inspector);
        return empresa;
    }

    /** Inspector ACTIVO de la provincia dada, con id y cuenta de usuario con id. */
    public static InspectorAnmat inspector(Provincia provincia) {
        Usuario cuenta = new Usuario("insp-" + UUID.randomUUID() + "@demo.com", "hash", "Ana", "Perez",
                "12345678", RolUsuario.INSPECTOR);
        cuenta.setId(UUID.randomUUID());
        InspectorAnmat inspector = new InspectorAnmat("INSP-" + provincia, "12345678", provincia);
        inspector.setId(UUID.randomUUID());
        inspector.setUsuario(cuenta);
        return inspector;
    }

    /** Usuario autenticado correspondiente a la cuenta del inspector dado. */
    public static UsuarioAutenticado autenticadoInspector(InspectorAnmat inspector) {
        return new UsuarioAutenticado(inspector.getUsuario().getId(), inspector.getUsuario().getEmail(),
                RolUsuario.INSPECTOR, null, inspector.getProvincia().name());
    }

    /** Lote con id aleatorio, de un medicamento del laboratorio dado. */
    public static Lote loteDe(Empresa laboratorio) {
        Medicamento medicamento = new Medicamento("07791234567898", "Amoxidal", "Amoxicilina", "500 mg",
                "Comprimido", "Caja x 20");
        medicamento.setId(UUID.randomUUID());
        medicamento.setLaboratorio(laboratorio);
        Lote lote = new Lote("L2026-0001", LocalDate.of(2026, 1, 1), LocalDate.now().plusYears(1), 100, medicamento);
        lote.setId(UUID.randomUUID());
        return lote;
    }

    /** Usuario autenticado con el rol dado, perteneciente a la empresa dada (puede ser null). */
    public static UsuarioAutenticado autenticado(RolUsuario rol, Empresa empresa) {
        return new UsuarioAutenticado(UUID.randomUUID(), rol.name().toLowerCase() + "@demo.com", rol,
                empresa != null ? empresa.getId() : null, null);
    }

    /** Circuito PENDIENTE_EMPRESAS con id, entre las tres empresas dadas. */
    public static EnlaceCuit circuito(Empresa laboratorio, Empresa distribuidor, Empresa farmacia) {
        EnlaceCuit circuito = new EnlaceCuit("CIR-0001", laboratorio, distribuidor, farmacia, null);
        circuito.setId(UUID.randomUUID());
        return circuito;
    }

    /** Circuito llevado a PENDIENTE_INSPECTOR por los métodos de dominio (aceptaron las dos). */
    public static EnlaceCuit circuitoPendienteInspector(Empresa laboratorio, Empresa distribuidor, Empresa farmacia) {
        EnlaceCuit circuito = circuito(laboratorio, distribuidor, farmacia);
        circuito.aceptar(distribuidor);
        circuito.aceptar(farmacia);
        return circuito;
    }

    /** Circuito APROBADO por los métodos de dominio (aceptado, tomado y aprobado). */
    public static EnlaceCuit circuitoAprobado(Empresa laboratorio, Empresa distribuidor, Empresa farmacia) {
        EnlaceCuit circuito = circuitoPendienteInspector(laboratorio, distribuidor, farmacia);
        InspectorAnmat inspector = inspector(farmacia.getProvincia());
        circuito.tomar(inspector);
        circuito.aprobar(inspector);
        return circuito;
    }

    /** Caja con id del lote dado, con la serie dada (en poder del laboratorio). */
    public static UnidadTrazable caja(Lote lote, String serie) {
        UnidadTrazable caja = new UnidadTrazable(serie, lote);
        caja.setId(UUID.randomUUID());
        return caja;
    }

    /** Bulto ARMADO con id, del lote y circuito dados. */
    public static Bulto bulto(String codigo, Lote lote, EnlaceCuit circuito) {
        Bulto bulto = new Bulto(codigo, 10, "PRE-1", lote, circuito);
        bulto.setId(UUID.randomUUID());
        return bulto;
    }

    /** Viaje PROGRAMADO con id, del tramo y la empresa origen dados (sin bultos). */
    public static DespachoLogistico viaje(TramoDespacho tramo, Empresa origen) {
        DespachoLogistico viaje = new DespachoLogistico("VJ-0001", tramo, "AB123CD", "Juan Pérez",
                LocalDateTime.now().plusDays(1), origen, null);
        viaje.setId(UUID.randomUUID());
        return viaje;
    }

    /** Caja con id EN_STOCK en la farmacia dada, llevada hasta ahí por los métodos de dominio. */
    public static UnidadTrazable cajaEnStock(Lote lote, String serie, Empresa farmacia) {
        UnidadTrazable caja = caja(lote, serie);
        caja.salir();
        caja.recibirEnFarmacia(farmacia);
        return caja;
    }

    /** Transacción firmada de prueba (hash 0x + 64 hex) con nonce 7 y comisiones de 2 gwei / 0,1 gwei. */
    public static TransaccionFirmada transaccion(String hashTransaccion) {
        return new TransaccionFirmada(hashTransaccion, "0x02f8", BigInteger.valueOf(7), BigInteger.valueOf(80_000),
                BigInteger.valueOf(2_000_000_000L), BigInteger.valueOf(100_000_000L));
    }

    /** Anclaje CONFIRMADO con id (eventos desde..hasta), llevado hasta ahí por los métodos de dominio. */
    public static RegistroBlockchain anclajeConfirmado(long desde, long hasta, String hashAnclado, String contrato) {
        RegistroBlockchain anclaje = new RegistroBlockchain(desde, hasta, hashAnclado, "sepolia", contrato);
        anclaje.setId(UUID.randomUUID());
        anclaje.iniciarIntento();
        anclaje.registrarTransaccion(transaccion("0x" + "ab".repeat(32)));
        anclaje.marcarEnviado(LocalDateTime.of(2026, 10, 5, 12, 0));
        anclaje.registrarInclusion(new ReciboTransaccion(9_000_000L, true, 61_000L, 1_100_000_000L), 3, 3,
                LocalDateTime.of(2026, 10, 5, 12, 1));
        return anclaje;
    }
}
