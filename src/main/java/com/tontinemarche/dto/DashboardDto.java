package com.tontinemarche.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class DashboardDto {
    private String scopeLabel;
    private LocalDate periodeDebut;
    private LocalDate periodeFin;
    private boolean vueAgent;
    private long nombreAgences;
    private long nombreAgents;
    private long nombreClients;
    private BigDecimal collectesJour;
    private BigDecimal collectesMois;
    private BigDecimal collectesPeriode;
    private BigDecimal beneficeGlobal;
    private BigDecimal commissionAdmin;
    private long nombreSignatures;
    private BigDecimal soldeCaisse;
    private List<CollecteDto> dernieresCollectes;
    private List<RestitutionDto> dernieresRestitutions;
    private List<DepenseDto> dernieresDepenses;
    private BigDecimal montantCommissionsPeriode;
    private BigDecimal montantDepensesPeriode;
    private BigDecimal montantOperationsEntree;
    private BigDecimal montantOperationsSortie;
    private List<Map<String, Object>> topAgents;
    private List<Map<String, Object>> evolutionCollectes;
}
