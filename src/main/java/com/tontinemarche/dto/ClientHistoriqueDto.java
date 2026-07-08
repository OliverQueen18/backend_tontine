package com.tontinemarche.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ClientHistoriqueDto {
    private Long id;
    private String typeAction;
    private String champ;
    private String valeurAvant;
    private String valeurApres;
    private String details;
    private String effectueParNom;
    private LocalDateTime dateHeure;
}
