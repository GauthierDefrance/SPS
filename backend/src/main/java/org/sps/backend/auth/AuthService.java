package org.sps.backend.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.sps.backend.user.MeResponse;
import org.sps.backend.user.User;
import org.sps.backend.user.UserRepository;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final RefreshTokenRepository refreshTokens;
    private final JwtProperties props;

    /** Le résultat d'une connexion : le token d'accès pour la réponse, le refresh token pour le cookie. */
    public record Session(TokenResponse access, String refreshToken) { }

    // --- Inscription ---

    /**
     * Crée un compte client. L'email est enregistré en minuscules pour qu'un même email
     * ne puisse pas exister deux fois avec une casse différente.
     */
    @Transactional
    public MeResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw emailTaken();
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));   // jamais le mot de passe en clair
        user.setName(request.name().strip());
        user.setSurname(request.surname() == null || request.surname().isBlank() ? null : request.surname().strip());

        try {
            // saveAndFlush : l'INSERT part tout de suite, pour attraper ici une violation de la contrainte UNIQUE
            user = users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Deux inscriptions simultanées avec le même email : la base refuse la seconde
            throw emailTaken();
        }
        return new MeResponse(user.getId(), user.getEmail(), user.getName(), user.getSurname(), null, 0, 0, 0);
    }

    // --- Connexion ---

    @Transactional
    public Session login(LoginRequest request) {
        Optional<User> user = users.findByEmailIgnoreCase(normalize(request.email()));
        // Même message que l'email existe ou non : on ne révèle pas quels comptes existent
        if (user.isEmpty() || !passwordEncoder.matches(request.password(), user.get().getPasswordHash())) {
            throw invalidCredentials();
        }
        return openSession(user.get(), Instant.now());
    }

    /**
     * Échange un refresh token valide contre un nouveau token d'accès. Le refresh token utilisé
     * est supprimé et remplacé ("rotation") : s'il a été volé et réutilisé, il ne marche qu'une fois.
     */
    @Transactional
    public Session refresh(String refreshToken) {
        Instant now = Instant.now();
        RefreshToken stored = Optional.ofNullable(refreshToken)
                .flatMap(token -> refreshTokens.findByTokenHash(tokens.hash(token)))
                .filter(token -> token.getExpiresAt().isAfter(now))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expirée, reconnecte-toi"));

        refreshTokens.delete(stored);
        return openSession(stored.getUser(), now);
    }

    /** Déconnexion de cet appareil : son refresh token ne pourra plus servir. */
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken != null) {
            refreshTokens.deleteByTokenHash(tokens.hash(refreshToken));
        }
    }

    /** Chaque nuit à 4 h : suppression des refresh tokens expirés. */
    @Scheduled(cron = "0 0 4 * * *")
    public void purgeExpiredRefreshTokens() {
        refreshTokens.deleteExpired(Instant.now());
    }

    private Session openSession(User user, Instant now) {
        String refreshToken = tokens.createRefreshToken();
        RefreshToken stored = new RefreshToken();
        stored.setUser(user);
        stored.setTokenHash(tokens.hash(refreshToken));
        stored.setCreatedAt(now);
        stored.setExpiresAt(now.plus(props.refreshTokenTtl()));
        refreshTokens.save(stored);

        TokenResponse access = TokenResponse.bearer(tokens.createAccessToken(user, now), props.accessTokenTtl().toSeconds());
        return new Session(access, refreshToken);
    }

    private static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private static ResponseStatusException emailTaken() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Cet email est déjà utilisé");
    }

    private static ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email ou mot de passe incorrect");
    }
}
