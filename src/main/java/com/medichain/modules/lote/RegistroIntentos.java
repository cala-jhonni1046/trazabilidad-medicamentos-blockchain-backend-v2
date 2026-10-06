package com.medichain.modules.lote;

import com.medichain.modules.auth.UsuarioAutenticado;
import com.medichain.modules.medicamento.Medicamento;
import com.medichain.modules.trazabilidad.DatosEventos;
import com.medichain.modules.trazabilidad.RegistradorEventos;
import com.medichain.modules.trazabilidad.TipoEvento;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/**
 * Servicio RegistroIntentos en MediChain.
 * Registra INTENTO_SERIE_INVALIDA (R3) en su PROPIA transacción
 * (REQUIRES_NEW): el evento queda en la cadena aunque la transacción del
 * lote haga rollback y el lote no se cree.
 * <ul>
 *   <li>Es un bean aparte de LoteService a propósito: una llamada interna
 *       (this.metodo) saltearía el proxy y REQUIRES_NEW no tendría efecto.</li>
 *   <li>Sin deadlock: LoteService lo llama ANTES de registrar cualquier
 *       evento propio, así que la transacción externa no tiene tomado el
 *       bloqueo de cadena_estado que pide la interna. No llamarlo desde una
 *       transacción que ya registró eventos.</li>
 * </ul>
 * TODO: rate limiting de intentos inválidos (junto a login y registro), para
 * que un laboratorio no infle la cadena a propósito.
 */
@Service
public class RegistroIntentos {

    private final RegistradorEventos registradorEventos;

    @Autowired
    public RegistroIntentos(RegistradorEventos registradorEventos) {
        this.registradorEventos = registradorEventos;
    }

    /** Registra el intento de lote con series inválidas, duplicadas o ya existentes. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarIntentoSerieInvalida(String codigoLote, Medicamento medicamento, int cantidadRecibida,
                                              int invalidas, int duplicadasEnLote, int yaExistentes,
                                              List<String> muestra, String seriesHash, UsuarioAutenticado actor) {
        registradorEventos.registrar(TipoEvento.INTENTO_SERIE_INVALIDA, "Medicamento", medicamento.getId(),
                DatosEventos.intentoSerieInvalida(codigoLote, medicamento, cantidadRecibida, invalidas,
                        duplicadasEnLote, yaExistentes, muestra, seriesHash), actor);
    }
}
