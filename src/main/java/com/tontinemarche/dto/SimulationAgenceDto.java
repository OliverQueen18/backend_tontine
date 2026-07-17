package com.tontinemarche.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class SimulationAgenceDto {
    private Long agenceId;
    private String agenceNom;
    private BigDecimal tauxCommissionAdmin;
    private long nombreClients;
    private BigDecimal totalMisesJournalieres;
    private BigDecimal totalCollectesSimulees;
    private BigDecimal beneficeAgence;
    private BigDecimal commissionAdmin;
}
