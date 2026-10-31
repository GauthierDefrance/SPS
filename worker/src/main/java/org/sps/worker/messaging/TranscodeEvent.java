package org.sps.worker.messaging;

import java.time.Instant;
import java.util.List;

/**
 * Message envoyé au backend (file sps.video.events) : progression, succès ou échec.
 * CONTRAT PARTAGÉ : identique à org.sps.backend.processing.TranscodeEvent (échangé en JSON).
 */
public record TranscodeEvent(
        String videoId,
        Type type,
        int progress,
        String step,
        List<Integer> renditions,
        Double durationSeconds,
        Integer thumbnailCandidates,   // READY : nombre de miniatures candidates (thumbnails/auto_N.jpg)
        String error,
        String workerId,
        Instant at
) {
    public enum Type { PROGRESS, READY, FAILED }
}
