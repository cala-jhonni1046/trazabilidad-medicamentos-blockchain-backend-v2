package com.medichain.modules.lote;

import com.medichain.modules.auth.UsuarioAutenticado;
import com.medichain.modules.medicamento.Medicamento;
import com.medichain.modules.trazabilidad.DatosEventos;
import com.medichain.modules.trazabilidad.RegistradorEventos;
import com.medichain.modules.trazabilidad.TipoEvento;
import com.medichain.modules.unidadtrazable.UnidadTrazableRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
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
 * También registra la CARRERA de series (registrarChoqueDeSeries): dos lotes
 * simultáneos con series repetidas pasan la validación y el índice único
 * rechaza al segundo; se registra igual que una serie ya existente.
 * TODO: rate limiting de intentos inválidos (junto a login y registro), para
 * que un laboratorio no infle la cadena a propósito.
 */
@Service
public class RegistroIntentos {

    /** Tamaño de cada consulta de series existentes y cantidad de ejemplos que van al evento. */
    private static final int TANDA_CONSULTA = 1000;
    private static final int MUESTRA_MAXIMA = 10;

    private final RegistradorEventos registradorEventos;
    private final UnidadTrazableRepository unidadTrazableRepository;

    @Autowired
    public RegistroIntentos(RegistradorEventos registradorEventos, UnidadTrazableRepository unidadTrazableRepository) {
        this.registradorEventos = registradorEventos;
        this.unidadTrazableRepository = unidadTrazableRepository;
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

    /**
     * Carrera de series (R3): otro lote con series del mismo GTIN entró al
     * mismo tiempo y el índice ux_unidad_gtin_serie rechazó este. En su propia
     * transacción (la del lote quedó abortada) busca qué series ya existen
     * ahora, registra INTENTO_SERIE_INVALIDA y devuelve esas series.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<String> registrarChoqueDeSeries(String codigoLote, Medicamento medicamento, List<String> series,
                                                String seriesHash, UsuarioAutenticado actor) {
        List<String> existentes = new ArrayList<>();
        for (int desde = 0; desde < series.size(); desde += TANDA_CONSULTA) {
            existentes.addAll(unidadTrazableRepository.findSeriesExistentes(medicamento.getGtin(),
                    series.subList(desde, Math.min(desde + TANDA_CONSULTA, series.size()))));
        }
        List<String> muestra = new ArrayList<>(existentes.subList(0, Math.min(MUESTRA_MAXIMA, existentes.size())));
        registradorEventos.registrar(TipoEvento.INTENTO_SERIE_INVALIDA, "Medicamento", medicamento.getId(),
                DatosEventos.intentoSerieInvalida(codigoLote, medicamento, series.size(), 0, 0, existentes.size(),
                        muestra, seriesHash), actor);
        return existentes;
    }
}
