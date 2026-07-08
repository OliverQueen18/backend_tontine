package com.tontinemarche.service;

import com.tontinemarche.domain.entity.Notification;
import com.tontinemarche.domain.entity.Agent;
import com.tontinemarche.domain.entity.Agence;
import com.tontinemarche.domain.entity.Client;
import com.tontinemarche.domain.entity.Collecte;
import com.tontinemarche.domain.entity.Restitution;
import com.tontinemarche.domain.entity.Utilisateur;
import com.tontinemarche.domain.enums.RoleType;
import com.tontinemarche.domain.enums.StatutEntity;
import com.tontinemarche.repository.NotificationRepository;
import com.tontinemarche.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final EmailService emailService;

    @Value("${app.public.api-base-url:http://localhost:8081}")
    private String apiBaseUrl;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> forUser(Long utilisateurId) {
        return notificationRepository.findByUtilisateurIdOrderByDateNotificationDesc(utilisateurId)
                .stream()
                .map(this::toMap)
                .toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long utilisateurId) {
        return notificationRepository.countByUtilisateurIdAndLueFalse(utilisateurId);
    }

    @Transactional
    public void markAsRead(Long notificationId, Long utilisateurId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            if (n.getUtilisateur().getId().equals(utilisateurId)) {
                n.setLue(true);
                notificationRepository.save(n);
            }
        });
    }

    @Transactional
    public void notifyUsers(List<Utilisateur> users, String type, String titre, String message) {
        Instant now = Instant.now();
        for (Utilisateur user : users) {
            if (user == null || user.getStatut() != StatutEntity.ACTIF) {
                continue;
            }
            notificationRepository.save(Notification.builder()
                    .utilisateur(user)
                    .titre(titre)
                    .message(message)
                    .type(type)
                    .lue(false)
                    .dateNotification(now)
                    .build());

            if (user.getEmail() != null && !user.getEmail().isBlank()) {
                emailService.send(user.getEmail(), "[Tontine Marché] " + titre, buildHtml(titre, message, user.getNomComplet()));
            }
        }
    }

    @Transactional
    public void notifyAgenceStaff(Long agenceId, String type, String titre, String message, RoleType... roles) {
        List<Utilisateur> recipients = new ArrayList<>();
        if (agenceId != null) {
            for (Utilisateur u : utilisateurRepository.findByAgenceId(agenceId)) {
                if (matchesRole(u, roles)) {
                    recipients.add(u);
                }
            }
        }
        for (RoleType role : roles) {
            if (role == RoleType.SUPER_ADMIN || role == RoleType.AUDITEUR) {
                recipients.addAll(utilisateurRepository.findByRole(role));
            }
        }
        notifyUsers(dedupe(recipients), type, titre, message);
    }

    public void sendRestitutionNoticeToClient(Client client, Restitution restitution, Agent agent) {
        if (client.getEmail() == null || client.getEmail().isBlank()) {
            log.debug("Aucun email pour le client {} — avis restitution {} non envoyé",
                    client.getCode(), restitution.getNumeroRecu());
            return;
        }
        String subject = "[Tontine Marché] Restitution " + restitution.getNumeroRecu();
        emailService.send(client.getEmail(), subject, buildRestitutionNoticeHtml(client, restitution, agent));
    }

    public void sendCollecteReceiptToClient(Client client, Collecte collecte, Agent agent) {
        if (client.getEmail() == null || client.getEmail().isBlank()) {
            log.debug("Aucun email pour le client {} — reçu {} non envoyé", client.getCode(), collecte.getNumeroRecu());
            return;
        }
        String subject = "[Tontine Marché] Reçu de collecte " + collecte.getNumeroRecu();
        emailService.send(client.getEmail(), subject, buildCollecteReceiptHtml(client, collecte, agent));
    }

    public void sendPlainEmail(String email, String subject, String body) {
        if (email == null || email.isBlank()) {
            return;
        }
        String html = """
                <div style="font-family:Arial,sans-serif;max-width:560px;margin:0 auto;padding:24px;color:#0f172a;line-height:1.6">
                  %s
                </div>
                """.formatted(escape(body).replace("\n", "<br/>"));
        emailService.send(email, "[Tontine Marché] " + subject, html);
    }

    public void sendInscriptionApprovalEmail(com.tontinemarche.domain.entity.DemandeInscriptionAgence demande,
                                             String agenceCode) {
        if (demande.getEmail() == null || demande.getEmail().isBlank()) {
            return;
        }
        String subject = "Votre demande d'inscription a été approuvée";
        String html = """
                <div style="font-family:Arial,sans-serif;max-width:560px;margin:0 auto;padding:24px;color:#0f172a">
                  <div style="background:#0b1f3a;color:#fff;padding:16px 20px;border-radius:12px 12px 0 0">
                    <strong>Tontine Marché</strong>
                    <div style="font-size:13px;margin-top:4px;opacity:0.9">Demande approuvée</div>
                  </div>
                  <div style="border:1px solid #e2e8f0;border-top:none;padding:20px;border-radius:0 0 12px 12px">
                    <p>Bonjour %s,</p>
                    <p style="line-height:1.6">
                      Félicitations ! Votre demande de création d'agence <strong>« %s »</strong> a été <strong style="color:#166534">validée</strong>.
                    </p>
                    <table style="width:100%%;border-collapse:collapse;margin:16px 0;font-size:14px">
                      <tr><td style="padding:8px 0;color:#64748b">Code agence</td><td style="padding:8px 0;text-align:right"><strong>%s</strong></td></tr>
                      <tr><td style="padding:8px 0;color:#64748b">Identifiant</td><td style="padding:8px 0;text-align:right">%s</td></tr>
                      <tr><td style="padding:8px 0;color:#64748b">E-mail</td><td style="padding:8px 0;text-align:right">%s</td></tr>
                    </table>
                    <p style="line-height:1.6;background:#f0fdf4;border:1px solid #bbf7d0;border-radius:10px;padding:14px 16px;color:#166534">
                      Vous pouvez dès maintenant vous connecter à la plateforme Tontine Marché avec votre identifiant et le mot de passe choisi lors de l'inscription.
                    </p>
                    <p style="color:#64748b;font-size:13px;margin-top:24px">
                      Pour toute assistance, contactez le support Tontine Marché.
                    </p>
                  </div>
                </div>
                """.formatted(
                escape(demande.getNomComplet()),
                escape(demande.getAgenceNom()),
                escape(agenceCode),
                escape(demande.getUsername()),
                escape(demande.getEmail())
        );
        emailService.send(demande.getEmail(), "[Tontine Marché] " + subject, html);
    }

    public void sendInscriptionRejectionEmail(com.tontinemarche.domain.entity.DemandeInscriptionAgence demande) {
        if (demande.getEmail() == null || demande.getEmail().isBlank()) {
            return;
        }
        String subject = "Votre demande d'inscription a été refusée";
        String html = """
                <div style="font-family:Arial,sans-serif;max-width:560px;margin:0 auto;padding:24px;color:#0f172a">
                  <div style="background:#0b1f3a;color:#fff;padding:16px 20px;border-radius:12px 12px 0 0">
                    <strong>Tontine Marché</strong>
                    <div style="font-size:13px;margin-top:4px;opacity:0.9">Demande refusée</div>
                  </div>
                  <div style="border:1px solid #e2e8f0;border-top:none;padding:20px;border-radius:0 0 12px 12px">
                    <p>Bonjour %s,</p>
                    <p style="line-height:1.6">
                      Nous avons examiné votre demande pour l'agence <strong>« %s »</strong>.
                      Malheureusement, elle n'a pas été retenue.
                    </p>
                    <div style="background:#fef2f2;border:1px solid #fecaca;border-radius:10px;padding:14px 16px;margin:16px 0">
                      <div style="font-size:12px;font-weight:700;text-transform:uppercase;color:#991b1b;margin-bottom:6px">Motif</div>
                      <p style="margin:0;line-height:1.6;color:#7f1d1d">%s</p>
                    </div>
                    <p style="line-height:1.6;color:#64748b;font-size:14px">
                      Vous pouvez soumettre une nouvelle demande corrigée ou contacter le support pour plus d'informations.
                    </p>
                  </div>
                </div>
                """.formatted(
                escape(demande.getNomComplet()),
                escape(demande.getAgenceNom()),
                escape(orDash(demande.getMotifRejet()))
        );
        emailService.send(demande.getEmail(), "[Tontine Marché] " + subject, html);
    }

    private boolean matchesRole(Utilisateur u, RoleType... roles) {
        if (roles == null || roles.length == 0) {
            return true;
        }
        for (RoleType role : roles) {
            if (u.getRole() == role) {
                return true;
            }
        }
        return false;
    }

    private List<Utilisateur> dedupe(List<Utilisateur> users) {
        Map<Long, Utilisateur> map = new LinkedHashMap<>();
        for (Utilisateur u : users) {
            if (u != null && u.getId() != null) {
                map.putIfAbsent(u.getId(), u);
            }
        }
        return new ArrayList<>(map.values());
    }

    private Map<String, Object> toMap(Notification n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", n.getId());
        m.put("titre", n.getTitre());
        m.put("message", n.getMessage());
        m.put("type", n.getType());
        m.put("lue", n.isLue());
        m.put("dateNotification", n.getDateNotification());
        return m;
    }

    private String buildHtml(String titre, String message, String nom) {
        return """
                <div style="font-family:Arial,sans-serif;max-width:560px;margin:0 auto;padding:24px;color:#0f172a">
                  <div style="background:#0b1f3a;color:#fff;padding:16px 20px;border-radius:12px 12px 0 0">
                    <strong>Tontine Marché</strong>
                  </div>
                  <div style="border:1px solid #e2e8f0;border-top:none;padding:20px;border-radius:0 0 12px 12px">
                    <p>Bonjour %s,</p>
                    <h2 style="color:#0b1f3a;font-size:18px">%s</h2>
                    <p style="line-height:1.6">%s</p>
                    <p style="color:#64748b;font-size:13px;margin-top:24px">
                      Ceci est une notification automatique de la plateforme Tontine Marché.
                    </p>
                  </div>
                </div>
                """.formatted(escape(nom), escape(titre), escape(message).replace("\n", "<br/>"));
    }

    private String buildRestitutionNoticeHtml(Client client, Restitution restitution, Agent agent) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm");
        String dateHeure = restitution.getDateHeure() != null
                ? restitution.getDateHeure().format(dtf)
                : "";
        var agenceEntity = client.getAgence() != null ? client.getAgence() : restitution.getAgence();
        String agentNom = agent != null ? orDash(agent.getNomComplet()) : "votre agent collecteur";
        String agentTel = agent != null ? orDash(agent.getTelephone()) : "—";

        return """
                <div style="font-family:Arial,sans-serif;max-width:560px;margin:0 auto;padding:24px;color:#0f172a">
                  <div style="background:#0b1f3a;color:#fff;padding:16px 20px;border-radius:12px 12px 0 0">
                    <strong>Tontine Marché</strong>
                    <div style="font-size:13px;margin-top:4px;opacity:0.9">Avis de restitution</div>
                  </div>
                  <div style="border:1px solid #e2e8f0;border-top:none;padding:20px;border-radius:0 0 12px 12px">
                    %s
                    <p>Bonjour %s,</p>
                    <p style="line-height:1.6">Votre restitution a été effectuée par la caisse. Voici le détail :</p>
                    <table style="width:100%%;border-collapse:collapse;margin:16px 0;font-size:14px">
                      <tr><td style="padding:8px 0;color:#64748b">N° reçu</td><td style="padding:8px 0;text-align:right"><strong>%s</strong></td></tr>
                      <tr><td style="padding:8px 0;color:#64748b">Code client</td><td style="padding:8px 0;text-align:right">%s</td></tr>
                      <tr><td style="padding:8px 0;color:#64748b">Date</td><td style="padding:8px 0;text-align:right">%s</td></tr>
                      <tr><td style="padding:8px 0;color:#64748b">Total collecté</td><td style="padding:8px 0;text-align:right">%s FCFA</td></tr>
                      <tr><td style="padding:8px 0;color:#64748b">Commission</td><td style="padding:8px 0;text-align:right">%s FCFA</td></tr>
                      <tr style="border-top:1px solid #e2e8f0">
                        <td style="padding:12px 0;color:#0b1f3a"><strong>Montant restitué</strong></td>
                        <td style="padding:12px 0;text-align:right;color:#166534"><strong>%s FCFA</strong></td>
                      </tr>
                    </table>
                    <p style="line-height:1.6;background:#f8fafc;border:1px solid #e2e8f0;border-radius:10px;padding:14px 16px">
                      <strong>Prochaine étape :</strong> %s vous contactera pour recueillir votre signature
                      et finaliser la restitution.
                    </p>
                    <p style="color:#64748b;font-size:13px;margin-top:24px">
                      Agence : %s — Tél. %s
                    </p>
                  </div>
                </div>
                """.formatted(
                agencyLogoBlock(agenceEntity),
                escape(client.getNomComplet()),
                escape(restitution.getNumeroRecu()),
                escape(client.getCode()),
                escape(dateHeure),
                escape(formatDecimal(restitution.getTotalCollecte())),
                escape(formatDecimal(restitution.getCommission())),
                escape(formatDecimal(restitution.getMontantNet())),
                escape(agentNom),
                escape(orDash(agenceEntity != null ? agenceEntity.getNom() : null)),
                escape(agentTel)
        );
    }

    private String buildCollecteReceiptHtml(Client client, Collecte collecte, Agent agent) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm");
        String dateHeure = collecte.getDateHeure() != null
                ? collecte.getDateHeure().format(dtf)
                : "";
        var agenceEntity = client.getAgence() != null ? client.getAgence() : collecte.getAgence();
        String marche = client.getMarche() != null ? client.getMarche().getNom() : "—";
        String jours = formatDecimal(collecte.getNombreJoursPayes());
        String montantJournalier = formatDecimal(client.getMontantJournalier());
        String montantRecu = formatDecimal(collecte.getMontantRecu());
        String solde = formatDecimal(client.getSoldeEpargne());

        return """
                <div style="font-family:Arial,sans-serif;max-width:560px;margin:0 auto;padding:24px;color:#0f172a">
                  <div style="background:#0b1f3a;color:#fff;padding:16px 20px;border-radius:12px 12px 0 0">
                    <strong>Tontine Marché</strong>
                    <div style="font-size:13px;margin-top:4px;opacity:0.9">Confirmation de collecte</div>
                  </div>
                  <div style="border:1px solid #e2e8f0;border-top:none;padding:20px;border-radius:0 0 12px 12px">
                    %s
                    <p>Bonjour %s,</p>
                    <p style="line-height:1.6">Votre collecte a bien été enregistrée. Voici le détail :</p>
                    <table style="width:100%%;border-collapse:collapse;margin:16px 0;font-size:14px">
                      <tr><td style="padding:8px 0;color:#64748b">N° reçu</td><td style="padding:8px 0;text-align:right"><strong>%s</strong></td></tr>
                      <tr><td style="padding:8px 0;color:#64748b">Code client</td><td style="padding:8px 0;text-align:right">%s</td></tr>
                      <tr><td style="padding:8px 0;color:#64748b">Date</td><td style="padding:8px 0;text-align:right">%s</td></tr>
                      <tr><td style="padding:8px 0;color:#64748b">Marché</td><td style="padding:8px 0;text-align:right">%s</td></tr>
                      <tr><td style="padding:8px 0;color:#64748b">Montant journalier</td><td style="padding:8px 0;text-align:right">%s FCFA</td></tr>
                      <tr><td style="padding:8px 0;color:#64748b">Jours payés</td><td style="padding:8px 0;text-align:right">%s j</td></tr>
                      <tr style="border-top:1px solid #e2e8f0">
                        <td style="padding:12px 0;color:#0b1f3a"><strong>Montant collecté</strong></td>
                        <td style="padding:12px 0;text-align:right;color:#166534"><strong>%s FCFA</strong></td>
                      </tr>
                      <tr>
                        <td style="padding:8px 0;color:#0b1f3a"><strong>Solde épargne</strong></td>
                        <td style="padding:8px 0;text-align:right"><strong>%s FCFA</strong></td>
                      </tr>
                    </table>

                    <div style="background:#f8fafc;border:1px solid #e2e8f0;border-radius:10px;padding:14px 16px;margin:16px 0">
                      <div style="font-size:12px;font-weight:700;text-transform:uppercase;letter-spacing:0.05em;color:#64748b;margin-bottom:10px">Agence</div>
                      <table style="width:100%%;border-collapse:collapse;font-size:14px">
                        <tr><td style="padding:4px 0;color:#64748b">Nom</td><td style="padding:4px 0;text-align:right"><strong>%s</strong></td></tr>
                        <tr><td style="padding:4px 0;color:#64748b">Code</td><td style="padding:4px 0;text-align:right">%s</td></tr>
                        <tr><td style="padding:4px 0;color:#64748b">Responsable</td><td style="padding:4px 0;text-align:right">%s</td></tr>
                        <tr><td style="padding:4px 0;color:#64748b">Téléphone</td><td style="padding:4px 0;text-align:right">%s</td></tr>
                        <tr><td style="padding:4px 0;color:#64748b">E-mail</td><td style="padding:4px 0;text-align:right">%s</td></tr>
                        <tr><td style="padding:4px 0;color:#64748b">Adresse</td><td style="padding:4px 0;text-align:right">%s</td></tr>
                        <tr><td style="padding:4px 0;color:#64748b">Ville</td><td style="padding:4px 0;text-align:right">%s</td></tr>
                      </table>
                    </div>

                    <div style="background:#f8fafc;border:1px solid #e2e8f0;border-radius:10px;padding:14px 16px;margin:16px 0">
                      <div style="font-size:12px;font-weight:700;text-transform:uppercase;letter-spacing:0.05em;color:#64748b;margin-bottom:10px">Agent collecteur</div>
                      <table style="width:100%%;border-collapse:collapse;font-size:14px">
                        <tr><td style="padding:4px 0;color:#64748b">Nom</td><td style="padding:4px 0;text-align:right"><strong>%s</strong></td></tr>
                        <tr><td style="padding:4px 0;color:#64748b">Code</td><td style="padding:4px 0;text-align:right">%s</td></tr>
                        <tr><td style="padding:4px 0;color:#64748b">Téléphone</td><td style="padding:4px 0;text-align:right">%s</td></tr>
                      </table>
                    </div>

                    <p style="color:#64748b;font-size:13px;margin-top:24px">
                      Conservez ce message comme justificatif. Pour toute question, contactez votre agent collecteur ou votre agence aux coordonnées ci-dessus.
                    </p>
                  </div>
                </div>
                """.formatted(
                agencyLogoBlock(agenceEntity),
                escape(client.getNomComplet()),
                escape(collecte.getNumeroRecu()),
                escape(client.getCode()),
                escape(dateHeure),
                escape(marche),
                escape(montantJournalier),
                escape(jours),
                escape(montantRecu),
                escape(solde),
                escape(orDash(agenceEntity != null ? agenceEntity.getNom() : null)),
                escape(orDash(agenceEntity != null ? agenceEntity.getCode() : null)),
                escape(orDash(agenceEntity != null ? agenceEntity.getResponsable() : null)),
                escape(orDash(agenceEntity != null ? agenceEntity.getTelephone() : null)),
                escape(orDash(agenceEntity != null ? agenceEntity.getEmail() : null)),
                escape(orDash(agenceEntity != null ? agenceEntity.getAdresse() : null)),
                escape(orDash(agenceEntity != null ? agenceEntity.getVille() : null)),
                escape(orDash(agent.getNomComplet())),
                escape(orDash(agent.getCode())),
                escape(orDash(agent.getTelephone()))
        );
    }

    private String orDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private String agencyLogoBlock(Agence agence) {
        if (agence == null || agence.getLogoUrl() == null || agence.getLogoUrl().isBlank()) {
            return "";
        }
        String url = absoluteUrl(agence.getLogoUrl());
        return "<div style=\"text-align:center;margin-bottom:16px\"><img src=\"" + escape(url)
                + "\" alt=\"Logo agence\" style=\"max-height:56px;max-width:200px;object-fit:contain\" /></div>";
    }

    private String absoluteUrl(String path) {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        String base = apiBaseUrl.endsWith("/") ? apiBaseUrl.substring(0, apiBaseUrl.length() - 1) : apiBaseUrl;
        return base + (path.startsWith("/") ? path : "/" + path);
    }

    private String formatDecimal(java.math.BigDecimal value) {
        if (value == null) {
            return "0";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
