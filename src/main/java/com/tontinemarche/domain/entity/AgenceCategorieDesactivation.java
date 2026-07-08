package com.tontinemarche.domain.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "agence_categorie_desactivations", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"agence_id", "categorie_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgenceCategorieDesactivation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agence_id", nullable = false)
    private Agence agence;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categorie_id", nullable = false)
    private CategorieDepense categorie;
}
