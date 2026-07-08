package com.tontinemarche.dto.auth;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OtpResponse {
    private String message;
    private String maskedEmail;
    private int expiresInSeconds;
    private int step;
    private String resetToken;
}
