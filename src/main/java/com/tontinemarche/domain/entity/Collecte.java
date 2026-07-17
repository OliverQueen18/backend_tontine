package com.tontinemarche.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "collectes", indexes = {
        @Index(name = "idx_collecte_date", columnList = "dateCollecte"),
        @Index(name = "idx_collecte_client", columnList = "client_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Collecte extends BaseEntity {

    @Column(nullable = false, unique = true, length = 40)
    private String numeroRecu;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agence_id", nullable = false)
    private Agence agence;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal montantPrevu;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal montantRecu;

    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal nombreJoursPayes = BigDecimal.ONE;

    @Column(nullable = false)
    private LocalDate dateCollecte;

    @Column(nullable = false)
    private LocalDateTime dateHeure;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String signatureClient;

    @Column(nullable = false)
    @Builder.Default
    private boolean validee = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean annulee = false;
}
