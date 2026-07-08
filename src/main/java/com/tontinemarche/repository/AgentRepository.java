package com.tontinemarche.repository;

import com.tontinemarche.domain.entity.Agent;
import com.tontinemarche.domain.enums.StatutEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentRepository extends JpaRepository<Agent, Long> {
    Optional<Agent> findByCode(String code);

    @EntityGraph(attributePaths = {"marches", "marches.agence", "agence"})
    Optional<Agent> findByUtilisateurId(Long utilisateurId);
    List<Agent> findByAgenceId(Long agenceId);
    List<Agent> findByAgenceIdAndStatut(Long agenceId, StatutEntity statut);
    long countByAgenceId(Long agenceId);
    long countByStatut(StatutEntity statut);
}
