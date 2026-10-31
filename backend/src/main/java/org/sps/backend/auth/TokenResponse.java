package org.sps.backend.auth;

/**
 * Réponse de login et refresh. Le refresh token n'y figure pas : il part dans un cookie HttpOnly,
 * que le JavaScript de la page ne peut pas lire (protection contre le vol par une faille XSS).
 *
 * @param accessToken à envoyer dans l'en-tête "Authorization: Bearer <accessToken>"
 * @param expiresIn   durée de validité en secondes : le client rafraîchit un peu avant
 */
public record TokenResponse(String accessToken, String tokenType, long expiresIn) {

    public static TokenResponse bearer(String accessToken, long expiresIn) {
        return new TokenResponse(accessToken, "Bearer", expiresIn);
    }
}
