package org.sps.backend.video;

import java.time.Instant;

/**
 * Projection : seulement les colonnes utiles pour une carte de vidéo (catalogue, recherche,
 * vidéos d'une chaîne). Spring Data ne lit que ces colonnes, par leur nom : chaque composant
 * du record doit porter le nom d'un champ de Video.
 */
public record VideoSummary(
        String publicId,
        String title,
        double duration,
        long views,
        Instant publishedAt
) { }
