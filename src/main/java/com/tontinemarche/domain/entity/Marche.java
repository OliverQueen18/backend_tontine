package com.tontinemarche.domain.entity;

import com.tontinemarche.domain.enums.StatutEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "marches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Marche extends BaseEntity {

    @Column(nullable = false)
    private String nom;

    @Column(length = 30)
    private String code;

    private String description;

    /** Localisation / adresse du marché */
    private String adresse;

    private Double latitude;

    private Double longitude;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agence_id", nullable = false)
    private Agence agence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StatutEntity statut = StatutEntity.ACTIF;
}
