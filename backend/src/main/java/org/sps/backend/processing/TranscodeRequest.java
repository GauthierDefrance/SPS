package org.sps.backend.processing;

import java.time.Instant;

/**
 * Message "vidéo à traiter", envoyé par le backend dans la file sps.video.transcode.
 * CONTRAT PARTAGÉ : le même record existe dans le worker (org.sps.worker.messaging).
 * Les deux applications s'échangent du JSON : les noms de champs doivent rester identiques.
 *
 * @param videoId identifiant public de la vidéo, sert aussi de préfixe dans le stockage
 * @param rawKey  clé du fichier original dans le bucket "raw"
 */
public record TranscodeRequest(String videoId, String rawKey, Instant requestedAt) { }
