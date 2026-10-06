package com.medichain.modules.cuarentena;

import com.medichain.modules.bulto.Bulto;
import com.medichain.modules.lote.EstadoLote;
import com.medichain.modules.lote.Lote;
import com.medichain.modules.unidadtrazable.UnidadTrazable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Servicio EvaluadorBloqueo en MediChain (R10).
 * "Bloqueado" se calcula, no se guarda. Un lote está bloqueado si venció,
 * si su estado es CUARENTENA o RECALL, o si tiene una medida VIGENTE de
 * alcance LOTE. Un bulto está bloqueado si su lote lo está o si figura en
 * una medida VIGENTE (alcance DESPACHO —ruptura de frío o robo— o BULTO).
 * Una caja hereda el bloqueo de su lote y de su bulto. Bloqueado no viaja,
 * no se recibe y no se dispensa. Las versiones en bloque hacen una consulta
 * por grupo (no una por bulto) para no caer en N+1.
 */
@Service
public class EvaluadorBloqueo {

    private final CuarentenaRepository cuarentenaRepository;

    @Autowired
    public EvaluadorBloqueo(CuarentenaRepository cuarentenaRepository) {
        this.cuarentenaRepository = cuarentenaRepository;
    }

    /** Devuelve el motivo por el que el lote está bloqueado, o vacío si no lo está. */
    @Transactional(readOnly = true)
    public Optional<String> bloqueoDeLote(Lote lote) {
        Optional<String> propio = bloqueoPropioDeLote(lote);
        if (propio.isPresent()) {
            return propio;
        }
        if (!cuarentenaRepository.findLotesConMedidaVigente(List.of(lote.getId())).isEmpty()) {
            return Optional.of("el lote " + lote.getCodigo() + " tiene una medida sanitaria vigente");
        }
        return Optional.empty();
    }

    /**
     * Evalúa varios bultos de una vez. Devuelve, para cada bulto bloqueado,
     * el motivo (código del bulto → motivo). Vacío si ninguno lo está.
     */
    @Transactional(readOnly = true)
    public Map<String, String> bultosBloqueados(Collection<Bulto> bultos) {
        Map<String, String> bloqueados = new LinkedHashMap<>();
        if (bultos.isEmpty()) {
            return bloqueados;
        }
        Set<UUID> loteIds = new HashSet<>();
        Set<UUID> bultoIds = new HashSet<>();
        for (Bulto bulto : bultos) {
            loteIds.add(bulto.getLote().getId());
            bultoIds.add(bulto.getId());
        }
        Set<UUID> lotesConMedida = new HashSet<>(cuarentenaRepository.findLotesConMedidaVigente(loteIds));
        Set<UUID> bultosConMedida = new HashSet<>(cuarentenaRepository.findBultosConMedidaVigente(bultoIds));
        for (Bulto bulto : bultos) {
            Optional<String> delLote = bloqueoPropioDeLote(bulto.getLote());
            if (delLote.isPresent()) {
                bloqueados.put(bulto.getCodigo(), delLote.get());
            } else if (lotesConMedida.contains(bulto.getLote().getId())) {
                bloqueados.put(bulto.getCodigo(), "el lote " + bulto.getLote().getCodigo() + " tiene una medida sanitaria vigente");
            } else if (bultosConMedida.contains(bulto.getId())) {
                bloqueados.put(bulto.getCodigo(), "el bulto tiene una cuarentena o recall vigente");
            }
        }
        return bloqueados;
    }

    /**
     * Motivo por el que una caja está bloqueada (R10), o vacío: hereda el
     * bloqueo de su lote y, si está en un bulto, el de su bulto.
     */
    @Transactional(readOnly = true)
    public Optional<String> bloqueoDeCaja(UnidadTrazable caja) {
        if (caja.getBulto() != null) {
            Map<String, String> bloqueados = bultosBloqueados(List.of(caja.getBulto()));
            if (!bloqueados.isEmpty()) {
                return Optional.of(bloqueados.values().iterator().next());
            }
            return Optional.empty();
        }
        return bloqueoDeLote(caja.getLote());
    }

    /**
     * Indica si la caja está en RETIRO DEL MERCADO (recall): su lote está en
     * RECALL o su bulto figura en una medida CONVERTIDA_EN_RECALL. Sirve para
     * el mensaje de la verificación pública (distinto del de cuarentena).
     */
    @Transactional(readOnly = true)
    public boolean esRetiroDelMercado(UnidadTrazable caja) {
        if (caja.getLote().getEstado() == EstadoLote.RECALL) {
            return true;
        }
        return caja.getBulto() != null
                && !cuarentenaRepository.findBultosEnRecall(List.of(caja.getBulto().getId())).isEmpty();
    }

    /** Ids de los bultos dados que ya figuran en una medida vigente (para no duplicar cuarentenas). */
    @Transactional(readOnly = true)
    public Set<UUID> bultosConMedidaVigente(Collection<UUID> bultoIds) {
        if (bultoIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(cuarentenaRepository.findBultosConMedidaVigente(bultoIds));
    }

    /** Bloqueo que surge del propio lote (sin consultar medidas): vencido, CUARENTENA o RECALL. */
    private Optional<String> bloqueoPropioDeLote(Lote lote) {
        if (lote.estaVencido()) {
            return Optional.of("el lote " + lote.getCodigo() + " está vencido");
        }
        if (lote.getEstado() == EstadoLote.CUARENTENA || lote.getEstado() == EstadoLote.RECALL) {
            return Optional.of("el lote " + lote.getCodigo() + " está en " + lote.getEstado());
        }
        return Optional.empty();
    }
}
