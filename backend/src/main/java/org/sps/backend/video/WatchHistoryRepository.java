package org.sps.backend.video;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public interface WatchHistoryRepository extends JpaRepository<WatchHistoryEntry, UUID> {

    // Historique, du visionnage le plus récent au plus ancien (vidéos devenues privées exclues)
    @Query(value = """
            select new org.sps.backend.video.WatchedVideo(v.publicId, v.title, v.duration, h.positionSeconds, h.watchedAt)
            from WatchHistoryEntry h join h.video v
            where h.user.id = :userId
              and (v.policy <> org.sps.backend.video.VideoPolicy.PRIVATE or v.owner.id = :userId)
            order by h.watchedAt desc""",
            countQuery = """
            select count(h)
            from WatchHistoryEntry h join h.video v
            where h.user.id = :userId
              and (v.policy <> org.sps.backend.video.VideoPolicy.PRIVATE or v.owner.id = :userId)""")
    Page<WatchedVideo> findHistory(@Param("userId") UUID userId, Pageable pageable);

    /*
     * "Upsert" PostgreSQL : crée la ligne si elle n'existe pas, sinon la met à jour
     * (ON CONFLICT sur la contrainte UNIQUE (user_id, video_id)). Une seule requête, et
     * pas de doublon même si deux onglets envoient la même vidéo en même temps.
     */

    // Nouveau visionnage : remonte la vidéo en haut de l'historique, sans toucher à la position
    @Transactional
    @Modifying
    @Query(value = """
            insert into watch_history (id, user_id, video_id, position_seconds, watched_at)
            values (gen_random_uuid(), :userId, :videoId, 0, now())
            on conflict (user_id, video_id) do update set watched_at = now()""",
            nativeQuery = true)
    void touch(@Param("userId") UUID userId, @Param("videoId") UUID videoId);

    // Sauvegarde de la position de lecture (appelée régulièrement par le lecteur)
    @Transactional
    @Modifying
    @Query(value = """
            insert into watch_history (id, user_id, video_id, position_seconds, watched_at)
            values (gen_random_uuid(), :userId, :videoId, :position, now())
            on conflict (user_id, video_id) do update set position_seconds = :position, watched_at = now()""",
            nativeQuery = true)
    void savePosition(@Param("userId") UUID userId, @Param("videoId") UUID videoId, @Param("position") double position);

    @Transactional
    @Modifying
    @Query("delete from WatchHistoryEntry h where h.user.id = :userId")
    int clearHistory(@Param("userId") UUID userId);
}
