package org.sps.backend.video;

import java.time.Instant;
import java.util.UUID;

/** Une vidéo dans une liste (recherche, fil des abonnements) : ce qu'il faut pour afficher sa carte. */
public record VideoCard(
        String publicId,
        String title,
        double duration,
        long views,
        long likes,
        Instant publishedAt,
        UUID ownerId,
        String ownerName
) {
    static VideoCard from(Video video) {
        return new VideoCard(video.getPublicId(), video.getTitle(), video.getDuration(), video.getViews(),
                video.getLikes(), video.getPublishedAt(), video.getOwner().getId(), video.getOwner().getName());
    }
}
