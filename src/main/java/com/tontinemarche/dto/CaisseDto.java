package com.tontinemarche.dto;

import com.tontinemarche.domain.enums.StatutCaisse;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class CaisseDto {
    private Long id;
    private Long agenceId;
    private String agenceNom;
    private LocalDate dateCaisse;
    private BigDecimal soldeInitial;
    private BigDecimal totalEntrees;
    private BigDecimal totalSorties;
    private BigDecimal soldeTheorique;
    private BigDecimal soldeReel;
    private BigDecimal ecart;
    private String observation;
    private StatutCaisse statut;
    private LocalDateTime dateOuverture;
    private LocalDateTime dateCloture;
    private String ouvertParNom;
    private String clotureParNom;
    private String agenceTelephone;
    private String agenceAdresse;
    private String agenceVille;
    private List<MouvementCaisseDto> mouvements;
}
