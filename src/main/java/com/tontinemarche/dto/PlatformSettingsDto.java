package com.tontinemarche.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PlatformSettingsDto {
    private BigDecimal fraisCreationAgence;
    private String telephonePaiementMobile;
    private BigDecimal tauxCommissionAdminDefaut;
}
