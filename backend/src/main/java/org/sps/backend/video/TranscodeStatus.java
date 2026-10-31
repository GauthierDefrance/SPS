package org.sps.backend.video;

/** L'état de transcodage d'une vidéo. */
public enum TranscodeStatus {
    PENDING,
    PROCESSING,
    READY,
    FAILED
}
