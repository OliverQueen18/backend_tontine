package com.tontinemarche.domain.entity;

import com.tontinemarche.domain.enums.StatutEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "agences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Agence extends BaseEntity {

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false)
    private String nom;

    private String responsable;
    private String telephone;
    private String email;
    private String adresse;
    private String ville;

    private Double latitude;
    private Double longitude;

    @Column(length = 500)
    private String logoUrl;

    @Column(nullable = false, precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal tauxCommission = new BigDecimal("0.1000");

    @Column(nullable = false, precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal tauxCommissionAdmin = new BigDecimal("0.0500");

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StatutEntity statut = StatutEntity.ACTIF;
}
