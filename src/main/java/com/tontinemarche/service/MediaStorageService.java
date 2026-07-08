package com.tontinemarche.service;

import com.tontinemarche.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class MediaStorageService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif", "image/svg+xml",
            "application/pdf"
    );

    private static final Set<String> DOCUMENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "application/pdf"
    );

    private final Path uploadDir;

    public MediaStorageService(@Value("${app.storage.upload-dir:uploads}") String uploadDir) {
        this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de créer le dossier uploads", e);
        }
    }

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Fichier vide");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw ApiException.badRequest("Type de fichier non autorisé (JPEG, PNG, WebP, GIF, SVG, PDF)");
        }
        return storeWithType(file, contentType);
    }

    public String storeDocument(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Fichier vide");
        }
        String contentType = file.getContentType();
        if (contentType == null || !DOCUMENT_TYPES.contains(contentType)) {
            throw ApiException.badRequest("Document non autorisé (JPEG, PNG, WebP ou PDF)");
        }
        return storeWithType(file, contentType);
    }

    private String storeWithType(MultipartFile file, String contentType) {
        String ext = extensionFromContentType(contentType);
        String filename = UUID.randomUUID() + ext;
        Path target = uploadDir.resolve(filename).normalize();
        if (!target.startsWith(uploadDir)) {
            throw ApiException.badRequest("Nom de fichier invalide");
        }
        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Erreur upload média", e);
            throw ApiException.badRequest("Erreur lors de l'enregistrement du fichier");
        }
        return "/api/public/uploads/" + filename;
    }

    public Path resolve(String filename) {
        Path resolved = uploadDir.resolve(filename).normalize();
        if (!resolved.startsWith(uploadDir)) {
            throw ApiException.notFound("Fichier introuvable");
        }
        if (!Files.exists(resolved)) {
            throw ApiException.notFound("Fichier introuvable");
        }
        return resolved;
    }

    private String extensionFromContentType(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            case "image/svg+xml" -> ".svg";
            case "application/pdf" -> ".pdf";
            default -> ".bin";
        };
    }
}
