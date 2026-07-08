package com.tontinemarche.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "client_historique")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientHistorique extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(nullable = false, length = 40)
    private String typeAction;

    @Column(length = 80)
    private String champ;

    @Column(length = 500)
    private String valeurAvant;

    @Column(length = 500)
    private String valeurApres;

    @Column(length = 1000)
    private String details;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "effectue_par_id")
    private Utilisateur effectuePar;

    @Column(nullable = false)
    private LocalDateTime dateHeure;
}
