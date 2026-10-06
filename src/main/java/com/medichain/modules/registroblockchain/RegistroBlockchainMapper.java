package com.medichain.modules.registroblockchain;

import org.springframework.stereotype.Component;

/**
 * Mapper RegistroBlockchainMapper en MediChain.
 * Convierte la entidad RegistroBlockchain en su DTO de salida, sin
 * lógica de negocio (los anclajes los crea el proceso interno, no la API).
 */
@Component
public class RegistroBlockchainMapper {

    /** Convierte una entidad RegistroBlockchain en su DTO de salida. */
    public RegistroBlockchainResponseDTO toResponseDTO(RegistroBlockchain entity) {
        if (entity == null) {
            return null;
        }
        RegistroBlockchainResponseDTO dto = new RegistroBlockchainResponseDTO();
        dto.setId(entity.getId());
        dto.setFechaCreacion(entity.getFechaCreacion());
        dto.setFechaActualizacion(entity.getFechaActualizacion());
        dto.setVersion(entity.getVersion());
        dto.setDesdeNumero(entity.getDesdeNumero());
        dto.setHastaNumero(entity.getHastaNumero());
        dto.setHashAnclado(entity.getHashAnclado());
        dto.setRed(entity.getRed());
        dto.setDireccionContrato(entity.getDireccionContrato());
        dto.setTransactionHash(entity.getTransactionHash());
        dto.setNonce(entity.getNonce());
        dto.setBloque(entity.getBloque());
        dto.setConfirmaciones(entity.getConfirmaciones());
        dto.setGasUsado(entity.getGasUsado());
        dto.setCostoEth(entity.costoEth() == null ? null : entity.costoEth().toPlainString());
        dto.setEstado(entity.getEstado());
        dto.setIntentos(entity.getIntentos());
        dto.setUltimoError(entity.getUltimoError());
        dto.setProximoIntento(entity.getProximoIntento());
        dto.setFechaEnvio(entity.getFechaEnvio());
        dto.setFechaConfirmacion(entity.getFechaConfirmacion());
        dto.setEnlaceEtherscan(entity.enlaceEtherscan());
        return dto;
    }
}
