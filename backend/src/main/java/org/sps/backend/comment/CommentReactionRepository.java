package org.sps.backend.comment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommentReactionRepository extends JpaRepository<CommentReaction, UUID> {

    Optional<CommentReaction> findByUser_IdAndComment_Id(UUID userId, UUID commentId);

    // Les réactions de l'utilisateur sur toute une page de commentaires, en une seule requête
    // (WHERE comment_id IN (...)) plutôt qu'une requête par commentaire
    List<CommentReaction> findByUser_IdAndComment_IdIn(UUID userId, Collection<UUID> commentIds);
}
