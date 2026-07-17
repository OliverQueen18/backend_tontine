package com.tontinemarche.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class SimulationClientDto {
    private Long clientId;
    private String clientCode;
    private String clientNom;
    private Long agenceId;
    private String agenceNom;
    private BigDecimal montantJournalier;
    private BigDecimal collecteSimulee;
    private BigDecimal commissionAgence;
    private String trancheLabel;
}
