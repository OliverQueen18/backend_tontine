package com.tontinemarche.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "restitutions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Restitution extends BaseEntity {

    @Column(nullable = false, unique = true, length = 40)
    private String numeroRecu;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agence_id", nullable = false)
    private Agence agence;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caissier_id")
    private Utilisateur caissier;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalCollecte;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal commission;

    @Column(precision = 15, scale = 2)
    private BigDecimal commissionCalculee;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal montantNet;

    @Column(nullable = false)
    private LocalDateTime dateHeure;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String signatureClient;

    @Column(nullable = false)
    @Builder.Default
    private boolean validee = false;
}
