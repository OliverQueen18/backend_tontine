package com.tontinemarche.dto;

import com.tontinemarche.domain.enums.RoleType;
import com.tontinemarche.domain.enums.StatutEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateUtilisateurRequest {

    @NotBlank(message = "Le nom complet est obligatoire")
    private String nomComplet;

    private String email;
    private String telephone;
    private String photoUrl;

    @NotNull(message = "Le rôle est obligatoire")
    private RoleType role;

    private Long agenceId;

    @NotNull(message = "Le statut est obligatoire")
    private StatutEntity statut;

    @Size(min = 6, message = "Le mot de passe doit contenir au moins 6 caractères")
    private String password;
}
