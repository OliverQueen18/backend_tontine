package com.tontinemarche.repository;

import com.tontinemarche.domain.entity.Client;
import com.tontinemarche.domain.enums.StatutEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {
    Optional<Client> findByCode(String code);
    List<Client> findByAgenceId(Long agenceId);
    List<Client> findByAgentId(Long agentId);
    List<Client> findByAgentIdAndStatut(Long agentId, StatutEntity statut);

    @EntityGraph(attributePaths = "agence")
    List<Client> findByAgenceIdAndStatutAndSupprimeFalse(Long agenceId, StatutEntity statut);

    @EntityGraph(attributePaths = "agence")
    List<Client> findByStatutAndSupprimeFalse(StatutEntity statut);
    long countByAgenceId(Long agenceId);
    long countByAgentId(Long agentId);
    long countByStatut(StatutEntity statut);

    @Query("SELECT COUNT(c) FROM Client c WHERE c.agence.id = :agenceId AND c.code LIKE :prefix%")
    long countByAgenceAndCodePrefix(@Param("agenceId") Long agenceId, @Param("prefix") String prefix);

    long countByMarcheId(Long marcheId);

    @Query("""
            SELECT c FROM Client c
            WHERE (:agenceId IS NULL OR c.agence.id = :agenceId)
            AND (:agentId IS NULL OR c.agent.id = :agentId)
            AND c.supprime = false
            AND (
                :q IS NULL OR :q = '' OR
                LOWER(c.code) LIKE LOWER(CONCAT('%', :q, '%')) OR
                LOWER(c.nomComplet) LIKE LOWER(CONCAT('%', :q, '%')) OR
                LOWER(c.telephone) LIKE LOWER(CONCAT('%', :q, '%'))
            )
            ORDER BY c.nomComplet
            """)
    List<Client> search(@Param("q") String q, @Param("agenceId") Long agenceId, @Param("agentId") Long agentId);
}
