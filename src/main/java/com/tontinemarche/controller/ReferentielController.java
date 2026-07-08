package com.tontinemarche.controller;

import com.tontinemarche.config.MarcheCodeInitializer;
import com.tontinemarche.domain.entity.Agent;
import com.tontinemarche.domain.entity.Marche;
import com.tontinemarche.domain.entity.Quartier;
import com.tontinemarche.domain.entity.Utilisateur;
import com.tontinemarche.domain.enums.RoleType;
import com.tontinemarche.domain.enums.SensOperation;
import com.tontinemarche.domain.enums.StatutEntity;
import com.tontinemarche.exception.ApiException;
import com.tontinemarche.repository.AgenceRepository;
import com.tontinemarche.repository.AgentRepository;
import com.tontinemarche.repository.MarcheRepository;
import com.tontinemarche.repository.QuartierRepository;
import com.tontinemarche.repository.UtilisateurRepository;
import com.tontinemarche.repository.UtilisateurRepository;
import com.tontinemarche.service.AgentService;
import com.tontinemarche.service.CategorieDepenseService;
import com.tontinemarche.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/referentiels")
@RequiredArgsConstructor
@Transactional
public class ReferentielController {

    private final MarcheRepository marcheRepository;
    private final QuartierRepository quartierRepository;
    private final CategorieDepenseService categorieDepenseService;
    private final AgenceRepository agenceRepository;
    private final AgentRepository agentRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final AgentService agentService;

    @GetMapping("/marches")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_AGENCE', 'AGENT')")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> marches(@RequestParam(required = false) Long agenceId) {
        UserPrincipal principal = currentPrincipal();
        if (principal != null && principal.getRole() == RoleType.AGENT) {
            Agent agent = agentRepository.findByUtilisateurId(principal.getId())
                    .orElseThrow(() -> ApiException.badRequest("Profil agent introuvable"));
            return agent.getMarches().stream().map(this::toMarcheMap).toList();
        }

