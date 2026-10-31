package org.sps.worker.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Accès au stockage objet S3 (SeaweedFS), lu dans application.yaml (préfixe sps.s3). */
@ConfigurationProperties(prefix = "sps.s3")
public record S3Properties(
        String endpoint,
        String accessKey,
        String secretKey,
        String bucketRaw,   // fichiers originaux, à lire
        String bucketHls    // résultat du transcodage, à écrire
) { }
