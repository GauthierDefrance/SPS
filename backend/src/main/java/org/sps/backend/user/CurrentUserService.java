package org.sps.backend.user;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

/**
 * Retrouve l'utilisateur connecté à partir de l'Authentication de Spring Security
 * (que Spring passe aux méthodes de contrôleur qui la déclarent en paramètre).
 * Avec un token JWT, le "nom" de l'authentification est le claim "sub" du token : l'id de
 * l'utilisateur. Aucune requête en base n'est nécessaire.
 */
@Service
public class CurrentUserService {

    /** L'identifiant de l'utilisateur connecté, ou vide pour un visiteur anonyme. */
    public Optional<UUID> findId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return Optional.of(UUID.fromString(authentication.getName()));
    }

    /** L'identifiant de l'utilisateur connecté, ou 401 si personne n'est connecté. */
    public UUID requireId(Authentication authentication) {
        return findId(authentication)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Connexion requise"));
    }
}
