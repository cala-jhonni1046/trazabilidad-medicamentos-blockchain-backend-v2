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
 * no se recibe y no se dispensa. Devuelve un Bloqueo (causa en código +
 * mensaje); las versiones en bloque (lotes, bultos, cajas) hacen una consulta
 * por grupo, no una por elemento, para los listados paginados (sin N+1).
 * Es el ÚNICO lugar donde se calcula R10.
 */
@Service
public class EvaluadorBloqueo {

    private final CuarentenaRepository cuarentenaRepository;

    @Autowired
    public EvaluadorBloqueo(CuarentenaRepository cuarentenaRepository) {
        this.cuarentenaRepository = cuarentenaRepository;
    }

    /** Bloqueo del lote (vencido, CUARENTENA, RECALL o medida vigente de alcance LOTE), o vacío si no lo está. */
    @Transactional(readOnly = true)
    public Optional<Bloqueo> bloqueoDeLote(Lote lote) {
        return Optional.ofNullable(bloqueosDeLotes(List.of(lote)).get(lote.getId()));
    }

    /**
     * Evalúa varios lotes de una vez, con UNA consulta de medidas vigentes
     * para todos. Devuelve id del lote → bloqueo, solo de los bloqueados.
     */
    @Transactional(readOnly = true)
    public Map<UUID, Bloqueo> bloqueosDeLotes(Collection<Lote> lotes) {
        Map<UUID, Bloqueo> bloqueados = new LinkedHashMap<>();
        if (lotes.isEmpty()) {
            return bloqueados;
        }
        Set<UUID> loteIds = new HashSet<>();
        for (Lote lote : lotes) {
            loteIds.add(lote.getId());
        }
        Set<UUID> lotesConMedida = new HashSet<>(cuarentenaRepository.findLotesConMedidaVigente(loteIds));
        for (Lote lote : lotes) {
            Optional<Bloqueo> bloqueo = bloqueoDeLote(lote, lotesConMedida);
            bloqueo.ifPresent(b -> bloqueados.put(lote.getId(), b));
        }
        return bloqueados;
    }

    /**
     * Evalúa varios bultos de una vez: hereda el bloqueo de su lote y suma el
     * propio (medida vigente de alcance DESPACHO o BULTO). Dos consultas para
     * todo el grupo. Devuelve id del bulto → bloqueo, solo de los bloqueados.
     */
    @Transactional(readOnly = true)
    public Map<UUID, Bloqueo> bloqueosDeBultos(Collection<Bulto> bultos) {
        Map<UUID, Bloqueo> bloqueados = new LinkedHashMap<>();
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
            Optional<Bloqueo> delLote = bloqueoDeLote(bulto.getLote(), lotesConMedida);
            if (delLote.isPresent()) {
                bloqueados.put(bulto.getId(), delLote.get());
            } else if (bultosConMedida.contains(bulto.getId())) {
                bloqueados.put(bulto.getId(), new Bloqueo(CausaBloqueo.BULTO_CON_MEDIDA_VIGENTE,
                        "el bulto " + bulto.getCodigo() + " tiene una cuarentena o recall vigente"));
            }
        }
        return bloqueados;
    }

    /**
     * Evalúa varias cajas de una vez (listados paginados): la caja en un bulto
     * hereda el bloqueo del bulto (que ya incluye el de su lote, R6); la caja
     * sin bulto, el de su lote. Pocas consultas para todo el grupo, sin N+1.
     * Devuelve id de la caja → bloqueo, solo de las bloqueadas.
     */
    @Transactional(readOnly = true)
    public Map<UUID, Bloqueo> bloqueosDeCajas(Collection<UnidadTrazable> cajas) {
        Map<UUID, Bulto> bultos = new LinkedHashMap<>();
        Map<UUID, Lote> lotesSinBulto = new LinkedHashMap<>();
        for (UnidadTrazable caja : cajas) {
            if (caja.getBulto() != null) {
                bultos.put(caja.getBulto().getId(), caja.getBulto());
            } else {
                lotesSinBulto.put(caja.getLote().getId(), caja.getLote());
            }
        }
        Map<UUID, Bloqueo> porBulto = bloqueosDeBultos(bultos.values());
        Map<UUID, Bloqueo> porLote = bloqueosDeLotes(lotesSinBulto.values());
        Map<UUID, Bloqueo> bloqueadas = new LinkedHashMap<>();
        for (UnidadTrazable caja : cajas) {
            Bloqueo bloqueo = caja.getBulto() != null
                    ? porBulto.get(caja.getBulto().getId())
                    : porLote.get(caja.getLote().getId());
            if (bloqueo != null) {
                bloqueadas.put(caja.getId(), bloqueo);
            }
        }
        return bloqueadas;
    }

    /** Bloqueo de una caja (R10): hereda el de su lote y, si está en un bulto, el de su bulto. Vacío si no lo está. */
    @Transactional(readOnly = true)
    public Optional<Bloqueo> bloqueoDeCaja(UnidadTrazable caja) {
        return Optional.ofNullable(bloqueosDeCajas(List.of(caja)).get(caja.getId()));
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

    /**
     * Bloqueo de un lote dado el conjunto de lotes con medida vigente (ya
     * consultado): primero lo propio del lote (vencido, CUARENTENA, RECALL) y
     * después la medida vigente de alcance LOTE.
     */
    private Optional<Bloqueo> bloqueoDeLote(Lote lote, Set<UUID> lotesConMedida) {
        if (lote.estaVencido()) {
            return Optional.of(new Bloqueo(CausaBloqueo.LOTE_VENCIDO, "el lote " + lote.getCodigo() + " está vencido"));
        }
        if (lote.getEstado() == EstadoLote.CUARENTENA) {
            return Optional.of(new Bloqueo(CausaBloqueo.LOTE_EN_CUARENTENA,
                    "el lote " + lote.getCodigo() + " está en CUARENTENA"));
        }
        if (lote.getEstado() == EstadoLote.RECALL) {
            return Optional.of(new Bloqueo(CausaBloqueo.LOTE_EN_RECALL, "el lote " + lote.getCodigo() + " está en RECALL"));
        }
        if (lotesConMedida.contains(lote.getId())) {
            return Optional.of(new Bloqueo(CausaBloqueo.LOTE_CON_MEDIDA_VIGENTE,
                    "el lote " + lote.getCodigo() + " tiene una medida sanitaria vigente"));
        }
        return Optional.empty();
    }
}
