package com.tontinemarche.domain.entity;

import com.tontinemarche.domain.enums.StatutCaisse;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "caisses", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"agence_id", "dateCaisse"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Caisse extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agence_id", nullable = false)
    private Agence agence;

    @Column(nullable = false)
    private LocalDate dateCaisse;

    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal soldeInitial = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalEntrees = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalSorties = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal soldeTheorique = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2)
    private BigDecimal soldeReel;

    @Column(precision = 15, scale = 2)
    private BigDecimal ecart;

    private String observation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StatutCaisse statut = StatutCaisse.OUVERTE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ouvert_par_id")
    private Utilisateur ouvertPar;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cloture_par_id")
    private Utilisateur cloturePar;

    private LocalDateTime dateOuverture;
    private LocalDateTime dateCloture;
}
