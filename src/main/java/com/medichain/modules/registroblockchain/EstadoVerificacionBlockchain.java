package com.medichain.modules.registroblockchain;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Enumeración EstadoVerificacionBlockchain en MediChain (R15).
 * Resultado de comparar la cadena local con los anclajes del contrato:
 * VERIFICADA (todos coinciden), ALTERADA (algún evento anclado no coincide
 * o falta), NO_CONSULTADA (la red no respondió: no es lo mismo que
 * alterada) y NO_DISPONIBLE (anclaje deshabilitado).
 */
@Schema(enumAsRef = true, description = "Resultado de comparar la cadena local con los anclajes del contrato: VERIFICADA (todos coinciden), ALTERADA (algún evento anclado no coincide o falta), NO_CONSULTADA (la red no respondió: no es lo mismo que alterada) y NO_DISPONIBLE (anclaje deshabilitado).")
public enum EstadoVerificacionBlockchain {
    VERIFICADA,
    ALTERADA,
    NO_CONSULTADA,
    NO_DISPONIBLE
}
