package com.tontinemarche.repository;

import com.tontinemarche.domain.entity.Restitution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface RestitutionRepository extends JpaRepository<Restitution, Long> {
    List<Restitution> findByClientIdOrderByDateHeureDesc(Long clientId);
    List<Restitution> findTop10ByOrderByDateHeureDesc();
    List<Restitution> findTop10ByAgenceIdOrderByDateHeureDesc(Long agenceId);
    List<Restitution> findTop10ByClient_Agent_IdOrderByDateHeureDesc(Long agentId);
    List<Restitution> findByAgenceIdOrderByDateHeureDesc(Long agenceId);
    List<Restitution> findByClient_Agent_IdOrderByDateHeureDesc(Long agentId);
    List<Restitution> findByClient_Agent_IdAndValideeFalseOrderByDateHeureDesc(Long agentId);
    List<Restitution> findByAgenceIdAndValideeFalseOrderByDateHeureDesc(Long agenceId);
    List<Restitution> findByValideeFalseOrderByDateHeureDesc();
    boolean existsByClientIdAndValideeFalse(Long clientId);

    @Query("SELECT COALESCE(SUM(r.commission), 0) FROM Restitution r WHERE r.dateHeure BETWEEN :debut AND :fin")
    BigDecimal sumCommissionBetween(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);

    @Query("SELECT COALESCE(SUM(r.commission), 0) FROM Restitution r WHERE r.agence.id = :agenceId AND r.dateHeure BETWEEN :debut AND :fin")
    BigDecimal sumCommissionByAgenceBetween(@Param("agenceId") Long agenceId,
                                            @Param("debut") LocalDateTime debut,
                                            @Param("fin") LocalDateTime fin);
}
