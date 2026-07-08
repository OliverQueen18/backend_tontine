package com.tontinemarche.dto;

import com.tontinemarche.domain.enums.MoyenPaiementMobile;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InscriptionCollecteurRequest {

    @NotBlank(message = "Le nom de l'agence est obligatoire")
    private String agenceNom;

    private String responsable;
    private String agenceTelephone;

    @Email(message = "E-mail agence invalide")
    private String agenceEmail;

    private String adresse;
    private String ville;
    private String logoUrl;
    private Double latitude;
    private Double longitude;

    @NotBlank(message = "L'identifiant administrateur est obligatoire")
    private String username;

    @NotBlank(message = "Le mot de passe est obligatoire")
    @Size(min = 6, message = "Le mot de passe doit contenir au moins 6 caractères")
    private String password;

    @NotBlank(message = "La confirmation du mot de passe est obligatoire")
    private String confirmPassword;

    @NotBlank(message = "Le nom complet de l'administrateur est obligatoire")
    private String nomComplet;

    @NotBlank(message = "L'e-mail administrateur est obligatoire")
    @Email(message = "E-mail administrateur invalide")
    private String email;

    private String telephone;

    @NotBlank(message = "La pièce d'identité est obligatoire")
    private String pieceIdentiteUrl;

    @NotNull(message = "Le moyen de paiement est obligatoire")
    private MoyenPaiementMobile moyenPaiement;

    @NotBlank(message = "La référence de paiement est obligatoire")
    private String referencePaiement;

    @NotBlank(message = "La vérification e-mail est obligatoire")
    private String verificationToken;

    private boolean accepteConditions;
}
