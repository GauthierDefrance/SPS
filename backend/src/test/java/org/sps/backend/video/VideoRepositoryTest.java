package org.sps.backend.video;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.sps.backend.TestcontainersConfiguration;
import org.sps.backend.user.User;
import org.sps.backend.user.UserRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exécute les requêtes de VideoRepository sur un vrai PostgreSQL (Testcontainers).
 * @Transactional : chaque test est annulé à la fin, la base reste vide entre les tests.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class VideoRepositoryTest {

    private static final PageRequest FIRST_PAGE = PageRequest.of(0, 20, Sort.by("createdAt").descending());

    @Autowired
    private VideoRepository videos;

    @Autowired
    private UserRepository users;

    private User owner;
    private Video publicReady;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setEmail("owner@sps.dev");
        owner.setPasswordHash("hash");
        owner.setName("Ada");
        owner = users.save(owner);

        publicReady = videos.save(video("Mon Premier Vlog", VideoPolicy.PUBLIC, TranscodeStatus.READY));
        videos.save(video("Brouillon privé", VideoPolicy.PRIVATE, TranscodeStatus.READY));
        videos.save(video("En cours d'encodage", VideoPolicy.PUBLIC, TranscodeStatus.PROCESSING));
    }

    @Test
    void publicIdIsGeneratedOnSave() {
        assertThat(publicReady.getPublicId()).hasSize(VideoIdGenerator.LENGTH);
        assertThat(videos.existsByPublicId(publicReady.getPublicId())).isTrue();
    }

    @Test
    void catalogContainsOnlyPublicAndReadyVideos() {
        Page<VideoSummary> page = videos.findByPolicyAndStatus(VideoPolicy.PUBLIC, TranscodeStatus.READY, FIRST_PAGE);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().getFirst().title()).isEqualTo("Mon Premier Vlog");
    }

    @Test
    void searchIgnoresCase() {
        Page<VideoSummary> page = videos.findByPolicyAndStatusAndTitleContainingIgnoreCase(
                VideoPolicy.PUBLIC, TranscodeStatus.READY, "vLoG", FIRST_PAGE);

        assertThat(page.getContent()).extracting(VideoSummary::publicId).containsExactly(publicReady.getPublicId());
    }

    @Test
    void ownerQueries() {
        assertThat(videos.findByOwner_Id(owner.getId(), FIRST_PAGE).getTotalElements()).isEqualTo(3);
        assertThat(videos.findByOwner_IdAndPolicyAndStatusAndPublishedAtIsNotNull(owner.getId(), VideoPolicy.PUBLIC, TranscodeStatus.READY, FIRST_PAGE)
                .getTotalElements()).isEqualTo(1);
        assertThat(videos.countByOwner_Id(owner.getId())).isEqualTo(3);
        assertThat(videos.existsByPublicIdAndOwner_Id(publicReady.getPublicId(), owner.getId())).isTrue();
        assertThat(videos.existsByPublicIdAndOwner_Id(publicReady.getPublicId(), UUID.randomUUID())).isFalse();
    }

    @Test
    void findWithOwnerLoadsTheOwner() {
        Video video = videos.findWithOwnerByPublicId(publicReady.getPublicId()).orElseThrow();

        assertThat(video.getOwner().getEmail()).isEqualTo("owner@sps.dev");
    }

    @Test
    void videosToTranscode() {
        assertThat(videos.findByStatusIn(List.of(TranscodeStatus.PENDING, TranscodeStatus.PROCESSING)))
                .extracting(Video::getTitle).containsExactly("En cours d'encodage");
    }

    @Test
    void countersAreUpdatedInSql() {
        UUID id = publicReady.getId();

        assertThat(videos.incrementViews(id)).isEqualTo(1);
        videos.incrementViews(id);
        videos.addReactions(id, 1, 0);   // un like
        videos.addReactions(id, -1, 1);  // le like devient un dislike

        // Lu via la projection : une nouvelle requête SQL, donc les valeurs à jour
        VideoSummary summary = videos.findSummaryByPublicId(publicReady.getPublicId()).orElseThrow();
        assertThat(summary.views()).isEqualTo(2);
        assertThat(videos.incrementViews(UUID.randomUUID())).isZero();
    }

    private Video video(String title, VideoPolicy policy, TranscodeStatus status) {
        Video video = new Video();
        video.setOwner(owner);
        video.setTitle(title);
        video.setPolicy(policy);
        video.setStatus(status);
        video.setPublishedAt(java.time.Instant.now());
        return video;
    }
}
