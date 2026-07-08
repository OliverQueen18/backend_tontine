package com.tontinemarche.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import com.tontinemarche.domain.enums.SensOperation;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class DepenseDto {
    private Long id;
    @NotNull
    private Long agenceId;
    private LocalDate dateDepense;
    @NotBlank
    private String categorie;
    private SensOperation sens;
    @NotNull
    @Positive
    private BigDecimal montant;
    private String justificatifUrl;
    private String observation;
    private Long agentId;
    private String agentNom;
    private Long clientId;
    private String clientNom;
    private boolean validee;
}
