package org.sps.backend.video;

import java.time.Instant;

/**
 * Une vidéo dans "Mes vidéos" : le propriétaire voit aussi sa visibilité et l'avancement de
 * son traitement (en attente, en cours avec un pourcentage, prête ou en échec).
 */
public record OwnedVideo(
        String publicId,
        String title,
        double duration,
        long views,
        long likes,
        Instant createdAt,
        Instant publishedAt,       // null : brouillon
        VideoPolicy policy,
        TranscodeStatus status,
        VideoResponse.Processing processing
) {
    static OwnedVideo from(Video video) {
        return new OwnedVideo(video.getPublicId(), video.getTitle(), video.getDuration(), video.getViews(),
                video.getLikes(), video.getCreatedAt(), video.getPublishedAt(), video.getPolicy(), video.getStatus(),
                new VideoResponse.Processing(video.getProcessingProgress(), video.getProcessingStep(),
                        video.getProcessingError(), video.getProcessingUpdatedAt()));
    }
}
