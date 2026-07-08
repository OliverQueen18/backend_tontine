package com.tontinemarche.domain.entity;

import com.tontinemarche.domain.enums.StatutEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "quartiers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Quartier extends BaseEntity {

    @Column(nullable = false)
    private String nom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agence_id", nullable = false)
    private Agence agence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StatutEntity statut = StatutEntity.ACTIF;
}
