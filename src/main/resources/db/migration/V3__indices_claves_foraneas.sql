-- =============================================================================
-- V3__indices_claves_foraneas.sql · MediChain (paso 10)
--
-- PostgreSQL no crea índices sobre las claves foráneas. De las 45 FK, se indexan
-- las 29 columnas por las que la app BUSCA o hace JOIN (consultas de los
-- repositorios); cada índice dice cuál lo usa.
--
-- Sin índice (16), a propósito:
--   * ya cubiertas por la primera columna de otro índice:
--       lotes.laboratorio_id (ux_lote_laboratorio_codigo),
--       inspectores_anmat.usuario_id (uk_inspectores_anmat_usuario_id),
--       despacho_bulto.despacho_id (pk_despacho_bulto),
--       cuarentena_bulto.cuarentena_id (pk_cuarentena_bulto);
--   * nunca se busca por ellas (solo se leen desde la fila):
--       bultos.ubicacion_empresa_id, bultos.viaje_actual_id, cuarentenas.inspector_id,
--       despachos_logisticos.creado_por_id, dispensaciones.farmaceutico_id,
--       empresas.inspector_habilitador_id, enlaces_cuit.propuesto_por_id,
--       enlaces_cuit.inspector_aprobador_id, inspectores_anmat.usuario_alta_id,
--       lotes.liberado_por_id, recepciones.registrada_por_id,
--       reportes_ciudadanos.unidad_trazable_id.
--     No hay DELETE de negocio, así que tampoco hacen falta para borrar filas padre.
--   Si una consulta nueva empieza a buscar por una de ellas, su índice va en una V nueva.
--
-- NO EDITAR: Flyway compara checksums. Toda corrección va en una V nueva.
-- =============================================================================

-- empleados de una empresa (UsuarioRepository.findByEmpresaId)
CREATE INDEX ix_usuarios_empresa_id ON usuarios (empresa_id);

-- bandeja del inspector (EmpresaRepository.findBandeja, findByEstadoAndInspectorRevisorId)
CREATE INDEX ix_empresas_inspector_revisor_id ON empresas (inspector_revisor_id);

-- circuitos de un laboratorio (EnlaceCuitRepository.findByLaboratorioId, findByEstadoYEmpresa)
CREATE INDEX ix_enlaces_cuit_laboratorio_id ON enlaces_cuit (laboratorio_id);
-- circuitos de una distribuidora (findByDistribuidorId, findPendientesDeAceptacion)
CREATE INDEX ix_enlaces_cuit_distribuidor_id ON enlaces_cuit (distribuidor_id);
-- circuitos de una farmacia (findByFarmaciaId, findPendientesDeAceptacion)
CREATE INDEX ix_enlaces_cuit_farmacia_id ON enlaces_cuit (farmacia_id);
-- bandeja del inspector (EnlaceCuitRepository.findBandeja)
CREATE INDEX ix_enlaces_cuit_inspector_revisor_id ON enlaces_cuit (inspector_revisor_id);

-- medicamentos, lotes y cajas de un laboratorio (joins de findBy...MedicamentoLaboratorioId)
CREATE INDEX ix_medicamentos_laboratorio_id ON medicamentos (laboratorio_id);

-- lote → medicamento (LoteRepository.findByMedicamentoLaboratorioId, findBandejaLiberacion)
CREATE INDEX ix_lotes_medicamento_id ON lotes (medicamento_id);

-- cajas de un lote (findByLoteId, countByLoteId, findByLoteIdAndSerieIn, armado de bultos)
CREATE INDEX ix_unidades_trazables_lote_id ON unidades_trazables (lote_id);
-- cajas de un bulto (findByBultoId, findByBultoIdIn) y visibilidad por viaje
CREATE INDEX ix_unidades_trazables_bulto_id ON unidades_trazables (bulto_id);
-- stock de una empresa (findByEmpresaActualId, findVisiblesPara...)
CREATE INDEX ix_unidades_trazables_empresa_actual_id ON unidades_trazables (empresa_actual_id);

