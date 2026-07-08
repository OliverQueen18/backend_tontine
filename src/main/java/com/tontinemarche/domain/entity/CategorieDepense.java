package com.tontinemarche.domain.entity;

import com.tontinemarche.domain.enums.StatutEntity;
import com.tontinemarche.domain.enums.SensOperation;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "categories_depenses", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"nom"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategorieDepense extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String nom;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private SensOperation sens = SensOperation.SORTIE;

    @Builder.Default
    private boolean necessiteMouvementCaisse = true;

    @Builder.Default
    private boolean necessiteClient = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StatutEntity statut = StatutEntity.ACTIF;
}
