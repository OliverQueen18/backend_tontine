package com.tontinemarche.service;

import com.tontinemarche.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
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
            // Vérifie tôt que le dossier est réellement inscriptible (volume Docker souvent root).
            Path probe = this.uploadDir.resolve(".write-test");
            Files.writeString(probe, "ok");
            Files.deleteIfExists(probe);
            log.info("Dossier uploads prêt : {}", this.uploadDir);
        } catch (AccessDeniedException e) {
            throw new IllegalStateException(
                    "Dossier uploads non inscriptible (" + this.uploadDir
                            + "). Vérifiez les permissions du volume / UPLOAD_DIR.", e);
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de créer le dossier uploads: " + this.uploadDir, e);
        }
    }

    public String store(MultipartFile file) {
        validateNotEmpty(file);
        String contentType = resolveContentType(file);
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw ApiException.badRequest(
                    "Type de fichier non autorisé (" + contentType + "). Formats : JPEG, PNG, WebP, GIF, SVG, PDF");
        }
        return storeWithType(file, contentType);
    }

    public String storeDocument(MultipartFile file) {
        validateNotEmpty(file);
        String contentType = resolveContentType(file);
        if (!DOCUMENT_TYPES.contains(contentType)) {
            throw ApiException.badRequest(
                    "Document non autorisé (" + contentType + "). Formats : JPEG, PNG, WebP ou PDF");
        }
        return storeWithType(file, contentType);
    }

    private void validateNotEmpty(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Fichier vide ou manquant");
        }
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
        } catch (AccessDeniedException e) {
            log.error("Permission refusée sur {}", uploadDir, e);
            throw new ApiException(
                    "Impossible d'enregistrer le fichier (permissions du dossier uploads). Contactez l'administrateur.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (IOException e) {
            log.error("Erreur upload média vers {}", target, e);
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

    /**
     * Les navigateurs envoient parfois un Content-Type vide ou application/octet-stream.
     * On déduit alors le type depuis l'extension du nom de fichier.
     */
    private String resolveContentType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType != null) {
            contentType = contentType.toLowerCase(Locale.ROOT).split(";")[0].trim();
            if ("image/jpg".equals(contentType)) {
                contentType = "image/jpeg";
            }
            if (ALLOWED_TYPES.contains(contentType) || DOCUMENT_TYPES.contains(contentType)) {
                return contentType;
            }
        }
        String name = file.getOriginalFilename();
        if (name != null) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
            if (lower.endsWith(".png")) return "image/png";
            if (lower.endsWith(".webp")) return "image/webp";
            if (lower.endsWith(".gif")) return "image/gif";
            if (lower.endsWith(".svg")) return "image/svg+xml";
            if (lower.endsWith(".pdf")) return "application/pdf";
        }
        return contentType != null ? contentType : "application/octet-stream";
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
