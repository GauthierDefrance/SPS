package org.sps.worker.transcode;

/**
 * Échec prévisible du traitement (fichier illisible, sans piste vidéo...). Son message est
 * destiné à l'utilisateur : il est envoyé au backend et affiché avec la vidéo.
 */
public class VideoProcessingException extends Exception {

    public VideoProcessingException(String userMessage) {
        super(userMessage);
    }

    public VideoProcessingException(String userMessage, Throwable cause) {
        super(userMessage, cause);
    }
}
