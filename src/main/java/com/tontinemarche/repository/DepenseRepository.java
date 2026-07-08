package com.tontinemarche.repository;

import com.tontinemarche.domain.entity.Depense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface DepenseRepository extends JpaRepository<Depense, Long> {
    List<Depense> findByAgenceIdOrderByDateDepenseDesc(Long agenceId);
    List<Depense> findTop10ByOrderByDateDepenseDesc();
    List<Depense> findTop10ByAgenceIdOrderByDateDepenseDesc(Long agenceId);

    @Query("SELECT COALESCE(SUM(d.montant), 0) FROM Depense d WHERE d.agence.id = :agenceId AND d.dateDepense BETWEEN :debut AND :fin AND d.validee = true")
    BigDecimal sumByAgenceBetween(@Param("agenceId") Long agenceId,
                                  @Param("debut") LocalDate debut,
                                  @Param("fin") LocalDate fin);

    @Query("SELECT COALESCE(SUM(d.montant), 0) FROM Depense d WHERE d.dateDepense BETWEEN :debut AND :fin AND d.validee = true")
    BigDecimal sumBetween(@Param("debut") LocalDate debut, @Param("fin") LocalDate fin);

    @Query("SELECT COALESCE(SUM(d.montant), 0) FROM Depense d WHERE d.agence.id = :agenceId AND d.dateDepense BETWEEN :debut AND :fin AND d.validee = true AND d.sens = :sens")
    BigDecimal sumByAgenceBetweenAndSens(@Param("agenceId") Long agenceId,
                                         @Param("debut") LocalDate debut,
                                         @Param("fin") LocalDate fin,
                                         @Param("sens") com.tontinemarche.domain.enums.SensOperation sens);

    @Query("SELECT COALESCE(SUM(d.montant), 0) FROM Depense d WHERE d.dateDepense BETWEEN :debut AND :fin AND d.validee = true AND d.sens = :sens")
    BigDecimal sumBetweenAndSens(@Param("debut") LocalDate debut,
                                 @Param("fin") LocalDate fin,
                                 @Param("sens") com.tontinemarche.domain.enums.SensOperation sens);
}
