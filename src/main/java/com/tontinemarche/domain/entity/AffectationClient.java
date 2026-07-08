package com.tontinemarche.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "affectations_clients")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AffectationClient extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_source_id")
    private Agent agentSource;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_cible_id", nullable = false)
    private Agent agentCible;

    @Column(nullable = false)
    private LocalDate dateAffectation;

    private String motif;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "effectue_par_id")
    private Utilisateur effectuePar;
}
