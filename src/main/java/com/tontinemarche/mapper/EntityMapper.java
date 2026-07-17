package com.tontinemarche.mapper;

import com.tontinemarche.domain.entity.*;
import com.tontinemarche.dto.*;
import com.tontinemarche.util.ClientCalculUtil;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class EntityMapper {

    private EntityMapper() {
    }

    public static AgenceDto toDto(Agence a) {
        return AgenceDto.builder()
                .id(a.getId())
                .code(a.getCode())
                .nom(a.getNom())
                .responsable(a.getResponsable())
                .telephone(a.getTelephone())
                .email(a.getEmail())
                .adresse(a.getAdresse())
                .ville(a.getVille())
                .logoUrl(a.getLogoUrl())
                .latitude(a.getLatitude())
                .longitude(a.getLongitude())
                .tauxCommission(a.getTauxCommission())
                .tauxCommissionAdmin(a.getTauxCommissionAdmin())
                .statut(a.getStatut())
                .build();
    }

    public static AgentDto toDto(Agent a, long nombreClients, BigDecimal montantJour) {
        List<Marche> marches = a.getMarches() != null ? a.getMarches() : List.of();
        return AgentDto.builder()
                .id(a.getId())
                .code(a.getCode())
                .nomComplet(a.getNomComplet())
                .telephone(a.getTelephone())
                .photoUrl(a.getPhotoUrl())
                .agenceId(a.getAgence().getId())
                .agenceNom(a.getAgence().getNom())
                .marcheIds(marches.stream().map(Marche::getId).toList())
                .marcheNoms(marches.stream().map(Marche::getNom).toList())
                .marcheId(marches.isEmpty() ? null : marches.get(0).getId())
                .marcheNom(marches.isEmpty() ? null : marches.get(0).getNom())
                .utilisateurId(a.getUtilisateur() != null ? a.getUtilisateur().getId() : null)
                .username(a.getUtilisateur() != null ? a.getUtilisateur().getUsername() : null)
                .nombreClients(nombreClients)
                .montantCollecteAujourdhui(montantJour)
                .statut(a.getStatut())
                .build();
    }

    public static ClientDto toDto(Client c) {
        return ClientDto.builder()
                .id(c.getId())
                .code(c.getCode())
                .nomComplet(c.getNomComplet())
                .telephone(c.getTelephone())
                .email(c.getEmail())
                .personneAContacter(c.getPersonneAContacter())
                .telephoneSecondaire(c.getTelephoneSecondaire())
                .adresse(c.getAdresse())
                .profession(c.getProfession())
                .photoUrl(c.getPhotoUrl())
                .signatureReference(c.getSignatureReference())
                .agenceId(c.getAgence().getId())
                .agenceNom(c.getAgence().getNom())
                .agenceTelephone(c.getAgence().getTelephone())
                .agenceEmail(c.getAgence().getEmail())
                .agenceAdresse(c.getAgence().getAdresse())
                .agenceVille(c.getAgence().getVille())
                .marcheId(c.getMarche() != null ? c.getMarche().getId() : null)
                .marcheNom(c.getMarche() != null ? c.getMarche().getNom() : null)
                .marcheCode(c.getMarche() != null ? c.getMarche().getCode() : null)
                .agentId(c.getAgent() != null ? c.getAgent().getId() : null)
                .agentNom(c.getAgent() != null ? c.getAgent().getNomComplet() : null)
                .agentTelephone(c.getAgent() != null ? c.getAgent().getTelephone() : null)
                .montantJournalier(c.getMontantJournalier())
                .fraisAdhesion(c.getFraisAdhesion())
                .dateAdhesion(c.getDateAdhesion())
                .soldeEpargne(c.getSoldeEpargne())
                .statut(c.getStatut())
                .nombreJoursPayes(ClientCalculUtil.computeNombreJoursPayes(c.getSoldeEpargne(), c.getMontantJournalier()))
                .dateProbableRetrait(ClientCalculUtil.computeDateProbableRetrait(c.getDateAdhesion(), LocalDate.now()))
                .build();
    }

    public static CollecteDto toDto(Collecte c) {
        return CollecteDto.builder()
                .id(c.getId())
                .numeroRecu(c.getNumeroRecu())
                .clientId(c.getClient().getId())
                .clientCode(c.getClient().getCode())
                .clientNom(c.getClient().getNomComplet())
                .agentId(c.getAgent().getId())
                .agentNom(c.getAgent().getNomComplet())
                .agenceId(c.getAgence().getId())
                .montantPrevu(c.getMontantPrevu())
                .montantRecu(c.getMontantRecu())
                .nombreJoursPayes(c.getNombreJoursPayes())
                .dateCollecte(c.getDateCollecte())
                .dateHeure(c.getDateHeure())
                .signatureClient(c.getSignatureClient())
                .validee(c.isValidee())
                .annulee(c.isAnnulee())
                .build();
    }

    public static RestitutionDto toDto(Restitution r) {
        var client = r.getClient();
        var agence = r.getAgence() != null ? r.getAgence() : (client != null ? client.getAgence() : null);
        var agent = client != null ? client.getAgent() : null;
        var marche = client != null ? client.getMarche() : null;
        return RestitutionDto.builder()
                .id(r.getId())
                .numeroRecu(r.getNumeroRecu())
                .clientId(client != null ? client.getId() : null)
                .clientCode(client != null ? client.getCode() : null)
                .clientNom(client != null ? client.getNomComplet() : null)
                .clientTelephone(client != null ? client.getTelephone() : null)
                .clientEmail(client != null ? client.getEmail() : null)
                .montantJournalier(client != null ? client.getMontantJournalier() : null)
                .marcheNom(marche != null ? marche.getNom() : null)
                .agenceId(agence != null ? agence.getId() : null)
                .agenceNom(agence != null ? agence.getNom() : null)
                .agenceTelephone(agence != null ? agence.getTelephone() : null)
                .agenceEmail(agence != null ? agence.getEmail() : null)
                .agenceAdresse(agence != null ? agence.getAdresse() : null)
                .agenceVille(agence != null ? agence.getVille() : null)
                .agentId(agent != null ? agent.getId() : null)
                .agentNom(agent != null ? agent.getNomComplet() : null)
                .agentTelephone(agent != null ? agent.getTelephone() : null)
                .totalCollecte(r.getTotalCollecte())
                .commission(r.getCommission())
                .commissionCalculee(r.getCommissionCalculee())
                .montantNet(r.getMontantNet())
                .dateHeure(r.getDateHeure())
                .signatureClient(r.getSignatureClient())
                .validee(r.isValidee())
                .build();
    }

    public static DepenseDto toDto(Depense d) {
        return DepenseDto.builder()
                .id(d.getId())
                .agenceId(d.getAgence().getId())
                .dateDepense(d.getDateDepense())
                .categorie(d.getCategorie())
                .sens(d.getSens())
                .montant(d.getMontant())
                .justificatifUrl(d.getJustificatifUrl())
                .observation(d.getObservation())
                .agentId(d.getAgent() != null ? d.getAgent().getId() : null)
                .agentNom(d.getAgent() != null ? d.getAgent().getNomComplet() : null)
                .clientId(d.getClient() != null ? d.getClient().getId() : null)
                .clientNom(d.getClient() != null ? d.getClient().getNomComplet() : null)
                .validee(d.isValidee())
                .build();
    }

    public static MouvementCaisseDto toDto(MouvementCaisse m) {
        return MouvementCaisseDto.builder()
                .id(m.getId())
                .type(m.getType())
                .categorie(m.getCategorie())
                .montant(m.getMontant())
                .libelle(m.getLibelle())
                .reference(m.getReference())
                .dateHeure(m.getDateHeure())
                .build();
    }

    public static CaisseDto toDto(Caisse c, java.util.List<MouvementCaisseDto> mouvements) {
        return CaisseDto.builder()
                .id(c.getId())
                .agenceId(c.getAgence().getId())
                .agenceNom(c.getAgence().getNom())
                .dateCaisse(c.getDateCaisse())
                .soldeInitial(c.getSoldeInitial())
                .totalEntrees(c.getTotalEntrees())
                .totalSorties(c.getTotalSorties())
                .soldeTheorique(c.getSoldeTheorique())
                .soldeReel(c.getSoldeReel())
                .ecart(c.getEcart())
                .observation(c.getObservation())
                .statut(c.getStatut())
                .dateOuverture(c.getDateOuverture())
                .dateCloture(c.getDateCloture())
                .mouvements(mouvements)
                .build();
    }
}
