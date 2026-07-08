package com.tontinemarche.repository;

import com.tontinemarche.domain.entity.PasswordResetOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PasswordResetOtpRepository extends JpaRepository<PasswordResetOtp, Long> {

    Optional<PasswordResetOtp> findTopByUtilisateurIdAndUsedFalseOrderByCreatedAtDesc(Long utilisateurId);

    Optional<PasswordResetOtp> findByResetTokenAndUsedFalse(String resetToken);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PasswordResetOtp o SET o.used = true WHERE o.utilisateur.id = :userId AND o.used = false")
    void invalidateAllForUser(@Param("userId") Long userId);
}
