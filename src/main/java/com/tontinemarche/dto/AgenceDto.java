package com.tontinemarche.dto;

import com.tontinemarche.domain.enums.StatutEntity;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class AgenceDto {
    private Long id;
    private String code;
    @NotBlank
    private String nom;
    private String responsable;
    private String telephone;
    private String email;
    private String adresse;
    private String ville;
    private String logoUrl;
    private Double latitude;
    private Double longitude;
    private BigDecimal tauxCommission;
    private BigDecimal tauxCommissionAdmin;
    private StatutEntity statut;
}
