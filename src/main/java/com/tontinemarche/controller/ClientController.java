package com.tontinemarche.controller;

import com.tontinemarche.dto.ClientDto;
import com.tontinemarche.dto.ClientHistoriqueDto;
import com.tontinemarche.service.ClientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;

    @GetMapping
    public List<ClientDto> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long agenceId,
            @RequestParam(required = false) Long agentId
    ) {
        return clientService.search(q, agenceId, agentId);
    }

    @GetMapping("/{id}")
    public ClientDto findById(@PathVariable Long id) {
        return clientService.findById(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_AGENCE', 'AGENT')")
    public ClientDto create(@Valid @RequestBody ClientDto dto) {
        return clientService.create(dto);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_AGENCE', 'AGENT')")
    public ClientDto update(@PathVariable Long id, @Valid @RequestBody ClientDto dto) {
        return clientService.update(id, dto);
    }

    @PatchMapping("/{id}/desactiver")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_AGENCE', 'AGENT')")
    public ClientDto desactiver(@PathVariable Long id, @RequestBody(required = false) Map<String, String> payload) {
        String motif = payload != null ? payload.get("motif") : null;
        return clientService.desactiver(id, motif);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_AGENCE', 'AGENT')")
    public Map<String, String> supprimer(@PathVariable Long id) {
        clientService.supprimer(id);
        return Map.of("message", "Client supprimé");
    }

    @PostMapping("/{id}/transferer")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_AGENCE')")
    public ClientDto transferer(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        return clientService.transferer(id, payload);
    }

    @GetMapping("/{id}/affectations")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> historiqueAffectations(@PathVariable Long id) {
        return clientService.historiqueAffectations(id).stream().map(a -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", a.getId());
            m.put("dateAffectation", a.getDateAffectation());
            m.put("motif", a.getMotif());
            m.put("agentSource", a.getAgentSource() != null ? a.getAgentSource().getNomComplet() : null);
            m.put("agentCible", a.getAgentCible().getNomComplet());
            return m;
        }).toList();
    }

    @GetMapping("/{id}/historique")
    @Transactional(readOnly = true)
    public List<ClientHistoriqueDto> historique(@PathVariable Long id) {
        return clientService.historique(id);
    }
}
