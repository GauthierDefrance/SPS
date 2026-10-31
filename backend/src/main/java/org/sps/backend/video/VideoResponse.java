package org.sps.backend.video;

import org.sps.backend.user.UserSummary;

import java.time.Instant;
import java.util.List;

/**
 * Ce que l'API renvoie pour une vidéo. On ne renvoie jamais l'entité Video elle-même :
 * elle contient l'id interne, et sa relation owner donnerait accès au passwordHash.
 *
 * @param id         l'identifiant public (publicId), le seul que les clients connaissent
 * @param publishedAt date de publication ; null tant que la vidéo est un brouillon
 * @param processing où en est le traitement (utile pour une barre de progression après l'upload)
 * @param qualities  qualités disponibles, de la plus basse à la plus haute ("144p", ..., "1080p")
 * @param hlsUrl     playlist à donner au lecteur (hls.js), présente seulement quand la vidéo est prête
 * @param myReaction la réaction de l'utilisateur connecté (LIKE, DISLIKE), null s'il n'en a pas
 */
public record VideoResponse(
        String id,
        String title,
        String description,
        double duration,
        Instant createdAt,
        Instant publishedAt,
        long views,
        long likes,
        long dislikes,
        TranscodeStatus status,
        Processing processing,
        List<String> qualities,
        String hlsUrl,
        VideoPolicy policy,
        UserSummary owner,
        ReactionType myReaction,
        Thumbnail thumbnail
) {
    /**
     * La miniature choisie et celles qu'on peut choisir. Chaque option se voit à l'adresse
     * /api/videos/{id}/thumbnails/{option en minuscules}.jpg (ex. auto_2.jpg).
     */
    public record Thumbnail(ThumbnailChoice selected, List<ThumbnailChoice> options) { }

    /** Avancement du traitement. error n'est rempli qu'en cas d'échec (status FAILED). */
    public record Processing(int progress, String step, String error, Instant updatedAt) { }

    static VideoResponse from(Video video, ReactionType myReaction) {
        boolean ready = video.getStatus() == TranscodeStatus.READY;
        return new VideoResponse(
                video.getPublicId(),
                video.getTitle(),
                video.getDescription(),
                video.getDuration(),
                video.getCreatedAt(),
                video.getPublishedAt(),
                video.getViews(),
                video.getLikes(),
                video.getDislikes(),
                video.getStatus(),
                new Processing(video.getProcessingProgress(), video.getProcessingStep(),
                        video.getProcessingError(), video.getProcessingUpdatedAt()),
                video.getRenditions().stream().sorted().map(height -> height + "p").toList(),
                ready ? "/api/videos/" + video.getPublicId() + "/hls/master.m3u8" : null,
                video.getPolicy(),
                UserSummary.from(video.getOwner()),
                myReaction,
                new Thumbnail(video.getThumbnail(), video.thumbnailOptions()));
    }
}
