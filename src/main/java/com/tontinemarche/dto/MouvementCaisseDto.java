package com.tontinemarche.dto;

import com.tontinemarche.domain.enums.CategorieMouvement;
import com.tontinemarche.domain.enums.TypeMouvement;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class MouvementCaisseDto {
    private Long id;
    private TypeMouvement type;
    private CategorieMouvement categorie;
    private BigDecimal montant;
    private String libelle;
    private String reference;
    private LocalDateTime dateHeure;
}
