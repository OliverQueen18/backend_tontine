package com.tontinemarche.service;

import com.tontinemarche.domain.entity.CategorieDepense;
import com.tontinemarche.domain.entity.Client;
import com.tontinemarche.domain.entity.Depense;
import com.tontinemarche.domain.enums.CategorieMouvement;
import com.tontinemarche.domain.enums.SensOperation;
import com.tontinemarche.domain.enums.TypeMouvement;
import com.tontinemarche.dto.DepenseDto;
import com.tontinemarche.exception.ApiException;
import com.tontinemarche.mapper.EntityMapper;
import com.tontinemarche.repository.DepenseRepository;
import com.tontinemarche.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DepenseService {

    private final DepenseRepository depenseRepository;
    private final CategorieDepenseService categorieDepenseService;
    private final AgenceService agenceService;
    private final AgentService agentService;
    private final ClientService clientService;
    private final CaisseService caisseService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final com.tontinemarche.repository.UtilisateurRepository utilisateurRepository;

    @Value("${app.notification.depense-seuil:50000}")
    private BigDecimal depenseSeuil;

    @Transactional(readOnly = true)
    public List<DepenseDto> findAll(Long agenceId) {
        List<Depense> list = agenceId != null
                ? depenseRepository.findByAgenceIdOrderByDateDepenseDesc(agenceId)
                : depenseRepository.findAll();
        return list.stream().map(EntityMapper::toDto).toList();
    }

    @Transactional
    public DepenseDto create(DepenseDto dto) {
        if (dto.getCategorie() == null || dto.getCategorie().isBlank()) {
            throw ApiException.badRequest("La catégorie est obligatoire");
        }
        caisseService.requireCaisseOuverte(dto.getAgenceId());
        if (!categorieDepenseService.isActiveForAgence(dto.getAgenceId(), dto.getCategorie().trim())) {
            throw ApiException.badRequest("Catégorie d'opération invalide ou inactive");
        }

        CategorieDepense categorie = categorieDepenseService.getByNomForAgence(
                dto.getAgenceId(), dto.getCategorie().trim());

        Client client = resolveClient(categorie, dto);

        Depense depense = Depense.builder()
                .agence(agenceService.getEntity(dto.getAgenceId()))
                .dateDepense(dto.getDateDepense() != null ? dto.getDateDepense() : LocalDate.now())
                .categorie(dto.getCategorie())
                .sens(categorie.getSens())
                .montant(dto.getMontant())
                .justificatifUrl(dto.getJustificatifUrl())
                .observation(dto.getObservation())
                .agent(dto.getAgentId() != null ? agentService.getEntity(dto.getAgentId()) : null)
                .client(client)
                .validee(false)
                .build();

        depense = depenseRepository.save(depense);
        auditService.log("CREATION", "Operation", depense.getCategorie(),
                depense.getMontant() + " FCFA", dto.getAgenceId());

        if (depense.getMontant().compareTo(depenseSeuil) >= 0) {
            notificationService.notifyAgenceStaff(
                    depense.getAgence().getId(),
                    "OPERATION",
                    "Opération importante",
                    "Opération de " + depense.getMontant() + " FCFA (" + depense.getCategorie()
                            + ") enregistrée pour l'agence " + depense.getAgence().getNom() + ".",
                    com.tontinemarche.domain.enums.RoleType.ADMIN_AGENCE,
                    com.tontinemarche.domain.enums.RoleType.SUPER_ADMIN,
                    com.tontinemarche.domain.enums.RoleType.CAISSIER
            );
        }

        return EntityMapper.toDto(depense);
    }

    @Transactional
    public DepenseDto valider(Long id) {
        Depense depense = depenseRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Opération introuvable"));

        if (depense.isValidee()) {
            throw ApiException.badRequest("Opération déjà validée");
        }

        caisseService.requireCaisseOuverte(depense.getAgence().getId());

        CategorieDepense categorie = categorieDepenseService.getByNomForAgence(
                depense.getAgence().getId(), depense.getCategorie());

        if (categorie.isNecessiteClient() && depense.getClient() == null) {
            throw ApiException.badRequest("Un client est requis pour valider cette opération");
        }

        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            depense.setValidePar(utilisateurRepository.findById(principal.getId()).orElse(null));
        }

        depense.setValidee(true);
        depense = depenseRepository.save(depense);

        if (categorie.isNecessiteMouvementCaisse()) {
            TypeMouvement typeMouvement = depense.getSens() == SensOperation.ENTREE
                    ? TypeMouvement.ENTREE
                    : TypeMouvement.SORTIE;
            String libelle = depense.getSens() == SensOperation.ENTREE
                    ? "Opération entrée " + depense.getCategorie()
                    : "Opération sortie " + depense.getCategorie();
            if (depense.getClient() != null) {
                libelle += " — " + depense.getClient().getNomComplet();
            }

            caisseService.enregistrerMouvement(
                    depense.getAgence().getId(),
                    typeMouvement,
                    CategorieMouvement.DEPENSE,
                    depense.getMontant(),
                    libelle,
                    "OP-" + depense.getId()
            );
        }

        auditService.log("VALIDATION", "Operation", String.valueOf(depense.getId()),
                depense.getMontant() + " FCFA", depense.getAgence().getId());
        return EntityMapper.toDto(depense);
    }

    private Client resolveClient(CategorieDepense categorie, DepenseDto dto) {
        if (categorie.isNecessiteClient()) {
            if (dto.getClientId() == null) {
                throw ApiException.badRequest("Un client est obligatoire pour cette catégorie");
            }
            Client client = clientService.getEntity(dto.getClientId());
            if (!client.getAgence().getId().equals(dto.getAgenceId())) {
                throw ApiException.badRequest("Le client n'appartient pas à l'agence sélectionnée");
            }
            return client;
        }
        if (dto.getClientId() != null) {
            Client client = clientService.getEntity(dto.getClientId());
            if (!client.getAgence().getId().equals(dto.getAgenceId())) {
                throw ApiException.badRequest("Le client n'appartient pas à l'agence sélectionnée");
            }
            return client;
        }
        return null;
    }
}
