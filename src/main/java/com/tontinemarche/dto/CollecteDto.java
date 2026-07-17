package com.tontinemarche.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class CollecteDto {
    private Long id;
    private String numeroRecu;
    @NotNull
    private Long clientId;
    private String clientCode;
    private String clientNom;
    private Long agentId;
    private String agentNom;
    private Long agenceId;
    private BigDecimal montantPrevu;
    private BigDecimal montantRecu;
    private BigDecimal nombreJoursPayes;
    private BigDecimal montantJournalier;
    private BigDecimal soldeEpargne;
    private String clientPhotoUrl;
    private LocalDate dateProbableRetrait;
    private LocalDate dateCollecte;
    private LocalDateTime dateHeure;
    private String signatureClient;
    private boolean validee;
    private boolean annulee;
}
