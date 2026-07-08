package com.tontinemarche.config;

import com.tontinemarche.domain.entity.Marche;
import com.tontinemarche.repository.MarcheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Locale;

@Component
@RequiredArgsConstructor
@Slf4j
public class MarcheCodeInitializer implements CommandLineRunner {

    private final MarcheRepository marcheRepository;

    @Override
    @Transactional
    public void run(String... args) {
        marcheRepository.findAll().forEach(m -> {
            if (m.getCode() == null || m.getCode().isBlank()) {
                m.setCode(generateCode(m.getNom()));
                marcheRepository.save(m);
                log.info("Code marché généré: {} -> {}", m.getNom(), m.getCode());
            }
        });
    }

    public static String generateCode(String nom) {
        if (nom == null || nom.isBlank()) {
            return "MARCHE";
        }
        String normalized = Normalizer.normalize(nom, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]", "");
        if (normalized.isEmpty()) {
            return "MARCHE";
        }
        return normalized.length() > 10 ? normalized.substring(0, 10) : normalized;
    }
}
