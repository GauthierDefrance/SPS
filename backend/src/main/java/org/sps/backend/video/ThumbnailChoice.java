package org.sps.backend.video;

/**
 * Les miniatures possibles d'une vidéo, et où chacune est rangée dans le bucket hls (sous {publicId}/).
 * Les images AUTO_n sont produites par le worker ; CUSTOM est envoyée par le propriétaire.
 */
public enum ThumbnailChoice {
    AUTO("thumbnail.jpg"),
    AUTO_1("thumbnails/auto_1.jpg"),
    AUTO_2("thumbnails/auto_2.jpg"),
    AUTO_3("thumbnails/auto_3.jpg"),
    CUSTOM("thumbnails/custom.jpg");

    /** Chemin du fichier, relatif au dossier de la vidéo. */
    public final String path;

    ThumbnailChoice(String path) {
        this.path = path;
    }

    /** Numéro d'une image AUTO_n (1 à 3), 0 pour les autres. */
    int candidateNumber() {
        return name().startsWith("AUTO_") ? Integer.parseInt(name().substring(5)) : 0;
    }
}
