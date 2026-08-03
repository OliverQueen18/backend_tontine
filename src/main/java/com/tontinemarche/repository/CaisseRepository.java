package com.tontinemarche.repository;

import com.tontinemarche.domain.entity.Caisse;
import com.tontinemarche.domain.enums.StatutCaisse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CaisseRepository extends JpaRepository<Caisse, Long> {
    Optional<Caisse> findByAgenceIdAndDateCaisse(Long agenceId, LocalDate date);
    Optional<Caisse> findByAgenceIdAndStatut(Long agenceId, StatutCaisse statut);
    List<Caisse> findByAgenceIdOrderByDateCaisseDesc(Long agenceId);

    Optional<Caisse> findFirstByAgenceIdOrderByDateCaisseDesc(Long agenceId);

    Optional<Caisse> findFirstByAgenceIdAndDateCaisseLessThanOrderByDateCaisseDesc(
            Long agenceId, LocalDate date);

    Optional<Caisse> findFirstByAgenceIdAndDateCaisseGreaterThanOrderByDateCaisseAsc(
            Long agenceId, LocalDate date);

    List<Caisse> findByAgenceIdAndDateCaisseGreaterThanOrderByDateCaisseAsc(
            Long agenceId, LocalDate date);

    List<Caisse> findByAgenceIdAndStatutOrderByDateCaisseAsc(Long agenceId, StatutCaisse statut);

    List<Caisse> findByAgenceIdAndStatutAndDateCaisseLessThanOrderByDateCaisseAsc(
            Long agenceId, StatutCaisse statut, LocalDate date);

    List<Caisse> findByAgenceIdAndDateCaisseBetweenOrderByDateCaisseDesc(
            Long agenceId, LocalDate debut, LocalDate fin);
}
