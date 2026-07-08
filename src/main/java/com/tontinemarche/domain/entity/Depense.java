package com.tontinemarche.domain.entity;

import com.tontinemarche.domain.enums.SensOperation;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "depenses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Depense extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agence_id", nullable = false)
    private Agence agence;

    @Column(nullable = false)
    private LocalDate dateDepense;

    @Column(nullable = false)
    private String categorie;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private SensOperation sens = SensOperation.SORTIE;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal montant;

    private String justificatifUrl;
    private String observation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id")
    private Agent agent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "valide_par_id")
    private Utilisateur validePar;

    @Column(nullable = false)
    @Builder.Default
    private boolean validee = false;
}
