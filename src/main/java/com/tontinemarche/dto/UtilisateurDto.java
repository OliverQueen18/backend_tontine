package com.tontinemarche.dto;

import com.tontinemarche.domain.enums.RoleType;
import com.tontinemarche.domain.enums.StatutEntity;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UtilisateurDto {
    private Long id;
    private String username;
    private String nomComplet;
    private String email;
    private String telephone;
    private String photoUrl;
    private RoleType role;
    private Long agenceId;
    private String agenceNom;
    private StatutEntity statut;
}
