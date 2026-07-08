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
    private Long agenceId;
    private BigDecimal totalCollecte;
    private BigDecimal commission;
    private BigDecimal commissionCalculee;
    private BigDecimal montantNet;
    private LocalDateTime dateHeure;
    private String signatureClient;
    private boolean validee;
}
