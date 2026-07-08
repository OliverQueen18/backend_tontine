package com.tontinemarche.controller;

import com.tontinemarche.service.SiteContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/public/content")
@RequiredArgsConstructor
public class PublicSiteContentController {

    private final SiteContentService siteContentService;

    @GetMapping
    public Map<String, Map<String, Object>> getAll(
            @RequestParam(defaultValue = "fr") String locale
    ) {
        return siteContentService.getPublicContent(locale);
    }
}
