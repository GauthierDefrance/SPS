package org.sps.backend.comment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    // Les commentaires principaux d'une vidéo (pas les réponses), avec leur auteur chargé dans la
    // même requête (son nom est affiché)
    @EntityGraph(attributePaths = "author")
    Page<Comment> findByVideo_IdAndParentIsNull(UUID videoId, Pageable pageable);

    // Les réponses à un commentaire
    @EntityGraph(attributePaths = "author")
    Page<Comment> findByParent_Id(UUID parentId, Pageable pageable);

    // Un commentaire avec sa vidéo et le propriétaire de celle-ci : de quoi vérifier les droits
    @EntityGraph(attributePaths = {"author", "video", "video.owner"})
    Optional<Comment> findWithVideoById(UUID id);

    // Tous les commentaires d'une vidéo, réponses comprises (le "12 commentaires" affiché)
    long countByVideo_Id(UUID videoId);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Comment c SET c.replyCount = c.replyCount + :delta WHERE c.id = :id")
    int addReplies(@Param("id") UUID id, @Param("delta") long delta);

    // Même principe que VideoRepository.addReactions : un compteur modifié en SQL reste juste
    // même avec des clics simultanés
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Comment c SET c.likes = c.likes + :likes, c.dislikes = c.dislikes + :dislikes WHERE c.id = :id")
    int addReactions(@Param("id") UUID id, @Param("likes") long likes, @Param("dislikes") long dislikes);
}
