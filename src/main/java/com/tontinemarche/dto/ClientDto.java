package com.tontinemarche.dto;

import com.tontinemarche.domain.enums.StatutEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class ClientDto {
    private Long id;
    private String code;
    @NotBlank
    private String nomComplet;
    @NotBlank
    private String telephone;
    private String email;
    private String personneAContacter;
    private String telephoneSecondaire;
    private String adresse;
    private String profession;
    private String photoUrl;
    private String signatureReference;
    @NotNull
    private Long agenceId;
    private String agenceNom;
    private String agenceTelephone;
    private String agenceEmail;
    private String agenceAdresse;
    private String agenceVille;
    private Long marcheId;
    private String marcheNom;
    private String marcheCode;
    private Long agentId;
    private String agentNom;
    private String agentTelephone;
    private BigDecimal montantJournalier;
    private BigDecimal fraisAdhesion;
    private LocalDate dateAdhesion;
    private BigDecimal soldeEpargne;
    private StatutEntity statut;
    /** Solde épargne / montant journalier (calculé). */
    private BigDecimal nombreJoursPayes;
    /** Prochain retrait mensuel selon la date d'adhésion (calculé). */
    private LocalDate dateProbableRetrait;
    /** Commission agence estimée sur le solde actuel (calculé). */
    private BigDecimal commissionEstimee;
}
