package org.sps.worker.transcode;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param ffmpeg         chemin (ou nom dans le PATH) de ffmpeg
 * @param ffprobe        chemin (ou nom dans le PATH) de ffprobe
 * @param segmentSeconds durée cible d'un segment HLS
 * @param preset         vitesse d'encodage x264 : plus lent = fichiers plus petits à qualité égale
 */
@ConfigurationProperties(prefix = "sps.transcode")
public record TranscodeProperties(String ffmpeg, String ffprobe, int segmentSeconds, String preset) { }
