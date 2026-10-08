package com.medichain.utils;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test unitario RestriccionUnicaTest en MediChain.
 * Reconoce la restricción violada por el nombre que trae Hibernate
 * (getConstraintName) o, si no lo trae, por el mensaje de PostgreSQL en la
 * cadena de causas; una restricción distinta no se confunde.
 */
class RestriccionUnicaTest {

    @Test
    @DisplayName("Nombre de la restricción en la causa de Hibernate (getConstraintName) → la reconoce")
    void porNombreDeHibernate() {
        DataIntegrityViolationException excepcion = new DataIntegrityViolationException("could not execute statement",
                new ConstraintViolationException("insert", new SQLException("duplicate key"), "ux_circuito_par_vigente"));

        assertTrue(RestriccionUnica.es(excepcion, "ux_circuito_par_vigente"));
        assertFalse(RestriccionUnica.es(excepcion, "ux_lote_laboratorio_codigo"));
    }

    @Test
    @DisplayName("Sin nombre en Hibernate: lo busca en el mensaje de PostgreSQL de la cadena de causas")
    void porMensajeDePostgresql() {
        SQLException postgres = new SQLException(
                "ERROR: duplicate key value violates unique constraint \"ux_unidad_gtin_serie\"");
        DataIntegrityViolationException excepcion = new DataIntegrityViolationException("could not execute batch",
                new RuntimeException("batch", postgres));

        assertTrue(RestriccionUnica.es(excepcion, "ux_unidad_gtin_serie"));
    }

    @Test
    @DisplayName("Otra restricción (por ejemplo una FK o el CUIT único) → no la confunde")
    void otraRestriccion() {
        DataIntegrityViolationException excepcion = new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"empresas_cuit_key\"");

        assertFalse(RestriccionUnica.es(excepcion, "ux_circuito_par_vigente"));
    }
}
