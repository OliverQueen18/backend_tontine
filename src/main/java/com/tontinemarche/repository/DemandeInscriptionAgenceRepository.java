package com.tontinemarche.repository;

import com.tontinemarche.domain.entity.DemandeInscriptionAgence;
import com.tontinemarche.domain.enums.StatutDemandeInscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DemandeInscriptionAgenceRepository extends JpaRepository<DemandeInscriptionAgence, Long> {

    List<DemandeInscriptionAgence> findByStatutOrderByCreatedAtDesc(StatutDemandeInscription statut);

    List<DemandeInscriptionAgence> findAllByOrderByCreatedAtDesc();

    boolean existsByUsernameIgnoreCaseAndStatut(String username, StatutDemandeInscription statut);

    boolean existsByEmailIgnoreCaseAndStatut(String email, StatutDemandeInscription statut);

    long countByStatut(StatutDemandeInscription statut);
}
