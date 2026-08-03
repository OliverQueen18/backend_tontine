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
    /** Active les notifications SMS métier (SUPER_ADMIN). */
    private Boolean smsNotificationsEnabled;
    /** Passerelle technique prête (config sms.gateway.*). Lecture seule. */
    private Boolean smsGatewayReady;
}
