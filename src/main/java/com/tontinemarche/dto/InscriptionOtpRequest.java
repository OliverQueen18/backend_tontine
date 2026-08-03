package com.tontinemarche.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InscriptionOtpRequest {

    @NotBlank
    @Email
    private String email;

    private String nomComplet;

    /** Optionnel : si renseigné et passerelle SMS prête, le code est aussi envoyé par SMS. */
    private String telephone;
}
