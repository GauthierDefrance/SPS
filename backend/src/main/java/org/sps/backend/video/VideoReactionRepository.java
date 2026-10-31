package org.sps.backend.video;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface VideoReactionRepository extends JpaRepository<VideoReaction, UUID> {

    // La réaction d'un utilisateur à une vidéo (au plus une, grâce à la contrainte UNIQUE)
    Optional<VideoReaction> findByUser_IdAndVideo_Id(UUID userId, UUID videoId);

    /*
     * Vidéos aimées, du like le plus récent au plus ancien. On écarte les vidéos devenues
     * privées entre-temps, sauf celles de l'utilisateur lui-même.
     * countQuery : la requête qui compte le total, pour la pagination.
     */
    @Query(value = """
            select new org.sps.backend.video.VideoSummary(v.publicId, v.title, v.duration, v.views, coalesce(v.publishedAt, v.createdAt))
            from VideoReaction r join r.video v
            where r.user.id = :userId
              and r.type = org.sps.backend.video.ReactionType.LIKE
              and (v.policy <> org.sps.backend.video.VideoPolicy.PRIVATE or v.owner.id = :userId)
            order by r.createdAt desc""",
            countQuery = """
            select count(r)
            from VideoReaction r join r.video v
            where r.user.id = :userId
              and r.type = org.sps.backend.video.ReactionType.LIKE
              and (v.policy <> org.sps.backend.video.VideoPolicy.PRIVATE or v.owner.id = :userId)""")
    Page<VideoSummary> findLikedVideos(@Param("userId") UUID userId, Pageable pageable);
}
