package com.medichain.integracion;

import com.medichain.config.DatosIniciales;
import com.medichain.modules.auth.LoginRequestDTO;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base de los tests de integración (*IT) de MediChain (paso 9).
 * <ul>
 *   <li><b>PostgreSQL real en Docker</b> (Testcontainers), UN contenedor para
 *       toda la suite: se arranca una vez por JVM (campo estático) y
 *       Testcontainers lo borra al terminar. @ServiceConnection hace que la
 *       app use ese contenedor y tiene prioridad sobre spring.datasource.*:
 *       aunque haya un DB_URL en el entorno, nunca se toca otra base.</li>
 *   <li><b>UN contexto de Spring</b> para todas las clases: todas heredan esta
 *       configuración sin @MockitoBean ni propiedades propias, así Spring lo
 *       reutiliza (arrancarlo cuesta ~8 s).</li>
 *   <li><b>Perfil test</b> (application-test.properties, valores falsos) y anclaje
 *       apagado, puestos acá: estas propiedades le ganan a cualquier variable de
 *       entorno (por ejemplo un SPRING_PROFILES_ACTIVE=demo del .env).</li>
 *   <li><b>Base limpia antes de cada test</b> (LimpiadorBase: TRUNCATE, cadena en
 *       GENESIS, secuencias en 1) y la Sede inicial recreada. NUNCA @Transactional
 *       en un IT: el rollback ocultaría justo lo que se prueba (REQUIRES_NEW,
 *       concurrencia, el bloqueo de la cadena, propagation MANDATORY).</li>
 *   <li><b>MockMvc con la cadena de filtros real</b> (JWT real, @PreAuthorize real).</li>
 * </ul>
 * Sin Docker la suite falla con un mensaje claro (./mvnw verify -DskipITs para omitirla).
 */
@SpringBootTest(properties = {"spring.profiles.active=test", "medichain.anclaje.habilitado=false"})
@AutoConfigureMockMvc
@Import(EscenarioIntegracion.class)
public abstract class IntegracionBase {

    /** Imagen fijada: misma versión mayor que la base del proyecto (PostgreSQL 16). */
    public static final String IMAGEN_POSTGRES = "postgres:16.15-alpine";

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = iniciarPostgres();

    protected static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected EscenarioIntegracion escenario;

    @Autowired
    private DatosIniciales datosIniciales;

    /** Base limpia y Sede inicial recreada antes de cada test. */
    @BeforeEach
    void prepararBase() {
        LimpiadorBase.limpiar(jdbc);
        datosIniciales.run();
    }

    /** Login real (POST /api/auth/login, BCrypt) y devuelve el JWT. */
    protected String token(String email, String password) throws Exception {
        String respuesta = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(new LoginRequestDTO(email, password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(respuesta).get("token").asString();
    }

    /** Encabezado Authorization con el token del usuario. */
    protected String bearer(String email, String password) throws Exception {
        return "Bearer " + token(email, password);
    }

    /** Lee un JSON de respuesta. */
    protected static JsonNode json(String texto) {
        return JSON.readTree(texto);
    }

    /** Arranca el contenedor una sola vez; sin Docker, falla con un mensaje claro. */
    private static PostgreSQLContainer iniciarPostgres() {
        if (!DockerClientFactory.instance().isDockerAvailable()) {
            throw new IllegalStateException("Los tests de integración (*IT) necesitan Docker para levantar "
                    + "PostgreSQL con Testcontainers, y no se encontró Docker. Arrancá Docker o, para omitirlos, "
                    + "corré: ./mvnw verify -DskipITs");
        }
        PostgreSQLContainer contenedor = new PostgreSQLContainer(IMAGEN_POSTGRES);
        contenedor.start();
        return contenedor;
    }
}
