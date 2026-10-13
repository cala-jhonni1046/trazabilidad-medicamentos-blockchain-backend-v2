package com.medichain.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test unitario BusquedaTextoTest en MediChain (B12e).
 * El patrón LIKE de las búsquedas por código o serie: "contiene", en minúsculas y con los
 * comodines escapados; sin texto, "%" (todo).
 */
class BusquedaTextoTest {

    @Test
    @DisplayName("Sin texto (null o en blanco) busca todo: %")
    void sinTextoBuscaTodo() {
        assertEquals("%", BusquedaTexto.patron(null));
        assertEquals("%", BusquedaTexto.patron(""));
        assertEquals("%", BusquedaTexto.patron("   "));
    }

    @Test
    @DisplayName("Con texto: contiene, en minúsculas y sin espacios de los extremos")
    void contieneEnMinusculas() {
        assertEquals("%l2026-0010%", BusquedaTexto.patron("  L2026-0010 "));
    }

    @Test
    @DisplayName("Los comodines de LIKE y la barra se escapan: se busca lo que se escribió")
    void escapaComodines() {
        assertEquals("%a\\%b\\_c\\\\d%", BusquedaTexto.patron("A%B_C\\D"));
    }
}
