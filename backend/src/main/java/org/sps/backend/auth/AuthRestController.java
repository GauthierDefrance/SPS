package org.sps.backend.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.sps.backend.user.MeResponse;

import java.time.Duration;

/*
 * Cycle d'authentification côté client :
 *  1. POST /api/auth/login    -> token d'accès (15 min) dans la réponse + refresh token dans un cookie
 *  2. Chaque requête          -> en-tête "Authorization: Bearer <token d'accès>"
 *  3. Réponse 401 ou token proche de l'expiration
 *                             -> POST /api/auth/refresh : le navigateur envoie le cookie tout seul,
 *                                on reçoit un nouveau token d'accès (et un nouveau cookie)
 *  4. POST /api/auth/logout   -> le refresh token est révoqué et le cookie effacé
 *
 * Le cookie est :
 *  - HttpOnly       : invisible pour le JavaScript de la page, donc pas volable par une faille XSS
 *  - Secure         : envoyé seulement en HTTPS (les navigateurs l'acceptent aussi sur http://localhost)
 *  - SameSite=Strict: jamais envoyé depuis un autre site, ce qui bloque les attaques CSRF
 *  - Path=/api/auth : envoyé uniquement aux routes d'authentification, pas à toute l'API
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthRestController {

    static final String REFRESH_COOKIE = "sps_refresh";

    private final AuthService authService;
    private final JwtProperties props;

    /**
     * POST /api/auth/register
     * {"email": "ada@sps.dev", "password": "motdepasse123", "name": "Ada", "surname": "Lovelace"}
     * 201 avec le profil créé, 400 si un champ est invalide, 409 si l'email est déjà pris.
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public MeResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    /** POST /api/auth/login {"email": "...", "password": "..."} : 200 avec les tokens, 401 sinon. */
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return withRefreshCookie(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        return withRefreshCookie(authService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())   // Max-Age=0 : effacé
                .build();
    }

    private ResponseEntity<TokenResponse> withRefreshCookie(AuthService.Session session) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(session.refreshToken(), props.refreshTokenTtl()).toString())
                .body(session.access());
    }

    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(props.cookieSecure())
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(maxAge)
                .build();
    }
}
