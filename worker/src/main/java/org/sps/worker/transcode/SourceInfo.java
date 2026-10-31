package org.sps.worker.transcode;

/**
 * Dimensions affichées de la vidéo source (rotation des vidéos de téléphone déjà appliquée)
 * et sa durée.
 */
record SourceInfo(int width, int height, double durationSeconds) {

    boolean landscape() {
        return width >= height;
    }

    int shortSide() {
        return Math.min(width, height);
    }

    /** Filtre FFmpeg : le palier fixe le petit côté, l'autre suit le format d'image (-2 = arrondi pair). */
    String scaleFilter(Rendition rendition) {
        return landscape() ? "scale=-2:" + rendition.height() : "scale=" + rendition.height() + ":-2";
    }

    /** Résolution "LxH" d'un palier, calculée comme le fait le filtre scale de FFmpeg. */
    String resolutionFor(Rendition rendition) {
        int longSide = (int) Math.round((double) rendition.height() * Math.max(width, height) / shortSide() / 2) * 2;
        return landscape() ? longSide + "x" + rendition.height() : rendition.height() + "x" + longSide;
    }
}
