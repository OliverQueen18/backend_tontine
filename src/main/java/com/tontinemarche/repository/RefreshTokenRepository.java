package com.tontinemarche.repository;

import com.tontinemarche.domain.entity.RefreshToken;
import com.tontinemarche.domain.entity.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);

    Optional<RefreshToken> findByUtilisateurId(Long utilisateurId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM RefreshToken rt WHERE rt.utilisateur.id = :userId")
    void deleteByUtilisateurId(@Param("userId") Long userId);
}
