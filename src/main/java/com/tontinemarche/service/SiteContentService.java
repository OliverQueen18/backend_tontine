package com.tontinemarche.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tontinemarche.domain.entity.SiteSection;
import com.tontinemarche.dto.SiteContentUpdateDto;
import com.tontinemarche.dto.SiteSectionDto;
import com.tontinemarche.exception.ApiException;
import com.tontinemarche.repository.SiteSectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SiteContentService {

    private static final String DEFAULT_LOCALE = "fr";

    private final SiteSectionRepository repository;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Map<String, Map<String, Object>> getPublicContent(String locale) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        List<SiteSection> sections = repository.findByLocaleOrderBySortOrderAsc(locale);
        if (sections.isEmpty()) {
            return buildDefaultContentMap();
        }
        sections.forEach(section ->
                result.put(section.getSectionKey(), parseContent(section.getContentJson()))
        );
        return result;
    }

    @Transactional
    public List<SiteSectionDto> getAllSections(String locale) {
        if (repository.findByLocaleOrderBySortOrderAsc(locale).isEmpty()) {
            seedDefaults(locale);
        }
        return repository.findByLocaleOrderBySortOrderAsc(locale).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public SiteSectionDto getSection(String sectionKey, String locale) {
        SiteSection section = repository.findBySectionKeyAndLocale(sectionKey, locale)
                .orElseThrow(() -> ApiException.notFound("Section introuvable: " + sectionKey));
        return toDto(section);
    }

    @Transactional
    public SiteSectionDto updateSection(String sectionKey, String locale, SiteContentUpdateDto dto) {
        if (repository.findBySectionKeyAndLocale(sectionKey, locale).isEmpty()) {
            seedDefaults(locale);
        }
        SiteSection section = repository.findBySectionKeyAndLocale(sectionKey, locale)
                .orElseThrow(() -> ApiException.notFound("Section introuvable: " + sectionKey));

        try {
            section.setContentJson(objectMapper.writeValueAsString(dto.content()));
        } catch (Exception e) {
            throw ApiException.badRequest("Contenu JSON invalide");
        }

        SiteSection saved = repository.save(section);
        auditService.log("MODIFICATION", "SiteSection", sectionKey, "Mise à jour section site public", null);
        return toDto(saved);
    }

    private Map<String, Map<String, Object>> buildDefaultContentMap() {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        result.put("layout", defaultLayout());
        result.put("hero", defaultHero());
        result.put("features", defaultFeatures());
        result.put("about", defaultAbout());
        result.put("steps", defaultSteps());
        result.put("cta", defaultCta());
        result.put("collecteur", defaultCollecteur());
        return result;
    }

    @Transactional
    public void ensureDefaults(String locale) {
        if (!repository.findByLocaleOrderBySortOrderAsc(locale).isEmpty()) {
            return;
        }
        seedDefaults(locale);
    }

    @Transactional
    public void seedDefaults(String locale) {
        log.info("Initialisation du contenu public par défaut...");
        List<SiteSection> defaults = List.of(
                section("layout", "En-tête & Pied de page", 1, locale, defaultLayout()),
                section("hero", "Section Hero", 2, locale, defaultHero()),
                section("features", "Barre avantages", 3, locale, defaultFeatures()),
                section("about", "À propos", 4, locale, defaultAbout()),
                section("steps", "Comment ça marche", 5, locale, defaultSteps()),
                section("cta", "Appel à l'action", 6, locale, defaultCta()),
                section("collecteur", "Devenez collecteur", 7, locale, defaultCollecteur())
        );
        repository.saveAll(defaults);
    }

    @Transactional
    public void ensureCollecteurSection(String locale) {
        if (repository.findBySectionKeyAndLocale("collecteur", locale).isPresent()) {
            return;
        }
        repository.save(section("collecteur", "Devenez collecteur", 7, locale, defaultCollecteur()));
        log.info("Section collecteur ajoutée au contenu public");
    }

    private SiteSection section(String key, String label, int order, String locale, Map<String, Object> content) {
        try {
            return SiteSection.builder()
                    .sectionKey(key)
                    .label(label)
                    .sortOrder(order)
                    .locale(locale)
                    .contentJson(objectMapper.writeValueAsString(content))
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Erreur sérialisation contenu par défaut", e);
        }
    }

    private SiteSectionDto toDto(SiteSection section) {
        return new SiteSectionDto(
                section.getId(),
                section.getSectionKey(),
                section.getLabel(),
                section.getLocale(),
                parseContent(section.getContentJson()),
                section.getUpdatedAt()
        );
    }

    private Map<String, Object> parseContent(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Contenu JSON invalide pour une section, retour objet vide");
            return Map.of();
        }
    }

    private Map<String, Object> defaultLayout() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("phone", "+223 70 12 34 56");
        m.put("email", "contact@tontinemarche.ml");
        m.put("socialLabel", "Suivez-nous :");
        m.put("facebook", "#");
        m.put("instagram", "#");
        m.put("whatsapp", "#");
        m.put("youtube", "#");
        m.put("brandName", "Tontine Marché");
        m.put("brandTagline", "Épargnez, réalisez, prospérez");
        m.put("brandIconUrl", "icone-tontine-marche.png");
        m.put("footerLogoUrl", "logo-tontine-marche.png");
        m.put("footerDescription", "La tontine réinventée pour les commerçants et travailleurs indépendants d'Afrique de l'Ouest.");
        m.put("footerAddress", "Bamako, Mali");
        m.put("footerCopyright", "© 2026 Tontine Marché — Tous droits réservés");
        m.put("loginButtonLabel", "Espace Collecteur");
        m.put("contactButtonLabel", "Devenez Collecteur");
        m.put("collecteurRoute", "/devenez-collecteur");
        return m;
    }

    private Map<String, Object> defaultHero() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("highlightGreen", "Épargnez");
        m.put("highlightOrange", "réalisez");
        m.put("highlightRed", "demain");
        m.put("headlineMiddle", "aujourd'hui, vos projets");
        m.put("subtitle", "Tontine Marché est la solution digitale sécurisée qui modernise la tontine traditionnelle. Épargnez au quotidien, suivez vos versements en temps réel et récupérez votre épargne en toute confiance.");
        m.put("ctaPrimaryLabel", "Commencer à épargner");
        m.put("ctaPrimaryRoute", "/connexion");
        m.put("ctaSecondaryLabel", "Découvrir la plateforme");
        m.put("ctaSecondaryRoute", "/fonctionnalites");
        m.put("avatars", List.of("A", "F", "M", "S"));
        m.put("socialProofPrefix", "Rejoignez déjà plus de");
        m.put("socialProofCount", "5 000");
        m.put("socialProofSuffix", "personnes qui nous font confiance");
        m.put("txTitle", "Épargne du jour");
        m.put("txAmount", "1 000 FCFA");
        m.put("txStatus", "Collecte réussie");
        m.put("txTime", "Aujourd'hui à 08:45");
        m.put("backgroundImageUrl", "https://images.unsplash.com/photo-1593113598332-cd288d649329?w=1600&q=80");
        m.put("personImageUrl", "https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?w=600&q=80");
        return m;
    }

    private Map<String, Object> defaultFeatures() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("items", List.of(
                featureItem("pi pi-shield", "Sécurisé", "Vos données et votre épargne protégées", "#dcfce7", "#166534"),
                featureItem("pi pi-clock", "Accessible", "Disponible 24h/24, 7j/7", "#ffedd5", "#c2410c"),
                featureItem("pi pi-chart-bar", "Transparent", "Suivi en temps réel de vos versements", "#dcfce7", "#166534"),
                featureItem("pi pi-wallet", "Flexible", "Montants adaptés à votre rythme", "#fee2e2", "#b91c1c"),
                featureItem("pi pi-headphones", "Accompagnement", "Une équipe à votre écoute", "#dcfce7", "#166534")
        ));
        return m;
    }

    private Map<String, Object> defaultAbout() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", "À PROPOS DE NOUS");
        m.put("title", "La tontine réinventée pour vous");
        m.put("paragraph1", "Depuis des générations, la tontine est le pilier de l'épargne en Afrique de l'Ouest. Tontine Marché modernise cette tradition en offrant une plateforme digitale sécurisée, transparente et accessible à tous les commerçants et travailleurs indépendants.");
        m.put("paragraph2", "Fini les carnets papier, les erreurs de calcul et les pertes de traçabilité. Avec Tontine Marché, chaque versement est enregistré, signé et suivi en temps réel.");
        m.put("ctaLabel", "En savoir plus");
        m.put("ctaRoute", "/a-propos");
        m.put("badgeCount", "+5 000");
        m.put("badgeLabel", "Utilisateurs satisfaits");
        m.put("imageUrl", "https://images.unsplash.com/photo-1522071820081-009f0129c71c?w=800&q=80");
        return m;
    }

    private Map<String, Object> defaultSteps() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", "COMMENT ÇA MARCHE");
        m.put("title", "Épargner n'a jamais été aussi simple");
        m.put("items", List.of(
                stepItem("01", "pi pi-user-plus", "Inscription", "Créez votre compte en quelques minutes", "#dcfce7", "#166534"),
                stepItem("02", "pi pi-wallet", "Épargne quotidienne", "Versez votre montant quotidiennement", "#ffedd5", "#c2410c"),
                stepItem("03", "pi pi-calendar", "Suivi en temps réel", "Consultez vos versements et votre progression", "#dcfce7", "#166534"),
                stepItem("04", "pi pi-gift", "Recevez votre épargne", "Récupérez votre épargne à la date convenue", "#fee2e2", "#b91c1c")
        ));
        return m;
    }

    private Map<String, Object> defaultCta() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", "Prêt à commencer votre épargne ?");
        m.put("subtitle", "Rejoignez Tontine Marché et prenez le contrôle de vos finances dès aujourd'hui.");
        m.put("buttonLabel", "Créer mon compte");
        m.put("buttonRoute", "/connexion");
        return m;
    }

    private Map<String, Object> defaultCollecteur() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", "Rejoignez le réseau Tontine Marché");
        m.put("subtitle", "Créez votre agence de collecte et accompagnez vos clients vers une épargne sécurisée.");
        m.put("formTitle", "Créer mon agence");
        m.put("formSubtitle", "Renseignez les informations de votre agence et de l'administrateur.");
        m.put("conditionsUtilisation", """
                En soumettant cette demande, vous acceptez les conditions suivantes :

                1. Vous vous engagez à respecter la réglementation locale en matière de collecte d'épargne.
                2. Les fonds collectés appartiennent aux clients et doivent être restitués selon les règles de la plateforme.
                3. Tontine Marché se réserve le droit de valider ou refuser toute demande d'inscription.
                4. Les frais de création d'agence ne sont pas remboursables après validation.
                5. Vous garantissez l'exactitude des informations et documents fournis.
                """);
        m.put("promos", List.of(
                promoItem("pi pi-chart-line", "Développez votre activité",
                        "Gérez vos collecteurs, suivez les versements et fidélisez vos clients commerçants.",
                        "#dcfce7", "#166534"),
                promoItem("pi pi-shield", "Plateforme sécurisée",
                        "Chaque opération est tracée, signée et archivée pour une transparence totale.",
                        "#ffedd5", "#c2410c"),
                promoItem("pi pi-users", "Réseau de confiance",
                        "Rejoignez des centaines d'agences qui modernisent la tontine en Afrique de l'Ouest.",
                        "#fee2e2", "#b91c1c")
        ));
        m.put("conduct", List.of(
                conductItem("Intégrité", "Ne détournez jamais les fonds collectés. Chaque franc doit être tracé."),
                conductItem("Respect du client", "Traitez chaque commerçant avec courtoisie et professionnalisme."),
                conductItem("Ponctualité", "Effectuez vos collectes aux horaires convenus avec vos clients."),
                conductItem("Confidentialité", "Protégez les données personnelles et financières de vos clients."),
                conductItem("Transparence", "Remettez un reçu à chaque versement et expliquez clairement les commissions.")
        ));
        return m;
    }

    private Map<String, Object> promoItem(String icon, String title, String text, String bg, String color) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("icon", icon);
        item.put("title", title);
        item.put("text", text);
        item.put("bg", bg);
        item.put("color", color);
        return item;
    }

    private Map<String, Object> conductItem(String title, String text) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("title", title);
        item.put("text", text);
        return item;
    }

    private Map<String, Object> featureItem(String icon, String title, String desc, String bg, String color) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("icon", icon);
        item.put("title", title);
        item.put("desc", desc);
        item.put("bg", bg);
        item.put("color", color);
        return item;
    }

    private Map<String, Object> stepItem(String num, String icon, String title, String desc, String bg, String color) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("num", num);
        item.put("icon", icon);
        item.put("title", title);
        item.put("desc", desc);
        item.put("bg", bg);
        item.put("color", color);
        return item;
    }
}
