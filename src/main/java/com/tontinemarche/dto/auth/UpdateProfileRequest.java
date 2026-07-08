package com.tontinemarche.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateProfileRequest {

    @NotBlank(message = "Le nom complet est obligatoire")
    private String nomComplet;

    private String email;
    private String telephone;
    private String photoUrl;
}
