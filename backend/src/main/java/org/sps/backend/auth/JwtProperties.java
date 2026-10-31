package org.sps.backend.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Réglages des tokens, lus dans application.yaml (préfixe sps.jwt).
 *
 * @param secret          clé qui signe les tokens d'accès (HMAC-SHA256) : quiconque la connaît peut
 *                        fabriquer un token valide, elle ne doit jamais quitter le serveur
 * @param accessTokenTtl  durée de vie d'un token d'accès : courte, car il ne peut pas être révoqué
 * @param refreshTokenTtl durée de vie d'un refresh token (la "session" de l'utilisateur)
 * @param cookieSecure    ajoute l'attribut Secure au cookie : le navigateur ne l'envoie qu'en HTTPS
 */
@ConfigurationProperties(prefix = "sps.jwt")
public record JwtProperties(
        String secret,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        boolean cookieSecure
) {
    public JwtProperties {
        // HS256 exige une clé d'au moins 256 bits : on refuse de démarrer plutôt que d'être vulnérable
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("sps.jwt.secret (JWT_SECRET dans .env) doit faire au moins 32 caractères");
        }
    }
}
