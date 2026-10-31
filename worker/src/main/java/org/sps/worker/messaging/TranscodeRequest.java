package org.sps.worker.messaging;

import java.time.Instant;

/**
 * Message "vidéo à traiter", reçu du backend (file sps.video.transcode).
 * CONTRAT PARTAGÉ : identique à org.sps.backend.processing.TranscodeRequest (échangé en JSON).
 *
 * @param videoId identifiant public de la vidéo, sert de dossier dans le stockage
 * @param rawKey  clé du fichier original dans le bucket "raw"
 */
public record TranscodeRequest(String videoId, String rawKey, Instant requestedAt) { }
