package org.sps.worker.transcode;

import java.util.List;

/**
 * Une qualité de sortie, nommée comme sur YouTube d'après le petit côté de l'image
 * (720p = 1280x720 en paysage, 720x1280 en portrait).
 */
public record Rendition(int height, int videoKbps, int audioKbps) {

    /** Les paliers de YouTube, avec des débits H.264 proches de ceux qu'il diffuse. */
    static final List<Rendition> LADDER = List.of(
            new Rendition(144, 150, 64),
            new Rendition(240, 300, 64),
            new Rendition(360, 700, 96),
            new Rendition(480, 1200, 128),
            new Rendition(720, 2500, 128),
            new Rendition(1080, 4500, 192),
            new Rendition(1440, 9000, 192),
            new Rendition(2160, 17000, 192));

    /** Paliers inférieurs ou égaux à la source : on n'agrandit jamais l'image. */
    static List<Rendition> ladderFor(int sourceShortSide) {
        List<Rendition> ladder = LADDER.stream().filter(r -> r.height() <= sourceShortSide).toList();
        if (ladder.isEmpty()) {
            // Source plus petite que 144p : une seule qualité, à sa taille d'origine (paire pour H.264)
            return List.of(new Rendition(Math.max(2, sourceShortSide & ~1), 150, 64));
        }
        return ladder;
    }

    public String name() {
        return height + "p";
    }

    /** Débit total annoncé dans master.m3u8 (vidéo + audio, avec une marge de 10 %), en bit/s. */
    int bandwidth() {
        return (int) ((videoKbps + audioKbps) * 1000 * 1.1);
    }
}
