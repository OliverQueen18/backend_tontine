package com.tontinemarche.dto.auth;

import com.tontinemarche.domain.enums.RoleType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Long id;
    private String username;
    private String nomComplet;
    private RoleType role;
    private Long agenceId;
    private String agenceNom;
}
