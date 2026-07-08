package com.tontinemarche.repository;

import com.tontinemarche.domain.entity.Utilisateur;
import com.tontinemarche.domain.enums.RoleType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {
    @EntityGraph(attributePaths = "agence")
    Optional<Utilisateur> findByUsername(String username);

    @EntityGraph(attributePaths = "agence")
    List<Utilisateur> findAllByOrderByNomCompletAsc();

    @EntityGraph(attributePaths = "agence")
    List<Utilisateur> findByAgenceIdOrderByNomCompletAsc(Long agenceId);

    @EntityGraph(attributePaths = "agence")
    List<Utilisateur> findByAgenceId(Long agenceId);

    boolean existsByUsernameAndIdNot(String username, Long id);
    boolean existsByUsername(String username);
    List<Utilisateur> findByRole(RoleType role);
}
