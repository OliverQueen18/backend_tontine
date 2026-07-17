package com.tontinemarche.repository;

import com.tontinemarche.domain.entity.Collecte;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface CollecteRepository extends JpaRepository<Collecte, Long> {
    List<Collecte> findByClientIdOrderByDateHeureDesc(Long clientId);
    List<Collecte> findByAgentIdAndDateCollecte(Long agentId, LocalDate date);
    List<Collecte> findByAgenceIdAndDateCollecte(Long agenceId, LocalDate date);
    List<Collecte> findTop10ByOrderByDateHeureDesc();
    List<Collecte> findTop10ByAgenceIdOrderByDateHeureDesc(Long agenceId);

    @Query("SELECT COALESCE(SUM(c.montantRecu), 0) FROM Collecte c WHERE c.dateCollecte = :date AND c.annulee = false")
    BigDecimal sumByDate(@Param("date") LocalDate date);

    @Query("SELECT COALESCE(SUM(c.montantRecu), 0) FROM Collecte c WHERE c.agence.id = :agenceId AND c.dateCollecte = :date AND c.annulee = false")
    BigDecimal sumByAgenceAndDate(@Param("agenceId") Long agenceId, @Param("date") LocalDate date);

    @Query("SELECT COALESCE(SUM(c.montantRecu), 0) FROM Collecte c WHERE c.agent.id = :agentId AND c.dateCollecte = :date AND c.annulee = false")
    BigDecimal sumByAgentAndDate(@Param("agentId") Long agentId, @Param("date") LocalDate date);

    @Query("SELECT COALESCE(SUM(c.montantRecu), 0) FROM Collecte c WHERE c.agent.id = :agentId AND c.dateCollecte BETWEEN :debut AND :fin AND c.annulee = false")
    BigDecimal sumByAgentBetween(@Param("agentId") Long agentId, @Param("debut") LocalDate debut, @Param("fin") LocalDate fin);

    List<Collecte> findTop10ByAgentIdOrderByDateHeureDesc(Long agentId);

    long countByAgentIdAndSignatureClientIsNotNull(Long agentId);

    @Query("SELECT COALESCE(SUM(c.montantRecu), 0) FROM Collecte c WHERE c.agence.id = :agenceId AND c.agent.id = :agentId AND c.dateCollecte = :date AND c.annulee = false")
    BigDecimal sumByAgenceAndAgentAndDate(@Param("agenceId") Long agenceId, @Param("agentId") Long agentId, @Param("date") LocalDate date);

    @Query("SELECT COALESCE(SUM(c.montantRecu), 0) FROM Collecte c WHERE c.dateCollecte BETWEEN :debut AND :fin AND c.annulee = false")
    BigDecimal sumBetween(@Param("debut") LocalDate debut, @Param("fin") LocalDate fin);

    @Query("SELECT COALESCE(SUM(c.montantRecu), 0) FROM Collecte c WHERE c.agence.id = :agenceId AND c.dateCollecte BETWEEN :debut AND :fin AND c.annulee = false")
    BigDecimal sumByAgenceBetween(@Param("agenceId") Long agenceId, @Param("debut") LocalDate debut, @Param("fin") LocalDate fin);

    @Query("SELECT COALESCE(SUM(c.montantRecu), 0) FROM Collecte c WHERE c.client.id = :clientId AND c.annulee = false")
    BigDecimal sumByClient(@Param("clientId") Long clientId);

    long countByDateCollecte(LocalDate date);
    long countBySignatureClientIsNotNull();

    @Query("""
            SELECT c FROM Collecte c
            WHERE (:agenceId IS NULL OR c.agence.id = :agenceId)
            AND (:agentId IS NULL OR c.agent.id = :agentId)
            AND (:clientId IS NULL OR c.client.id = :clientId)
            AND (:debut IS NULL OR c.dateCollecte >= :debut)
            AND (:fin IS NULL OR c.dateCollecte <= :fin)
            ORDER BY c.dateHeure DESC
            """)
    List<Collecte> filter(@Param("agenceId") Long agenceId,
                          @Param("agentId") Long agentId,
                          @Param("clientId") Long clientId,
                          @Param("debut") LocalDate debut,
                          @Param("fin") LocalDate fin);
}
