package org.sps.backend.user;

import java.util.UUID;

/** Profil public d'une chaîne, visible par tous. */
public record UserProfile(
        UUID id,
        String name,
        String surname,
        String avatarUrl,   // null : pas de photo de profil
        long subscribers,   // nombre d'abonnés
        long videos,        // nombre de vidéos publiques et prêtes
        boolean subscribed  // l'utilisateur connecté est-il abonné à cette chaîne ? (false si anonyme)
) { }
