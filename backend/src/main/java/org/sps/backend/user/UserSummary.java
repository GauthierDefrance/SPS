package org.sps.backend.user;

import java.time.Instant;
import java.util.UUID;

/**
 * Ce qu'on montre d'un utilisateur dans une liste (abonnements, propriétaire d'une vidéo, auteur d'un commentaire).
 *
 * @param avatarUrl adresse de sa photo de profil, null s'il n'en a pas (le site affiche alors son initiale)
 */
public record UserSummary(UUID id, String name, String surname, String avatarUrl) {

    /** Utilisé par les requêtes JPQL "select new UserSummary(u.id, u.name, u.surname, u.avatarUpdatedAt)". */
    public UserSummary(UUID id, String name, String surname, Instant avatarUpdatedAt) {
        this(id, name, surname, avatarUrl(id, avatarUpdatedAt));
    }

    public static UserSummary from(User user) {
        return new UserSummary(user.getId(), user.getName(), user.getSurname(), user.getAvatarUpdatedAt());
    }

    /**
     * L'adresse contient la date d'envoi (?v=...) : une nouvelle photo a une nouvelle adresse. Le
     * navigateur peut donc garder une photo en cache très longtemps sans jamais afficher l'ancienne.
     */
    public static String avatarUrl(UUID userId, Instant avatarUpdatedAt) {
        return avatarUpdatedAt == null ? null : "/api/users/" + userId + "/avatar.jpg?v=" + avatarUpdatedAt.toEpochMilli();
    }
}
