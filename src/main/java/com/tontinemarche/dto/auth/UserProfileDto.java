package com.tontinemarche.dto.auth;

import com.tontinemarche.domain.enums.RoleType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserProfileDto {
    private Long id;
    private String username;
    private String nomComplet;
    private String email;
    private String telephone;
    private String photoUrl;
    private RoleType role;
    private Long agenceId;
    private String agenceNom;
}
