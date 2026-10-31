package org.sps.worker.transcode;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RenditionTest {

    @Test
    void ladderNeverUpscales() {
        assertThat(Rendition.ladderFor(720)).extracting(Rendition::height).containsExactly(144, 240, 360, 480, 720);
        assertThat(Rendition.ladderFor(1080)).extracting(Rendition::height).endsWith(720, 1080);
        assertThat(Rendition.ladderFor(2160)).hasSize(8);
    }

    @Test
    void tinySourceKeepsItsOwnSize() {
        assertThat(Rendition.ladderFor(101)).extracting(Rendition::height).containsExactly(100);
    }

    @Test
    void portraitVideosAreNamedAfterTheirShortSide() {
        SourceInfo portrait = new SourceInfo(1080, 1920, 10);   // téléphone tenu verticalement
        Rendition p720 = new Rendition(720, 2500, 128);

        assertThat(Rendition.ladderFor(portrait.shortSide())).extracting(Rendition::height).endsWith(1080);
        assertThat(portrait.scaleFilter(p720)).isEqualTo("scale=720:-2");
        assertThat(portrait.resolutionFor(p720)).isEqualTo("720x1280");
    }

    @Test
    void landscapeResolution() {
        SourceInfo landscape = new SourceInfo(1920, 1080, 10);
        assertThat(landscape.resolutionFor(new Rendition(240, 300, 64))).isEqualTo("426x240");
        assertThat(landscape.scaleFilter(new Rendition(240, 300, 64))).isEqualTo("scale=-2:240");
    }
}
