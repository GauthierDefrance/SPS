package org.sps.backend.video;

import java.time.Instant;

/** Une vidéo de l'historique, avec où on s'est arrêté et quand on l'a regardée. */
public record WatchedVideo(
        String publicId,
        String title,
        double duration,
        double positionSeconds,
        Instant watchedAt
) { }
