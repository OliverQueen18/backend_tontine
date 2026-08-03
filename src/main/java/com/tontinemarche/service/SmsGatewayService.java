package com.tontinemarche.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Client de l'API V2 Android SMS Gateway (même passerelle que eRDV).
 * Les erreurs SMS sont journalisées sans interrompre les opérations métier.
 */
@Service
@Slf4j
public class SmsGatewayService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${sms.gateway.enabled:false}")
    private boolean enabled;

    @Value("${sms.gateway.api-key:}")
    private String apiKey;

    @Value("${sms.gateway.url:https://europe-west1-sms-gateway-api-simpapp.cloudfunctions.net/api_v2_sms_send}")
    private String gatewayUrl;

    @Value("${sms.gateway.request-timeout-seconds:10}")
    private long requestTimeoutSeconds;

    @Value("${sms.gateway.default-country-code:+223}")
    private String defaultCountryCode;

    public SmsGatewayService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /** SMS réellement envoyables (passerelle activée + clé API présente). */
    public boolean isReady() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    public boolean sendSms(String phoneNumber, String message) {
        if (!enabled) {
            return false;
        }
        if (phoneNumber == null || phoneNumber.isBlank() || message == null || message.isBlank()) {
            return false;
        }
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("SMS Gateway activé mais sms.gateway.api-key n'est pas configurée");
            return false;
        }

        String normalizedPhone = normalizePhoneNumber(phoneNumber);
        if (normalizedPhone == null) {
            log.warn("Numéro SMS invalide (E.164 requis): {}", phoneNumber);
            return false;
        }

        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "phoneNumber", normalizedPhone,
                    "message", message
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(gatewayUrl))
                    .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Échec SMS Gateway HTTP {} pour {}", response.statusCode(), maskPhone(normalizedPhone));
                return false;
            }

            JsonNode body = objectMapper.readTree(response.body());
            boolean success = body.path("success").asBoolean(false);
            if (!success) {
                log.warn("SMS Gateway a refusé le message pour {}", maskPhone(normalizedPhone));
            }
            return success;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Envoi SMS interrompu pour {}", maskPhone(normalizedPhone));
            return false;
        } catch (Exception e) {
            log.warn("Erreur SMS Gateway pour {}: {}", maskPhone(normalizedPhone), e.getMessage());
            return false;
        }
    }

    /**
     * Normalise un numéro local maliien (8 chiffres) ou international vers E.164.
     */
    public String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return null;
        }
        String normalized = phoneNumber.trim().replaceAll("[\\s().-]", "");
        if (normalized.startsWith("00")) {
            normalized = "+" + normalized.substring(2);
        }
        if (normalized.matches("\\d{8}")) {
            String cc = defaultCountryCode != null ? defaultCountryCode.trim() : "+223";
            if (!cc.startsWith("+")) {
                cc = "+" + cc;
            }
            normalized = cc + normalized;
        }
        if (!normalized.matches("^\\+[1-9]\\d{7,14}$")) {
            return null;
        }
        return normalized;
    }

    private String maskPhone(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 5) {
            return "***";
        }
        return phoneNumber.substring(0, 4) + "****"
                + phoneNumber.substring(phoneNumber.length() - 2);
    }
}
