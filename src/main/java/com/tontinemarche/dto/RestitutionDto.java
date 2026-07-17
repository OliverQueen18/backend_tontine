package com.tontinemarche.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class RestitutionDto {
    private Long id;
    private String numeroRecu;
    @NotNull
    private Long clientId;
    private String clientCode;
    private String clientNom;
    private String clientTelephone;
    private String clientEmail;
    private BigDecimal montantJournalier;
    private String marcheNom;
    private Long agenceId;
    private String agenceNom;
    private String agenceTelephone;
    private String agenceEmail;
    private String agenceAdresse;
    private String agenceVille;
    private Long agentId;
    private String agentNom;
    private String agentTelephone;
    private BigDecimal totalCollecte;
    private BigDecimal commission;
    private BigDecimal commissionCalculee;
    private BigDecimal montantNet;
    private LocalDateTime dateHeure;
    private String signatureClient;
    private boolean validee;
}
