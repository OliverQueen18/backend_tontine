package com.tontinemarche.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "grille_commission_lignes", indexes = {
        @Index(name = "idx_grille_commission_agence", columnList = "agence_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GrilleCommissionLigne extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agence_id", nullable = false)
    private Agence agence;

    /** Montant minimum de l'intervalle (inclus), en FCFA */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal montantMin;

    /** Montant maximum de l'intervalle (inclus). Null = sans plafond */
    @Column(precision = 15, scale = 2)
    private BigDecimal montantMax;

    /** Commission fixe appliquée pour cet intervalle, en FCFA */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal montantCommission;

    @Column(nullable = false)
    @Builder.Default
    private Integer ordre = 0;
}
