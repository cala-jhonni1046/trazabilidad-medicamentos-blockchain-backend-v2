package com.medichain.integracion;

import com.medichain.modules.bulto.Bulto;
import com.medichain.modules.despachologistico.DespachoLogistico;
import com.medichain.modules.enlacecuit.EnlaceCuit;
import com.medichain.modules.inspectoranmat.InspectorAnmat;
import com.medichain.modules.lote.Lote;
import com.medichain.modules.medicamento.Medicamento;
import com.medichain.utils.enums.Provincia;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test de integración NombresEnDtoIT en MediChain (paso B12c).
 * Los DTO de salida traen los nombres (razón social, nombre y apellido del
 * inspector, código del lote, nombre del medicamento) además de los ids, así el
 * frontend no pide cada nombre por separado. Se comprueba con datos reales que
 * cada nombre sea el de la entidad, que los campos nulables vengan null cuando
 * corresponde y que un listado no haga más consultas por tener más filas (N+1).
 */
class NombresEnDtoIT extends IntegracionBase {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("Inspector, circuito, viaje, usuario, lote, bulto, caja y cuarentena traen sus nombres")
    void nombresDeCadaDto() throws Exception {
        EscenarioIntegracion.Actores actores = escenario.actores(Provincia.CHUBUT);
        Medicamento medicamento = escenario.medicamento(actores);
        EnlaceCuit circuito = escenario.circuitoAprobado(actores);
        Lote lote = escenario.loteLiberado(actores, medicamento, "NM-0001", 4);
        Bulto bulto = escenario.bultoArmado(actores, circuito, lote, 2);
        DespachoLogistico viaje = escenario.viajeEnTransito(actores.adminLaboratorio(), bulto);
        String sede = bearer(escenario.sede().getEmail(), "clave-falsa-de-la-sede");
        String laboratorio = bearer(actores.adminLaboratorio().getEmail(), EscenarioIntegracion.CLAVE);
        String inspector = bearer(actores.inspector().getUsuario().getEmail(), EscenarioIntegracion.CLAVE);
        String razonLaboratorio = actores.laboratorio().getRazonSocial();
        String razonDistribuidora = actores.distribuidora().getRazonSocial();
        String razonFarmacia = actores.farmacia().getRazonSocial();

        // Inspector: nombre y apellido de su cuenta.
        InspectorAnmat insp = actores.inspector();
        JsonNode inspectores = pagina("/api/inspectores-anmat?size=100", sede);
        JsonNode fila = buscar(inspectores, "id", insp.getId().toString());
        assertEquals(insp.getUsuario().getNombre(), fila.path("nombre").asString());
        assertEquals(insp.getUsuario().getApellido(), fila.path("apellido").asString());

        // Circuito: las tres razones sociales.
        JsonNode circuitos = pagina("/api/circuitos?size=100", sede);
        fila = buscar(circuitos, "id", circuito.getId().toString());
        assertEquals(razonLaboratorio, fila.path("laboratorioRazonSocial").asString());
        assertEquals(razonDistribuidora, fila.path("distribuidorRazonSocial").asString());
        assertEquals(razonFarmacia, fila.path("farmaciaRazonSocial").asString());

        // Viaje: origen y paradas (en el mismo orden que paradaIds).
        fila = buscar(pagina("/api/viajes?size=100", sede), "id", viaje.getId().toString());
        assertEquals(razonLaboratorio, fila.path("origenRazonSocial").asString());
        assertEquals(fila.path("paradaIds").size(), fila.path("paradaRazonesSociales").size());
        assertEquals(List.of(razonDistribuidora), textos(fila.path("paradaRazonesSociales")), "tramo 1: la parada es la distribuidora");

        // Usuario: la empresa del admin del laboratorio; la Sede no tiene empresa (null).
        JsonNode usuarios = pagina("/api/usuarios?size=100", sede);
        fila = buscar(usuarios, "email", actores.adminLaboratorio().getEmail());
        assertEquals(razonLaboratorio, fila.path("empresaRazonSocial").asString());
        fila = buscar(usuarios, "email", escenario.sede().getEmail());
        assertTrue(fila.path("empresaRazonSocial").isNull(), "la Sede no tiene empresa: " + fila);

        // Lote: medicamento y laboratorio.
        fila = buscar(pagina("/api/lotes?size=100", laboratorio), "id", lote.getId().toString());
        assertEquals(medicamento.getNombreComercial(), fila.path("medicamentoNombre").asString());
        assertEquals(razonLaboratorio, fila.path("laboratorioRazonSocial").asString());

        // Bulto: lote, circuito (destinoId es el id del circuito) y las dos empresas del circuito.
        fila = buscar(pagina("/api/bultos?size=100", laboratorio), "id", bulto.getId().toString());
        assertEquals("NM-0001", fila.path("loteCodigo").asString());
        assertEquals(circuito.getCodigo(), fila.path("circuitoCodigo").asString());
        assertEquals(razonDistribuidora, fila.path("distribuidorRazonSocial").asString());
        assertEquals(razonFarmacia, fila.path("farmaciaRazonSocial").asString());

        // Caja: lote y medicamento.
        JsonNode cajas = pagina("/api/unidades-trazables?size=100", laboratorio);
        assertEquals(4, cajas.path("content").size());
        for (JsonNode caja : cajas.path("content")) {
            assertEquals("NM-0001", caja.path("loteCodigo").asString());
            assertEquals(medicamento.getNombreComercial(), caja.path("medicamentoNombre").asString());
        }

        // Cuarentena de alcance LOTE: trae el código del lote; las de otro alcance lo traen null.
        mockMvc.perform(post("/api/cuarentenas").header("Authorization", inspector)
                        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(Map.of(
                                "loteId", lote.getId().toString(), "motivo", "PREVENTIVA", "descripcion", "Control"))))
                .andExpect(status().isCreated());
        JsonNode cuarentenas = pagina("/api/cuarentenas?size=100", inspector);
        assertEquals(1, cuarentenas.path("content").size());
        assertEquals("NM-0001", cuarentenas.path("content").get(0).path("loteCodigo").asString());
    }

    @Test
    @DisplayName("Sin N+1: listar bultos, lotes, cajas, circuitos, viajes, inspectores y usuarios de 3 empresas cuesta las mismas consultas que de 1")
    void listadosSinNMasUno() throws Exception {
        EscenarioIntegracion.Actores a = escenario.actores(Provincia.SANTA_CRUZ);
        EscenarioIntegracion.Actores b = escenario.actores(Provincia.SANTA_CRUZ);
        EscenarioIntegracion.Actores c = escenario.actores(Provincia.SANTA_CRUZ);
        for (EscenarioIntegracion.Actores actores : List.of(a, b, c)) {
            Medicamento medicamento = escenario.medicamento(actores);
            EnlaceCuit circuito = escenario.circuitoAprobado(actores);
            Lote lote = escenario.loteLiberado(actores, medicamento, "N1-" + actores.laboratorio().getCuit().substring(9, 12), 6);
            Lote otroLote = escenario.loteLiberado(actores, medicamento, "N2-" + actores.laboratorio().getCuit().substring(9, 12), 2);
            Bulto primero = escenario.bultoArmado(actores, circuito, lote, 2);
            escenario.bultoArmado(actores, circuito, lote, 2);
            Bulto tercero = escenario.bultoArmado(actores, circuito, otroLote, 2);
            escenario.viajeEnTransito(actores.adminLaboratorio(), primero);
            escenario.viajeEnTransito(actores.adminLaboratorio(), tercero);
        }
        String sede = bearer(escenario.sede().getEmail(), "clave-falsa-de-la-sede");
        Statistics estadisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        for (String ruta : List.of("/api/bultos", "/api/lotes", "/api/unidades-trazables", "/api/circuitos",
                "/api/viajes", "/api/inspectores-anmat", "/api/usuarios")) {
            // Una página de pocas filas contra otra de todas menos una: las dos son parciales (llevan su consulta
            // de conteo; una página completa no la lleva y no se podrían comparar) y la segunda mezcla empresas.
            long total = pagina(ruta + "?size=1", sede).path("page").path("totalElements").asLong();
            assertTrue(total >= 3, ruta + ": el escenario debería tener al menos 3 filas (" + total + ")");
            // Las cajas se ordenan por serie y la primera página es un lote entero (cajas en bultos y sueltas),
            // como en BloqueoIT: así las dos páginas pasan por las mismas clases de consulta.
            // En usuarios, lo más nuevo primero: el más viejo es la Sede, que no tiene empresa que buscar.
            String orden = ruta.equals("/api/unidades-trazables") ? "serie"
                    : ruta.equals("/api/usuarios") ? "fechaCreacion,desc" : "fechaCreacion,asc";
            int filas = ruta.equals("/api/unidades-trazables") ? 6 : 1;
            estadisticas.clear();
            pagina(ruta + "?size=" + filas + "&sort=" + orden, sede);
            long pocas = estadisticas.getPrepareStatementCount();
            estadisticas.clear();
            pagina(ruta + "?size=" + (total - 1) + "&sort=" + orden, sede);
            long muchas = estadisticas.getPrepareStatementCount();
            assertEquals(pocas, muchas, ruta + ": las consultas no crecen con las filas (" + pocas + " vs " + muchas + ")");
        }
    }

    // ---------- Utilidades ----------

    private JsonNode pagina(String url, String token) throws Exception {
        return json(mockMvc.perform(get(url).header("Authorization", token)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    /** El elemento de la página cuyo campo tiene ese valor (falla si no está). */
    private static JsonNode buscar(JsonNode pagina, String campo, String valor) {
        for (JsonNode elemento : pagina.path("content")) {
            if (valor.equals(elemento.path(campo).asString())) {
                return elemento;
            }
        }
        throw new AssertionError("no hay un elemento con " + campo + "=" + valor + " en " + pagina);
    }

    private static List<String> textos(JsonNode lista) {
        List<String> textos = new ArrayList<>();
        lista.forEach(e -> textos.add(e.asString()));
        return textos;
    }
}
