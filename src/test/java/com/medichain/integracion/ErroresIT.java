package com.medichain.integracion;

import com.medichain.modules.inspectoranmat.InspectorAnmat;
import com.medichain.modules.inspectoranmat.InspectorAnmatRepository;
import com.medichain.modules.usuario.RolUsuario;
import com.medichain.modules.usuario.Usuario;
import com.medichain.modules.usuario.UsuarioRepository;
import com.medichain.utils.ErrorResponseDTO;
import com.medichain.utils.GlobalExceptionHandler;
import com.medichain.utils.enums.Provincia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test de integración ErroresIT en MediChain (paso B11b, punto G).
 * Todo 409 trae su código, y una violación de una restricción de la base
 * nunca deja el valor en el log:
 * <ul>
 *   <li>GTIN repetido → MEDICAMENTO_DUPLICADO; legajo o DNI de inspector
 *       repetido → INSPECTOR_DUPLICADO (chequeo previo del Service).</li>
 *   <li>El mismo duplicado llegando a PostgreSQL (sin el chequeo previo, como en
 *       una carrera): el handler responde INSPECTOR_DUPLICADO y ningún log
 *       (ni el handler ni Hibernate) tiene el DNI. Eso lo garantiza el driver con
 *       logServerErrorDetail=false: sin él, Hibernate loguea el detalle de
 *       PostgreSQL ("Key (dni)=(…)") y el insert con sus valores.</li>
 * </ul>
 */
@ExtendWith(OutputCaptureExtension.class)
class ErroresIT extends IntegracionBase {

    @Autowired
    private InspectorAnmatRepository inspectorAnmatRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private GlobalExceptionHandler globalExceptionHandler;

    @Test
    @DisplayName("GTIN ya registrado → 409 MEDICAMENTO_DUPLICADO")
    void medicamentoDuplicado() throws Exception {
        EscenarioIntegracion.Actores actores = escenario.actores(Provincia.SAN_LUIS);
        String gtin = escenario.medicamento(actores).getGtin();
        Map<String, Object> pedido = Map.of("gtin", gtin, "nombreComercial", "Copia", "principioActivo", "X",
                "concentracion", "1 mg", "formaFarmaceutica", "Comprimido", "presentacion", "Caja x 10",
                "biologico", false);

        mockMvc.perform(post("/api/medicamentos")
                        .header("Authorization", bearer(actores.adminLaboratorio().getEmail(), EscenarioIntegracion.CLAVE))
                        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(pedido)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.regla").value("MEDICAMENTO_DUPLICADO"));
    }

    @Test
    @DisplayName("Inspector con DNI ya usado → 409 INSPECTOR_DUPLICADO por la API, y el DNI no aparece en el log")
    void inspectorDuplicadoPorLaApi(CapturedOutput salida) throws Exception {
        InspectorAnmat existente = escenario.inspector(Provincia.SAN_LUIS);
        Map<String, Object> pedido = Map.of("legajo", "OTRO-LEGAJO", "dni", existente.getDni(),
                "provincia", "SAN_LUIS", "email", "otro.inspector@integracion.test",
                "password", EscenarioIntegracion.CLAVE, "nombre", "Otro", "apellido", "Inspector");

        mockMvc.perform(post("/api/inspectores-anmat")
                        .header("Authorization", bearer(escenario.sede().getEmail(), "clave-falsa-de-la-sede"))
                        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(pedido)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.regla").value("INSPECTOR_DUPLICADO"));

        assertFalse(salida.getAll().contains(existente.getDni()), "el DNI no aparece en el log");
    }

    @Test
    @DisplayName("DNI duplicado que llega a PostgreSQL (carrera): 409 INSPECTOR_DUPLICADO y ni el handler ni Hibernate loguean el DNI")
    void duplicadoEnLaBaseSinValorEnElLog(CapturedOutput salida) {
        InspectorAnmat existente = escenario.inspector(Provincia.SAN_LUIS);
        Usuario alta = escenario.sede();
        TransactionTemplate transaccion = new TransactionTemplate(transactionManager);

        DataIntegrityViolationException violacion = assertThrows(DataIntegrityViolationException.class,
                () -> transaccion.executeWithoutResult(estado -> {
                    Usuario cuenta = usuarioRepository.save(new Usuario("carrera@integracion.test", "hash",
                            "Carrera", "Dni", existente.getDni(), RolUsuario.INSPECTOR));
                    InspectorAnmat duplicado = new InspectorAnmat("LEGAJO-CARRERA", existente.getDni(),
                            Provincia.SAN_LUIS);
                    duplicado.setUsuario(cuenta);
                    duplicado.setUsuarioAlta(alta);
                    inspectorAnmatRepository.saveAndFlush(duplicado);
                }));
        ResponseEntity<ErrorResponseDTO> respuesta = globalExceptionHandler.handleDataIntegrityViolationException(violacion);

        assertEquals(409, respuesta.getStatusCode().value());
        assertEquals("INSPECTOR_DUPLICADO", respuesta.getBody().getRegla());
        assertFalse(respuesta.getBody().getMessage().contains(existente.getDni()), "la respuesta no tiene el DNI");
        assertFalse(salida.getAll().contains(existente.getDni()),
                "ningún log (handler, Hibernate, driver) tiene el DNI duplicado");
    }
}
