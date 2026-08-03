package com.tontinemarche.repository;

import com.tontinemarche.domain.entity.Agent;
import com.tontinemarche.domain.enums.StatutEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AgentRepository extends JpaRepository<Agent, Long> {
    Optional<Agent> findByCode(String code);

    @EntityGraph(attributePaths = {"marches", "marches.agence", "agence"})
    Optional<Agent> findByUtilisateurId(Long utilisateurId);
    List<Agent> findByAgenceId(Long agenceId);
    List<Agent> findByAgenceIdAndStatut(Long agenceId, StatutEntity statut);

    @EntityGraph(attributePaths = {"marches", "marches.agence", "agence"})
    @Query("SELECT a FROM Agent a WHERE a.agence.statut = :statut")
    List<Agent> findByAgenceStatut(@Param("statut") StatutEntity statut);

    @EntityGraph(attributePaths = {"marches", "marches.agence", "agence"})
    @Query("SELECT a FROM Agent a WHERE a.agence.id = :agenceId AND a.agence.statut = :statut")
    List<Agent> findByAgenceIdAndAgenceStatut(@Param("agenceId") Long agenceId,
                                              @Param("statut") StatutEntity statut);

    long countByAgenceId(Long agenceId);
    long countByStatut(StatutEntity statut);

    @Query("SELECT DISTINCT a FROM Agent a JOIN a.marches m WHERE m.id = :marcheId")
    List<Agent> findByMarcheId(@Param("marcheId") Long marcheId);
}
