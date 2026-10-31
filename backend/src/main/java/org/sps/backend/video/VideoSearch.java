package org.sps.backend.video;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.sps.backend.user.User;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/*
 * Recherche avec filtres optionnels, construite avec des "Specifications" de Spring Data :
 * chaque filtre est un petit morceau de WHERE, qu'on n'ajoute que s'il est demandé.
 * C'est plus lisible qu'une énorme requête JPQL pleine de "(:param IS NULL OR ...)".
 */
public final class VideoSearch {

    /** Filtre de durée, comme sur YouTube. */
    public enum DurationFilter {
        ANY(0, Double.MAX_VALUE),
        SHORT(0, 4 * 60),            // moins de 4 min
        MEDIUM(4 * 60, 20 * 60),     // 4 à 20 min
        LONG(20 * 60, Double.MAX_VALUE);

        final double minSeconds;
        final double maxSeconds;

        DurationFilter(double minSeconds, double maxSeconds) {
            this.minSeconds = minSeconds;
            this.maxSeconds = maxSeconds;
        }
    }

    /** Filtre de date de mise en ligne. */
    public enum UploadedFilter {
        ANY(null), DAY(Duration.ofDays(1)), WEEK(Duration.ofDays(7)), MONTH(Duration.ofDays(30)), YEAR(Duration.ofDays(365));

        final Duration maxAge;

        UploadedFilter(Duration maxAge) {
            this.maxAge = maxAge;
        }
    }

    /** Ordre des résultats. */
    public enum SortOrder {
        RECENT(Sort.by("publishedAt").descending()),
        OLDEST(Sort.by("publishedAt").ascending()),
        VIEWS(Sort.by("views").descending().and(Sort.by("publishedAt").descending())),
        LIKES(Sort.by("likes").descending().and(Sort.by("publishedAt").descending()));

        final Sort sort;

        SortOrder(Sort sort) {
            this.sort = sort;
        }
    }

    private VideoSearch() { }

    /** Vidéos visibles dans le catalogue : publiées, publiques et prêtes à être lues. */
    static Specification<Video> publicAndReady() {
        return (video, query, cb) -> cb.and(
                cb.isNotNull(video.get("publishedAt")),
                cb.equal(video.get("policy"), VideoPolicy.PUBLIC),
                cb.equal(video.get("status"), TranscodeStatus.READY));
    }

    static Specification<Video> catalog(String text, DurationFilter duration, UploadedFilter uploaded) {
        Specification<Video> spec = publicAndReady();
        if (text != null && !text.isBlank()) {
            // % et _ ont un sens spécial dans LIKE : on les échappe pour chercher le texte tel quel
            String pattern = "%" + text.strip().toLowerCase()
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            spec = spec.and((video, query, cb) -> cb.like(cb.lower(video.get("title")), pattern, '\\'));
        }
        if (duration != null && duration != DurationFilter.ANY) {
            spec = spec.and((video, query, cb) -> cb.and(
                    cb.greaterThanOrEqualTo(video.get("duration"), duration.minSeconds),
                    cb.lessThan(video.get("duration"), duration.maxSeconds)));
        }
        if (uploaded != null && uploaded.maxAge != null) {
            Instant since = Instant.now().minus(uploaded.maxAge);
            spec = spec.and((video, query, cb) -> cb.greaterThanOrEqualTo(video.get("publishedAt"), since));
        }
        return spec;
    }

    /** Vidéos des chaînes que userId suit : owner.id IN (SELECT ... FROM user_subscriptions). */
    static Specification<Video> fromChannelsFollowedBy(UUID userId) {
        return publicAndReady().and((video, query, cb) -> {
            Subquery<UUID> followed = query.subquery(UUID.class);
            Root<User> follower = followed.from(User.class);
            Join<User, User> channel = follower.join("subscriptions");
            followed.select(channel.get("id")).where(cb.equal(follower.get("id"), userId));
            return video.get("owner").get("id").in(followed);
        });
    }
}
