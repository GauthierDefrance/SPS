package org.sps.backend.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Accès au stockage objet S3 (SeaweedFS), lu dans application.yaml (préfixe sps.s3). */
@ConfigurationProperties(prefix = "sps.s3")
public record S3Properties(
        String endpoint,
        String accessKey,
        String secretKey,
        String bucketRaw,   // fichiers originaux, tels qu'uploadés
        String bucketHls,   // résultat du transcodage : playlists, segments HLS et miniatures
        String bucketImages // images envoyées par les utilisateurs : photos de profil
) { }
