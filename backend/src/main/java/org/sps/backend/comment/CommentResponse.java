package org.sps.backend.comment;

import org.sps.backend.user.UserSummary;
import org.sps.backend.video.ReactionType;

import java.time.Instant;
import java.util.UUID;

/**
 * Un commentaire tel que l'API le renvoie.
 *
 * @param parentId     le commentaire principal auquel celui-ci répond, null pour un commentaire principal
 * @param replyCount   nombre de réponses (toujours 0 pour une réponse : un seul niveau)
 * @param editedAt     date de la dernière modification, null si jamais modifié
 * @param byVideoOwner l'auteur du commentaire est l'auteur de la vidéo (petite marque à côté de son nom)
 * @param myReaction   la réaction de l'utilisateur connecté à ce commentaire, null s'il n'en a pas
 */
public record CommentResponse(
        UUID id,
        UUID parentId,
        String content,
        Instant createdAt,
        Instant editedAt,
        long likes,
        long dislikes,
        long replyCount,
        UserSummary author,
        boolean byVideoOwner,
        ReactionType myReaction
) {
    static CommentResponse from(Comment comment, UUID videoOwnerId, ReactionType myReaction) {
        // getParent().getId() : l'id d'un proxy Hibernate se lit sans requête
        return new CommentResponse(comment.getId(), comment.getParent() == null ? null : comment.getParent().getId(),
                comment.getContent(), comment.getCreatedAt(), comment.getEditedAt(), comment.getLikes(),
                comment.getDislikes(), comment.getReplyCount(), UserSummary.from(comment.getAuthor()),
                comment.getAuthor().getId().equals(videoOwnerId), myReaction);
    }
}
