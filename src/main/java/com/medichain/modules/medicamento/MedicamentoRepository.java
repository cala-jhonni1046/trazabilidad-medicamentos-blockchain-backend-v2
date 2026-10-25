package com.medichain.modules.medicamento;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repositorio MedicamentoRepository en MediChain.
 * Acceso a datos JPA para Medicamento (CRUD y paginación heredados de
 * JpaRepository por UUID), más el alcance de lectura por rol: un laboratorio
 * ve solo los suyos; una distribuidora o farmacia, los de los laboratorios
 * con los que tiene un circuito APROBADO.
 */
@Repository
public interface MedicamentoRepository extends JpaRepository<Medicamento, UUID> {

    /** Medicamento por GTIN (normalizado, 14 dígitos). */
    Optional<Medicamento> findByGtin(String gtin);

    /** Indica si ya hay un medicamento con ese GTIN (el GTIN es único en el sistema). */
    boolean existsByGtin(String gtin);

    /** Medicamentos de un laboratorio (rol LABORATORIO: solo los suyos). */
    Page<Medicamento> findByLaboratorioId(UUID laboratorioId, Pageable pageable);

    /**
     * Medicamentos visibles para una distribuidora o farmacia: los de los
     * laboratorios con un circuito APROBADO con esa empresa. Una sola
     * subconsulta sobre enlaces_cuit por laboratorio_id (sin pasar por lotes
     * ni bultos), así el catálogo no arranca vacío y la consulta no encadena
     * saltos.
     */
    @Query("SELECT m FROM Medicamento m WHERE m.laboratorio.id IN "
            + "(SELECT e.laboratorio.id FROM EnlaceCuit e "
            + "WHERE e.estado = com.medichain.modules.enlacecuit.EstadoEnlaceCuit.APROBADO "
            + "AND (e.distribuidor.id = :empresaId OR e.farmacia.id = :empresaId))")
    Page<Medicamento> findVisiblesParaEmpresa(@Param("empresaId") UUID empresaId, Pageable pageable);

    /**
     * Indica si ese medicamento es visible para la distribuidora o farmacia
     * (su laboratorio tiene un circuito APROBADO con la empresa). Lo usa
     * getById para devolver 404 cuando está fuera del alcance del rol.
     */
    @Query("SELECT (COUNT(m) > 0) FROM Medicamento m WHERE m.id = :id AND m.laboratorio.id IN "
            + "(SELECT e.laboratorio.id FROM EnlaceCuit e "
            + "WHERE e.estado = com.medichain.modules.enlacecuit.EstadoEnlaceCuit.APROBADO "
            + "AND (e.distribuidor.id = :empresaId OR e.farmacia.id = :empresaId))")
    boolean existsVisibleParaEmpresa(@Param("id") UUID id, @Param("empresaId") UUID empresaId);
}
