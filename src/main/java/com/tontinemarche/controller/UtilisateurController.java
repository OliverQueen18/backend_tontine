package com.tontinemarche.controller;

import com.tontinemarche.domain.enums.RoleType;
import com.tontinemarche.dto.CreateUtilisateurRequest;
import com.tontinemarche.dto.UpdateUtilisateurRequest;
import com.tontinemarche.dto.UtilisateurDto;
import com.tontinemarche.service.UtilisateurService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/utilisateurs")
@RequiredArgsConstructor
public class UtilisateurController {

    private final UtilisateurService utilisateurService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_AGENCE')")
    public List<UtilisateurDto> findAll(
            @RequestParam(required = false) Long agenceId,
            @RequestParam(required = false) RoleType role
    ) {
        return utilisateurService.findAll(agenceId, role);
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_AGENCE')")
    public Map<String, Object> permissions() {
        return utilisateurService.permissions();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_AGENCE')")
    public UtilisateurDto findById(@PathVariable Long id) {
        return utilisateurService.findById(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_AGENCE')")
    public UtilisateurDto create(@Valid @RequestBody CreateUtilisateurRequest request) {
        return utilisateurService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_AGENCE')")
    public UtilisateurDto update(@PathVariable Long id, @Valid @RequestBody UpdateUtilisateurRequest request) {
        return utilisateurService.update(id, request);
    }

    @PatchMapping("/{id}/desactiver")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_AGENCE')")
    public UtilisateurDto desactiver(@PathVariable Long id) {
        return utilisateurService.desactiver(id);
    }
}
