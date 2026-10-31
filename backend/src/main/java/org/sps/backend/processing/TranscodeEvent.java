package org.sps.backend.processing;

import java.time.Instant;
import java.util.List;

/**
 * Message envoyé par un worker dans la file sps.video.events : progression, succès ou échec.
 * CONTRAT PARTAGÉ : le même record existe dans le worker (org.sps.worker.messaging).
 *
 * @param progress        0 à 100
 * @param step            étape lisible ("TÉLÉCHARGEMENT", "ENCODAGE 720p"...)
 * @param renditions      READY uniquement : hauteurs des qualités produites
 * @param durationSeconds READY uniquement : durée de la vidéo
 * @param error           FAILED uniquement : raison lisible de l'échec
 * @param workerId        nom de la machine / du conteneur qui traite la vidéo
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
