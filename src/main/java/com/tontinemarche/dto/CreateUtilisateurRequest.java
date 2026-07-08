package com.tontinemarche.dto;

import com.tontinemarche.domain.enums.RoleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateUtilisateurRequest {

    @NotBlank(message = "L'identifiant est obligatoire")
    private String username;

    @NotBlank
    @Size(min = 6, message = "Le mot de passe doit contenir au moins 6 caractères")
    private String password;

    @NotBlank(message = "Le nom complet est obligatoire")
    private String nomComplet;

    private String email;
    private String telephone;
    private String photoUrl;

    @NotNull(message = "Le rôle est obligatoire")
    private RoleType role;

    private Long agenceId;
}
