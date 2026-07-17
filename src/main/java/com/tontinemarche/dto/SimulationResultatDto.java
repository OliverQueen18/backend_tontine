package com.tontinemarche.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class SimulationResultatDto {
    private LocalDate debut;
    private LocalDate fin;
    private long nombreJours;
    private Long agenceId;
    private String agenceNom;
    private BigDecimal tauxCommissionAdmin;
    private long nombreClients;
    private BigDecimal totalMisesJournalieres;
    private BigDecimal totalCollectesSimulees;
    /** Bénéfice agence = somme des commissions grille (ou taux) par client. */
    private BigDecimal beneficeAgence;
    /** Commission administrateur = total collectes simulées × tauxCommissionAdmin. */
    private BigDecimal commissionAdmin;
    private List<SimulationAgenceDto> parAgence;
    private List<SimulationClientDto> clients;
}
