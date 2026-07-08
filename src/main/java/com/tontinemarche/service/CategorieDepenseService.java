package com.tontinemarche.service;

import com.tontinemarche.domain.entity.Agence;
import com.tontinemarche.domain.entity.AgenceCategorieDesactivation;
import com.tontinemarche.domain.entity.CategorieDepense;
import com.tontinemarche.domain.enums.SensOperation;
import com.tontinemarche.domain.enums.StatutEntity;
import com.tontinemarche.exception.ApiException;
import com.tontinemarche.repository.AgenceCategorieDesactivationRepository;
import com.tontinemarche.repository.AgenceRepository;
import com.tontinemarche.repository.CategorieDepenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CategorieDepenseService {

    private final CategorieDepenseRepository categorieDepenseRepository;
    private final AgenceCategorieDesactivationRepository desactivationRepository;
    private final AgenceRepository agenceRepository;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listForAgence(Long agenceId, boolean activesOnly) {
        Set<Long> desactiveIds = desactivationIds(agenceId);
        return categorieDepenseRepository.findAllByOrderByNomAsc().stream()
                .filter(c -> c.getStatut() == StatutEntity.ACTIF || !activesOnly)
                .map(c -> toMap(c, agenceId, desactiveIds))
                .filter(m -> !activesOnly || StatutEntity.ACTIF.name().equals(m.get("statut")))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listGlobal(boolean activesOnly) {
        return categorieDepenseRepository.findAllByOrderByNomAsc().stream()
                .filter(c -> !activesOnly || c.getStatut() == StatutEntity.ACTIF)
                .map(c -> toMap(c, null, Set.of()))
                .toList();
    }

    @Transactional
    public Map<String, Object> create(String nom, SensOperation sens, boolean necessiteMouvementCaisse,
                                      boolean necessiteClient, Long agenceId) {
        String trimmed = nom.trim();
        if (trimmed.isBlank()) {
            throw ApiException.badRequest("Le nom de la catégorie est obligatoire");
        }

        CategorieDepense existing = categorieDepenseRepository.findByNomIgnoreCase(trimmed).orElse(null);
        if (existing != null) {
            if (existing.getStatut() == StatutEntity.INACTIF) {
                existing.setStatut(StatutEntity.ACTIF);
                existing.setSens(sens);
                existing.setNecessiteMouvementCaisse(necessiteMouvementCaisse);
                existing.setNecessiteClient(necessiteClient);
                existing = categorieDepenseRepository.save(existing);
            } else if (agenceId != null && desactivationRepository.existsByAgenceIdAndCategorieId(agenceId, existing.getId())) {
                desactivationRepository.deleteByAgenceIdAndCategorieId(agenceId, existing.getId());
            } else if (existing.getStatut() == StatutEntity.ACTIF) {
                throw ApiException.conflict("Cette catégorie existe déjà");
            }
            return toMap(existing, agenceId, desactivationIds(agenceId));
        }

        CategorieDepense cat = categorieDepenseRepository.save(CategorieDepense.builder()
                .nom(trimmed)
                .sens(sens)
                .necessiteMouvementCaisse(necessiteMouvementCaisse)
                .necessiteClient(necessiteClient)
                .statut(StatutEntity.ACTIF)
                .build());
        return toMap(cat, agenceId, desactivationIds(agenceId));
    }

    @Transactional
    public Map<String, Object> desactiverPourAgence(Long categorieId, Long agenceId) {
        CategorieDepense cat = categorieDepenseRepository.findById(categorieId)
                .orElseThrow(() -> ApiException.notFound("Catégorie introuvable"));
        Agence agence = agenceRepository.findById(agenceId)
                .orElseThrow(() -> ApiException.notFound("Agence introuvable"));

        if (!desactivationRepository.existsByAgenceIdAndCategorieId(agenceId, categorieId)) {
            desactivationRepository.save(AgenceCategorieDesactivation.builder()
                    .agence(agence)
                    .categorie(cat)
                    .build());
        }
        return toMap(cat, agenceId, desactivationIds(agenceId));
    }

    @Transactional
    public Map<String, Object> reactiverPourAgence(Long categorieId, Long agenceId) {
        CategorieDepense cat = categorieDepenseRepository.findById(categorieId)
                .orElseThrow(() -> ApiException.notFound("Catégorie introuvable"));
        desactivationRepository.deleteByAgenceIdAndCategorieId(agenceId, categorieId);
        return toMap(cat, agenceId, desactivationIds(agenceId));
    }

    @Transactional(readOnly = true)
    public boolean isActiveForAgence(Long agenceId, String nom) {
        CategorieDepense cat = categorieDepenseRepository.findByNomIgnoreCase(nom.trim()).orElse(null);
        if (cat == null || cat.getStatut() != StatutEntity.ACTIF) {
            return false;
        }
        return !desactivationRepository.existsByAgenceIdAndCategorieId(agenceId, cat.getId());
    }

    @Transactional(readOnly = true)
    public CategorieDepense getByNomForAgence(Long agenceId, String nom) {
        CategorieDepense cat = categorieDepenseRepository.findByNomIgnoreCase(nom.trim())
                .orElseThrow(() -> ApiException.badRequest("Catégorie introuvable"));
        if (!isActiveForAgence(agenceId, nom)) {
            throw ApiException.badRequest("Catégorie d'opération invalide ou inactive pour cette agence");
        }
        return cat;
    }

    private Set<Long> desactivationIds(Long agenceId) {
        if (agenceId == null) {
            return Set.of();
        }
        return new HashSet<>(desactivationRepository.findCategorieIdByAgenceId(agenceId));
    }

    private Map<String, Object> toMap(CategorieDepense c, Long agenceId, Set<Long> desactiveIds) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", c.getId());
        map.put("nom", c.getNom());
        map.put("sens", c.getSens().name());
        map.put("necessiteMouvementCaisse", c.isNecessiteMouvementCaisse());
        map.put("necessiteClient", c.isNecessiteClient());
        if (agenceId != null) {
            boolean activeForAgence = c.getStatut() == StatutEntity.ACTIF
                    && !desactiveIds.contains(c.getId());
            map.put("statut", activeForAgence ? StatutEntity.ACTIF.name() : StatutEntity.INACTIF.name());
            map.put("agenceId", agenceId);
        } else {
            map.put("statut", c.getStatut().name());
        }
        return map;
    }
}
