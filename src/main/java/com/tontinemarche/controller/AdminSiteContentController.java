package com.tontinemarche.controller;

import com.tontinemarche.dto.SiteContentUpdateDto;
import com.tontinemarche.dto.SiteSectionDto;
import com.tontinemarche.service.MediaStorageService;
import com.tontinemarche.service.SiteContentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AdminSiteContentController {

    private final SiteContentService siteContentService;
    private final MediaStorageService mediaStorageService;

    @GetMapping("/api/admin/site-content")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public List<SiteSectionDto> getAll(
            @RequestParam(defaultValue = "fr") String locale
    ) {
        return siteContentService.getAllSections(locale);
    }

    @GetMapping("/api/admin/site-content/{sectionKey}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public SiteSectionDto getOne(
            @PathVariable String sectionKey,
            @RequestParam(defaultValue = "fr") String locale
    ) {
        return siteContentService.getSection(sectionKey, locale);
    }

    @PutMapping("/api/admin/site-content/{sectionKey}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public SiteSectionDto update(
            @PathVariable String sectionKey,
            @RequestParam(defaultValue = "fr") String locale,
            @Valid @RequestBody SiteContentUpdateDto dto
    ) {
        return siteContentService.updateSection(sectionKey, locale, dto);
    }

    @PostMapping("/api/admin/media/upload")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Map<String, String> upload(@RequestParam("file") MultipartFile file) {
        String url = mediaStorageService.store(file);
        return Map.of("url", url);
    }

    @GetMapping("/api/public/uploads/{filename}")
    public ResponseEntity<Resource> serveUpload(@PathVariable String filename) throws Exception {
        Path path = mediaStorageService.resolve(filename);
        Resource resource = new UrlResource(path.toUri());
        String contentType = Files.probeContentType(path);
        if (contentType == null) {
            contentType = "application/octet-stream";
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(resource);
    }
}
