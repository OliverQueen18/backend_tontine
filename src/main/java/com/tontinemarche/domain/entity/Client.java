package com.tontinemarche.domain.entity;

import com.tontinemarche.domain.enums.StatutEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "clients")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Client extends BaseEntity {

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false)
    private String nomComplet;

    private String telephone;
    private String email;
    private String personneAContacter;
    private String telephoneSecondaire;
    private String adresse;
    private String profession;
    @Column(length = 500)
    private String photoUrl;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String signatureReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agence_id", nullable = false)
    private Agence agence;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marche_id")
    private Marche marche;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quartier_id")
    private Quartier quartier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id")
    private Agent agent;

    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal montantJournalier = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal fraisAdhesion = BigDecimal.ZERO;

    @Column(nullable = false)
    private LocalDate dateAdhesion;

    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal soldeEpargne = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StatutEntity statut = StatutEntity.ACTIF;

    @Column(nullable = false)
    @Builder.Default
    private boolean supprime = false;
}
