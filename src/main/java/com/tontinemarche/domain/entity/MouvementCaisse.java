package com.tontinemarche.domain.entity;

import com.tontinemarche.domain.enums.CategorieMouvement;
import com.tontinemarche.domain.enums.TypeMouvement;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "mouvements_caisse")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MouvementCaisse extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caisse_id", nullable = false)
    private Caisse caisse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeMouvement type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CategorieMouvement categorie;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal montant;

    private String libelle;
    private String reference;

    @Column(nullable = false)
    private LocalDateTime dateHeure;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "effectue_par_id")
    private Utilisateur effectuePar;
}
