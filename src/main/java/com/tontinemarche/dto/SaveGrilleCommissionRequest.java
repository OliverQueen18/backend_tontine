package com.tontinemarche.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SaveGrilleCommissionRequest {

    @NotEmpty(message = "La grille doit contenir au moins une tranche")
    @Valid
    private List<GrilleCommissionLigneDto> lignes;
}
