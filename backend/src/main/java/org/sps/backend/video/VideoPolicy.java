package org.sps.backend.video;

/** La politique de visibilité d'une vidéo. */
public enum VideoPolicy {
    PUBLIC,     // Visible par tous, listée dans le catalogue
    PRIVATE,    // Visible seulement par son propriétaire
    UNLISTED    // Visible par ceux qui ont le lien, absente du catalogue
}
