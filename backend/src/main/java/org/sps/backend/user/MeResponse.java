package org.sps.backend.user;

import java.util.UUID;

/** Le profil de l'utilisateur connecté, avec ses informations privées (email). */
public record MeResponse(
        UUID id,
        String email,
        String name,
        String surname,
        String avatarUrl,   // null : pas de photo de profil
        long subscribers,   // nombre d'abonnés
        long subscriptions, // nombre de chaînes suivies
        long videos         // toutes ses vidéos, privées et en cours d'encodage comprises
) { }
