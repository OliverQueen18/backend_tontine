package com.tontinemarche.controller;

import com.tontinemarche.dto.InscriptionCollecteurRequest;
import com.tontinemarche.dto.InscriptionOtpRequest;
import com.tontinemarche.dto.InscriptionVerifyOtpRequest;
import com.tontinemarche.dto.auth.OtpResponse;
import com.tontinemarche.service.InscriptionCollecteurService;
import com.tontinemarche.service.InscriptionOtpService;
import com.tontinemarche.service.MediaStorageService;
import com.tontinemarche.service.PlatformSettingsService;
import com.tontinemarche.service.SiteContentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    private final InscriptionCollecteurService inscriptionCollecteurService;
    private final InscriptionOtpService inscriptionOtpService;
    private final MediaStorageService mediaStorageService;
    private final PlatformSettingsService platformSettingsService;
    private final SiteContentService siteContentService;

    @GetMapping("/inscription-collecteur/config")
    public Map<String, Object> inscriptionConfig() {
        var settings = platformSettingsService.get();
        var content = siteContentService.getPublicContent("fr");
        var collecteur = content.getOrDefault("collecteur", Map.of());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fraisCreationAgence", settings.getFraisCreationAgence());
        result.put("telephonePaiementMobile", settings.getTelephonePaiementMobile());
        result.put("conditionsUtilisation", collecteur.getOrDefault("conditionsUtilisation", ""));
        return result;
    }

    @PostMapping("/media/upload")
    public Map<String, String> uploadLogo(@RequestParam("file") MultipartFile file) {
        return Map.of("url", mediaStorageService.store(file));
    }

    @PostMapping("/media/upload-document")
    public Map<String, String> uploadDocument(@RequestParam("file") MultipartFile file) {
        return Map.of("url", mediaStorageService.storeDocument(file));
    }

    @PostMapping("/inscription-collecteur/envoyer-otp")
    public OtpResponse envoyerOtp(@Valid @RequestBody InscriptionOtpRequest request) {
        return inscriptionOtpService.envoyerOtp(request);
    }

    @PostMapping("/inscription-collecteur/verifier-otp")
    public OtpResponse verifierOtp(@Valid @RequestBody InscriptionVerifyOtpRequest request) {
        return inscriptionOtpService.verifierOtp(request);
    }

    @PostMapping("/inscription-collecteur")
    public Map<String, Object> soumettre(@Valid @RequestBody InscriptionCollecteurRequest request) {
        return inscriptionCollecteurService.soumettre(request);
    }
}
