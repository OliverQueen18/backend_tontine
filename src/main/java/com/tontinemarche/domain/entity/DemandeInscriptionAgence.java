package com.tontinemarche.domain.entity;

import com.tontinemarche.domain.enums.MoyenPaiementMobile;
import com.tontinemarche.domain.enums.StatutDemandeInscription;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "demandes_inscription_agence")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DemandeInscriptionAgence extends BaseEntity {

    @Column(nullable = false)
    private String agenceNom;

    private String responsable;
    private String agenceTelephone;
    private String agenceEmail;
    private String adresse;
    private String ville;
    private String logoUrl;

    private Double latitude;
    private Double longitude;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String nomComplet;

    @Column(nullable = false)
    private String email;

    private String telephone;

    @Column(nullable = false, length = 500)
    private String pieceIdentiteUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MoyenPaiementMobile moyenPaiement;

    @Column(nullable = false, length = 80)
    private String referencePaiement;

    @Column(nullable = false)
    @Builder.Default
    private boolean accepteConditions = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StatutDemandeInscription statut = StatutDemandeInscription.EN_ATTENTE;

    private String motifRejet;
    private Long agenceCreeeId;
    private Instant dateTraitement;
}
