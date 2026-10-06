package com.medichain.modules.trazabilidad;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/**
 * Servicio VerificadorCadena en MediChain.
 * Recorre toda la cadena en bloques de 500 eventos, en orden de número,
 * y comprueba para cada uno: que el número sea el siguiente (sin huecos),
 * que hashAnterior sea el hash del evento previo (GENESIS en el primero)
 * y que el hash recalculado coincida con el guardado. Al final compara
 * con CadenaEstado. Se detiene en el primer problema.
 */
@Service
public class VerificadorCadena {

    /** Tamaño del bloque de lectura. */
    public static final int TAMANIO_BLOQUE = 500;

    private final EventoTrazabilidadRepository eventoRepository;
    private final CadenaEstadoRepository cadenaEstadoRepository;

    @Autowired
    public VerificadorCadena(EventoTrazabilidadRepository eventoRepository,
                             CadenaEstadoRepository cadenaEstadoRepository) {
        this.eventoRepository = eventoRepository;
        this.cadenaEstadoRepository = cadenaEstadoRepository;
    }

    /** Verifica la cadena completa y devuelve el resultado. */
    @Transactional(readOnly = true)
    public VerificacionCadenaResponseDTO verificar() {
        long numeroEsperado = 1;
        String hashEsperado = EventoTrazabilidad.GENESIS;
        long verificados = 0;
        List<EventoTrazabilidad> bloque = eventoRepository.findByNumeroGreaterThanOrderByNumeroAsc(
                0L, Limit.of(TAMANIO_BLOQUE));
        while (!bloque.isEmpty()) {
            for (EventoTrazabilidad evento : bloque) {
                if (evento.getNumero() != numeroEsperado) {
                    return roto(verificados, numeroEsperado,
                            "Falta el evento " + numeroEsperado + " (siguiente encontrado: " + evento.getNumero() + ")");
                }
                if (!hashEsperado.equals(evento.getHashAnterior())) {
                    return roto(verificados, evento.getNumero(),
                            "hashAnterior no coincide con el hash del evento anterior");
                }
                if (!evento.calcularHash().equals(evento.getHash())) {
                    return roto(verificados, evento.getNumero(),
                            "El hash recalculado no coincide: el contenido del evento fue alterado");
                }
                hashEsperado = evento.getHash();
                numeroEsperado++;
                verificados++;
            }
            Long ultimo = bloque.get(bloque.size() - 1).getNumero();
            bloque = eventoRepository.findByNumeroGreaterThanOrderByNumeroAsc(ultimo, Limit.of(TAMANIO_BLOQUE));
        }
        CadenaEstado estado = cadenaEstadoRepository.findFirstByNombre(CadenaEstado.PRINCIPAL).orElse(null);
        if (estado == null) {
            return roto(verificados, null, "Falta la fila de estado de la cadena");
        }
        if (estado.getUltimoNumero() != verificados || !estado.getUltimoHash().equals(hashEsperado)) {
            return roto(verificados, verificados + 1,
                    "El estado de la cadena (ultimo " + estado.getUltimoNumero()
                            + ") no coincide con los eventos guardados (" + verificados + ")");
        }
        return new VerificacionCadenaResponseDTO(true, verificados, null, null);
    }

    /** Arma un resultado de cadena rota. */
    private VerificacionCadenaResponseDTO roto(long verificados, Long numero, String motivo) {
        return new VerificacionCadenaResponseDTO(false, verificados, numero, motivo);
    }
}
