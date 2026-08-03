package com.tontinemarche.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CaisseControleDto {
    /** true si la caisse du jour est ouverte ET aucune caisse antérieure n'est ouverte. */
    private boolean peutOperer;
    private String message;
    private CaisseDto caisseDuJour;
    private List<CaisseDto> caissesAnterieuresOuvertes;
}
