package com.tontinemarche.dto;

import com.tontinemarche.domain.enums.StatutEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class AgentDto {
    private Long id;
    private String code;
    @NotBlank
    private String nomComplet;
    private String telephone;
    private String photoUrl;
    @NotNull
    private Long agenceId;
    private String agenceNom;
    /** @deprecated préférer marcheIds */
    private Long marcheId;
    /** @deprecated préférer marcheNoms */
    private String marcheNom;
    private List<Long> marcheIds;
    private List<String> marcheNoms;
    private Long utilisateurId;
    private String username;
    private String password;
    private long nombreClients;
    private BigDecimal montantCollecteAujourdhui;
    private StatutEntity statut;
}