-- bultos de un lote (findByLoteLaboratorioId y visibilidad de lotes por viaje)
CREATE INDEX ix_bultos_lote_id ON bultos (lote_id);
-- bultos por circuito de destino (findByDestinoDistribuidorId, findByDestinoFarmaciaId)
CREATE INDEX ix_bultos_destino_id ON bultos (destino_id);

-- viajes de un bulto (DespachoLogisticoRepository.findSalidasDelBulto, visibilidad); despacho_id ya lo cubre la PK
CREATE INDEX ix_despacho_bulto_bulto_id ON despacho_bulto (bulto_id);

-- medidas vigentes sobre bultos, R10 (CuarentenaRepository.findBultosConMedidaVigente, findBultosEnRecall); cuarentena_id ya lo cubre la PK
CREATE INDEX ix_cuarentena_bulto_bulto_id ON cuarentena_bulto (bulto_id);

-- viajes de una empresa (findByOrigenId) y joins por origen de telemetría y cuarentenas
CREATE INDEX ix_despachos_logisticos_origen_id ON despachos_logisticos (origen_id);

-- lecturas de un viaje (TelemetriaTemperaturaRepository.findByDespachoId, findByDespachoOrigenId)
CREATE INDEX ix_telemetria_temperatura_despacho_id ON telemetria_temperatura (despacho_id);

-- lecturas de un viaje (TelemetriaGpsRepository.findByDespachoOrigenId)
CREATE INDEX ix_telemetria_gps_despacho_id ON telemetria_gps (despacho_id);

-- recepciones de un bulto (existsByBultoIdAndReceptoraId: BULTO_DUPLICADO; findByBultoIdOrderByFechaHoraAsc)
CREATE INDEX ix_recepciones_bulto_id ON recepciones (bulto_id);
-- recepciones de un viaje (RecepcionRepository.findByDespachoId)
CREATE INDEX ix_recepciones_despacho_id ON recepciones (despacho_id);
-- recepciones de una empresa (findByReceptoraId, findVisiblesParaEmpresa)
CREATE INDEX ix_recepciones_receptora_id ON recepciones (receptora_id);

-- R11 en cada dispensación (DispensacionRepository.existsByUnidadTrazableIdAndAnuladaFalse)
CREATE INDEX ix_dispensaciones_unidad_trazable_id ON dispensaciones (unidad_trazable_id);
-- dispensaciones de una farmacia (DispensacionRepository.findByFarmaciaId)
CREATE INDEX ix_dispensaciones_farmacia_id ON dispensaciones (farmacia_id);

-- reportes de un paciente (ReporteCiudadanoRepository.findByPacienteId)
CREATE INDEX ix_reportes_ciudadanos_paciente_id ON reportes_ciudadanos (paciente_id);
-- bandeja del inspector (ReporteCiudadanoRepository.findBandeja)
CREATE INDEX ix_reportes_ciudadanos_investiga_id ON reportes_ciudadanos (investiga_id);

-- R10 en cada evaluación de bloqueo (CuarentenaRepository.findLotesConMedidaVigente)
CREATE INDEX ix_cuarentenas_lote_id ON cuarentenas (lote_id);
-- medidas de un viaje (findByDespachoOrigenId, findVisiblesParaEmpresa)
CREATE INDEX ix_cuarentenas_despacho_id ON cuarentenas (despacho_id);
-- medida nacida de un reporte (CuarentenaRepository.findByReporteOrigenId)
CREATE INDEX ix_cuarentenas_reporte_origen_id ON cuarentenas (reporte_origen_id);
-- bandeja del inspector (CuarentenaRepository.findBandeja)
CREATE INDEX ix_cuarentenas_inspector_revisor_id ON cuarentenas (inspector_revisor_id);
