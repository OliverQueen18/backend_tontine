package com.tontinemarche.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "platform_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformSettings {

    @Id
    @Builder.Default
    private Long id = 1L;

    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal fraisCreationAgence = new BigDecimal("50000");

    @Column(length = 30)
    private String telephonePaiementMobile;

    @Column(nullable = false, precision = 8, scale = 4)
    @Builder.Default
    private BigDecimal tauxCommissionAdminDefaut = new BigDecimal("0.0500");
}