        Long scopedAgenceId = resolveAgenceScope(principal, agenceId);
        List<Marche> list = scopedAgenceId != null
                ? marcheRepository.findByAgenceId(scopedAgenceId)
                : marcheRepository.findAll();
        return list.stream().map(this::toMarcheMap).toList();
    }

    @PostMapping("/marches")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_AGENCE', 'AGENT')")
    public Map<String, Object> createMarche(@RequestBody Map<String, Object> payload) {
        UserPrincipal principal = currentPrincipal();
        Long agenceId;
        Agent agent = null;

        if (principal != null && principal.getRole() == RoleType.AGENT) {
            agent = agentRepository.findByUtilisateurId(principal.getId())
                    .orElseThrow(() -> ApiException.badRequest("Profil agent introuvable"));
            agenceId = agent.getAgence().getId();
        } else if (principal != null && principal.getRole() == RoleType.ADMIN_AGENCE) {
            agenceId = principal.getAgenceId();
            Utilisateur admin = utilisateurRepository.findById(principal.getId())
                    .orElseThrow(() -> ApiException.notFound("Utilisateur introuvable"));
            agent = agentService.ensureCollecteurProfile(admin);
        } else {
            if (payload.get("agenceId") == null) {
                throw ApiException.badRequest("L'agence est obligatoire");
            }
            agenceId = Long.valueOf(payload.get("agenceId").toString());
        }

        String adresse = stringOrEmpty(payload.get("adresse"));
        if (adresse.isBlank()) {
            throw ApiException.badRequest("La localisation (adresse) est obligatoire");
        }

        Marche m = marcheRepository.save(Marche.builder()
                .nom(payload.get("nom").toString())
                .code(resolveMarcheCode(payload))
                .description(stringOrEmpty(payload.get("description")))
                .adresse(adresse)
                .latitude(parseDoubleRequired(payload.get("latitude"), "latitude"))
                .longitude(parseDoubleRequired(payload.get("longitude"), "longitude"))
                .agence(agenceRepository.findById(agenceId)
                        .orElseThrow(() -> ApiException.notFound("Agence introuvable")))
                .statut(StatutEntity.ACTIF)
                .build());

        if (agent != null) {
            if (agent.getMarches() == null) {
                agent.setMarches(new ArrayList<>());
            }
            agent.getMarches().add(m);
            agentRepository.save(agent);
        }

        Map<String, Object> result = new HashMap<>(toMarcheMap(m));
        result.put("code", m.getCode());
        return result;
    }

    @GetMapping("/marches/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_AGENCE', 'AGENT')")
    @Transactional(readOnly = true)
    public Map<String, Object> getMarche(@PathVariable Long id) {
        Marche m = getMarcheOrThrow(id);
        assertCanAccessMarche(m);
        return toMarcheMap(m);
    }

    @PutMapping("/marches/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_AGENCE', 'AGENT')")
    public Map<String, Object> updateMarche(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        Marche m = getMarcheOrThrow(id);
        assertCanModifyMarche(m);

        String adresse = stringOrEmpty(payload.get("adresse"));
        if (adresse.isBlank()) {
            throw ApiException.badRequest("La localisation (adresse) est obligatoire");
        }

        m.setNom(payload.get("nom").toString());
        if (payload.containsKey("code") && payload.get("code") != null && !payload.get("code").toString().isBlank()) {
            m.setCode(payload.get("code").toString().trim().toUpperCase());
        }
        m.setDescription(stringOrEmpty(payload.get("description")));
        m.setAdresse(adresse);
        m.setLatitude(parseDoubleRequired(payload.get("latitude"), "latitude"));
        m.setLongitude(parseDoubleRequired(payload.get("longitude"), "longitude"));

        return toMarcheMap(marcheRepository.save(m));
    }

    @PatchMapping("/marches/{id}/desactiver")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_AGENCE')")
    public Map<String, Object> desactiverMarche(@PathVariable Long id) {
        Marche m = getMarcheOrThrow(id);
        assertAdminAgenceScope(m);
        m.setStatut(StatutEntity.INACTIF);
        return toMarcheMap(marcheRepository.save(m));
    }

    @GetMapping("/quartiers")
    public List<Map<String, Object>> quartiers(@RequestParam(required = false) Long agenceId) {
        List<Quartier> list = agenceId != null ? quartierRepository.findByAgenceId(agenceId) : quartierRepository.findAll();
        return list.stream().map(q -> Map.<String, Object>of(
                "id", q.getId(),
                "nom", q.getNom(),
                "agenceId", q.getAgence().getId(),
                "statut", q.getStatut().name()
        )).toList();
    }

    @PostMapping("/quartiers")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_AGENCE')")
    public Map<String, Object> createQuartier(@RequestBody Map<String, Object> payload) {
        Long agenceId = Long.valueOf(payload.get("agenceId").toString());
        Quartier q = quartierRepository.save(Quartier.builder()
                .nom(payload.get("nom").toString())
                .agence(agenceRepository.findById(agenceId)
                        .orElseThrow(() -> ApiException.notFound("Agence introuvable")))
                .statut(StatutEntity.ACTIF)
                .build());
        return Map.of("id", q.getId(), "nom", q.getNom(), "agenceId", agenceId, "statut", q.getStatut().name());
    }

    @GetMapping("/categories-depenses")
    public List<Map<String, Object>> categoriesDepenses(
            @RequestParam(required = false) Long agenceId,
            @RequestParam(defaultValue = "false") boolean activesOnly
    ) {
        UserPrincipal principal = currentPrincipal();
        Long scopedAgenceId = resolveAgenceScope(principal, agenceId);
        if (scopedAgenceId != null) {
            return categorieDepenseService.listForAgence(scopedAgenceId, activesOnly);
        }
        if (principal != null && principal.getRole() == RoleType.SUPER_ADMIN) {
            return categorieDepenseService.listGlobal(activesOnly);
        }
        throw ApiException.badRequest("L'agence est obligatoire");
    }

    @PostMapping("/categories-depenses")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_AGENCE')")
    public Map<String, Object> createCategorieDepense(@RequestBody Map<String, Object> payload) {
        UserPrincipal principal = currentPrincipal();
        Long agenceId = payload.get("agenceId") != null
                ? Long.valueOf(payload.get("agenceId").toString())
                : null;
        Long scopedAgenceId = resolveAgenceScope(principal, agenceId);
        return categorieDepenseService.create(
                stringOrEmpty(payload.get("nom")),
                parseSens(payload.get("sens")),
                parseBoolean(payload.get("necessiteMouvementCaisse"), true),
                parseBoolean(payload.get("necessiteClient"), false),
                scopedAgenceId
        );
    }

    @PatchMapping("/categories-depenses/{id}/desactiver")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_AGENCE')")
    public Map<String, Object> desactiverCategorieDepense(
            @PathVariable Long id,
            @RequestParam(required = false) Long agenceId
    ) {
        UserPrincipal principal = currentPrincipal();
        Long scopedAgenceId = resolveAgenceScope(principal, agenceId);
        if (scopedAgenceId == null) {
            throw ApiException.badRequest("L'agence est obligatoire pour désactiver une catégorie");
        }
        return categorieDepenseService.desactiverPourAgence(id, scopedAgenceId);
    }

    @PatchMapping("/categories-depenses/{id}/reactiver")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_AGENCE')")
    public Map<String, Object> reactiverCategorieDepense(
            @PathVariable Long id,
            @RequestParam(required = false) Long agenceId
    ) {
        UserPrincipal principal = currentPrincipal();
        Long scopedAgenceId = resolveAgenceScope(principal, agenceId);
        if (scopedAgenceId == null) {
            throw ApiException.badRequest("L'agence est obligatoire pour réactiver une catégorie");
        }
        return categorieDepenseService.reactiverPourAgence(id, scopedAgenceId);
    }

    private void assertAgenceIdScope(Long agenceId) {
        UserPrincipal principal = currentPrincipal();
        if (principal == null) {
            throw ApiException.forbidden("Accès refusé");
        }
        if (principal.getRole() == RoleType.SUPER_ADMIN) {
            return;
        }
        if (principal.getRole() == RoleType.ADMIN_AGENCE) {
            if (principal.getAgenceId() == null || !principal.getAgenceId().equals(agenceId)) {
                throw ApiException.forbidden("Catégorie hors de votre agence");
            }
            return;
        }
        throw ApiException.forbidden("Accès refusé");
    }

    private Map<String, Object> toMarcheMap(Marche m) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", m.getId());
        map.put("nom", m.getNom());
        map.put("code", m.getCode() != null ? m.getCode() : "");
        map.put("description", m.getDescription() != null ? m.getDescription() : "");
        map.put("adresse", m.getAdresse() != null ? m.getAdresse() : "");
        map.put("latitude", m.getLatitude());
        map.put("longitude", m.getLongitude());
        map.put("agenceId", m.getAgence().getId());
        map.put("agenceNom", m.getAgence().getNom());
        map.put("statut", m.getStatut().name());
        return map;
    }

    private Marche getMarcheOrThrow(Long id) {
        return marcheRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Marché introuvable"));
    }

    private void assertCanAccessMarche(Marche m) {
        UserPrincipal principal = currentPrincipal();
        if (principal == null) {
            throw ApiException.forbidden("Accès refusé");
        }
        if (principal.getRole() == RoleType.SUPER_ADMIN) {
            return;
        }
        if (principal.getRole() == RoleType.ADMIN_AGENCE) {
            assertAdminAgenceScope(m);
            return;
        }
        if (principal.getRole() == RoleType.AGENT) {
            Agent agent = agentRepository.findByUtilisateurId(principal.getId())
                    .orElseThrow(() -> ApiException.badRequest("Profil agent introuvable"));
            boolean assigned = agent.getMarches().stream().anyMatch(mm -> mm.getId().equals(m.getId()));
            if (!assigned) {
                throw ApiException.forbidden("Marché non accessible");
            }
        }
    }

    private void assertCanModifyMarche(Marche m) {
        assertCanAccessMarche(m);
        if (m.getStatut() != StatutEntity.ACTIF) {
            throw ApiException.badRequest("Impossible de modifier un marché inactif");
        }
    }

    private void assertAdminAgenceScope(Marche m) {
        UserPrincipal principal = currentPrincipal();
        if (principal == null) {
            throw ApiException.forbidden("Accès refusé");
        }
        if (principal.getRole() == RoleType.SUPER_ADMIN) {
            return;
        }
        if (principal.getRole() == RoleType.ADMIN_AGENCE) {
            if (principal.getAgenceId() == null || !principal.getAgenceId().equals(m.getAgence().getId())) {
                throw ApiException.forbidden("Marché hors de votre agence");
            }
            return;
        }
        throw ApiException.forbidden("Accès refusé");
    }

    private String resolveMarcheCode(Map<String, Object> payload) {
        if (payload.containsKey("code") && payload.get("code") != null
                && !payload.get("code").toString().isBlank()) {
            return payload.get("code").toString().trim().toUpperCase();
        }
        return MarcheCodeInitializer.generateCode(payload.get("nom").toString());
    }

    private SensOperation parseSens(Object value) {
        if (value == null || value.toString().isBlank()) {
            return SensOperation.SORTIE;
        }
        try {
            return SensOperation.valueOf(value.toString().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Sens invalide (ENTREE ou SORTIE)");
        }
    }

    private boolean parseBoolean(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private String stringOrEmpty(Object value) {
        return value != null ? value.toString().trim() : "";
    }

    private Double parseDoubleRequired(Object value, String label) {
        if (value == null || value.toString().isBlank()) {
            throw ApiException.badRequest("La coordonnée " + label + " est obligatoire");
        }
        try {
            return Double.valueOf(value.toString());
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("Coordonnée " + label + " invalide");
        }
    }

    private Long resolveAgenceScope(UserPrincipal principal, Long requestedAgenceId) {
        if (principal == null) {
            return requestedAgenceId;
        }
        if (principal.getRole() == RoleType.SUPER_ADMIN) {
            return requestedAgenceId;
        }
        if (principal.getRole() == RoleType.ADMIN_AGENCE || principal.getRole() == RoleType.CAISSIER) {
            Long ownAgenceId = principal.getAgenceId();
            if (ownAgenceId == null) {
                throw ApiException.forbidden("Agence non définie pour cet utilisateur");
            }
            if (requestedAgenceId != null && !ownAgenceId.equals(requestedAgenceId)) {
                throw ApiException.forbidden("Hors de votre agence");
            }
            return ownAgenceId;
        }
        return requestedAgenceId;
    }

    private UserPrincipal currentPrincipal() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal;
        }
        return null;
    }
}
