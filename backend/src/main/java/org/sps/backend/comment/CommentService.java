package org.sps.backend.comment;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.sps.backend.common.PageResponse;
import org.sps.backend.user.UserRepository;
import org.sps.backend.video.ReactionResponse;
import org.sps.backend.video.ReactionType;
import org.sps.backend.video.Video;
import org.sps.backend.video.VideoService;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/*
 * Les commentaires suivent la visibilité de leur vidéo : on ne peut lire, écrire ou liker un
 * commentaire que si l'on peut voir la vidéo (VideoService.checkVisible).
 * Droits :
 *   - modifier : l'auteur seulement ;
 *   - supprimer : l'auteur, ou le propriétaire de la vidéo (il modère sa chaîne, comme sur YouTube).
 *
 * Réponses : un seul niveau, comme sur YouTube. Répondre à une réponse ajoute la réponse sous le
 * même commentaire principal ; le fil reste ainsi lisible, sans indentation sans fin.
 */
@Service
@RequiredArgsConstructor
public class CommentService {

    /** Ordre d'affichage des commentaires principaux : les plus récents, ou les plus aimés d'abord. */
    public enum SortOrder {
        RECENT(Sort.by("createdAt").descending()),
        TOP(Sort.by("likes").descending().and(Sort.by("createdAt").descending()));

        final Sort sort;

        SortOrder(Sort sort) {
            this.sort = sort;
        }
    }

    // Les réponses se lisent dans l'ordre de la conversation
    private static final Sort OLDEST_FIRST = Sort.by("createdAt").ascending();

    private final CommentRepository comments;
    private final CommentReactionRepository reactions;
    private final UserRepository users;
    private final VideoService videoService;

    // --- Lecture ---

    /** Les commentaires principaux d'une vidéo (leurs réponses se chargent à part, avec replies). */
    @Transactional(readOnly = true)
    public CommentPage list(String publicId, Optional<UUID> viewerId, SortOrder order, int page, int size) {
        Video video = videoService.findVisible(publicId, viewerId);
        Page<Comment> found = comments.findByVideo_IdAndParentIsNull(video.getId(), PageResponse.request(page, size, order.sort));
        return CommentPage.of(toResponses(found, video, viewerId), comments.countByVideo_Id(video.getId()));
    }

    /** Les réponses à un commentaire, de la plus ancienne à la plus récente. */
    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> replies(UUID commentId, Optional<UUID> viewerId, int page, int size) {
        Comment parent = findVisible(commentId, viewerId);
        Page<Comment> found = comments.findByParent_Id(parent.getId(), PageResponse.request(page, size, OLDEST_FIRST));
        return toResponses(found, parent.getVideo(), viewerId);
    }

    // --- Écriture ---

    @Transactional
    public CommentResponse create(String publicId, UUID authorId, String content) {
        Video video = videoService.findVisible(publicId, Optional.of(authorId));
        Comment comment = save(video, null, authorId, content);
        return CommentResponse.from(comment, video.getOwner().getId(), null);
    }

    /** Répond à un commentaire ; si c'est déjà une réponse, la réponse va sous son commentaire principal. */
    @Transactional
    public CommentResponse reply(UUID commentId, UUID authorId, String content) {
        Comment target = findVisible(commentId, Optional.of(authorId));
        Comment root = target.getParent() != null ? target.getParent() : target;
        Video video = target.getVideo();
        Comment reply = save(video, root, authorId, content);
        comments.addReplies(root.getId(), 1);   // compteur en SQL, juste même avec des réponses simultanées
        return CommentResponse.from(reply, video.getOwner().getId(), null);
    }

    @Transactional
    public CommentResponse edit(UUID commentId, UUID userId, String content) {
        Comment comment = findVisible(commentId, Optional.of(userId));
        if (!comment.getAuthor().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seul l'auteur peut modifier ce commentaire");
        }
        comment.setContent(content.strip());
        comment.setEditedAt(Instant.now());
        ReactionType myReaction = reactions.findByUser_IdAndComment_Id(userId, commentId)
                .map(CommentReaction::getType).orElse(null);
        return CommentResponse.from(comment, comment.getVideo().getOwner().getId(), myReaction);
    }

    @Transactional
    public void delete(UUID commentId, UUID userId) {
        Comment comment = findVisible(commentId, Optional.of(userId));
        boolean isAuthor = comment.getAuthor().getId().equals(userId);
        boolean isVideoOwner = comment.getVideo().getOwner().getId().equals(userId);
        if (!isAuthor && !isVideoOwner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tu ne peux supprimer que tes commentaires ou ceux de tes vidéos");
        }
        comments.delete(comment);   // ses réactions et ses réponses partent avec (ON DELETE CASCADE)
        if (comment.getParent() != null) {
            comments.addReplies(comment.getParent().getId(), -1);
        }
    }

    // --- Likes / dislikes : même logique que pour les vidéos (VideoService.react) ---

    @Transactional
    public ReactionResponse react(UUID commentId, UUID userId, ReactionType type) {
        Comment comment = findVisible(commentId, Optional.of(userId));
        Optional<CommentReaction> existing = reactions.findByUser_IdAndComment_Id(userId, commentId);

        long likesDelta = 0;
        long dislikesDelta = 0;
        if (existing.isEmpty()) {
            CommentReaction reaction = new CommentReaction();
            reaction.setUser(users.getReferenceById(userId));
            reaction.setComment(comment);
            reaction.setType(type);
            reactions.save(reaction);
            if (type == ReactionType.LIKE) likesDelta = 1; else dislikesDelta = 1;
        } else if (existing.get().getType() != type) {
            existing.get().setType(type);
            likesDelta = type == ReactionType.LIKE ? 1 : -1;
            dislikesDelta = -likesDelta;
        }

        if (likesDelta != 0 || dislikesDelta != 0) {
            comments.addReactions(commentId, likesDelta, dislikesDelta);
        }
        return new ReactionResponse(comment.getLikes() + likesDelta, comment.getDislikes() + dislikesDelta, type);
    }

    @Transactional
    public ReactionResponse removeReaction(UUID commentId, UUID userId) {
        Comment comment = findVisible(commentId, Optional.of(userId));
        Optional<CommentReaction> existing = reactions.findByUser_IdAndComment_Id(userId, commentId);
        if (existing.isEmpty()) {
            return new ReactionResponse(comment.getLikes(), comment.getDislikes(), null);
        }

        reactions.delete(existing.get());
        long likesDelta = existing.get().getType() == ReactionType.LIKE ? -1 : 0;
        long dislikesDelta = existing.get().getType() == ReactionType.DISLIKE ? -1 : 0;
        comments.addReactions(commentId, likesDelta, dislikesDelta);
        return new ReactionResponse(comment.getLikes() + likesDelta, comment.getDislikes() + dislikesDelta, null);
    }

    // --- Outils ---

    private Comment save(Video video, Comment parent, UUID authorId, String content) {
        Comment comment = new Comment();
        comment.setVideo(video);
        comment.setParent(parent);
        comment.setAuthor(users.findById(authorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Compte introuvable")));
        comment.setContent(content.strip());
        return comments.saveAndFlush(comment);
    }

    /** Une page de commentaires vers l'API, avec les réactions du spectateur lues en une seule requête. */
    private PageResponse<CommentResponse> toResponses(Page<Comment> found, Video video, Optional<UUID> viewerId) {
        Map<UUID, ReactionType> mine = viewerId
                .map(userId -> reactions.findByUser_IdAndComment_IdIn(userId, found.map(Comment::getId).getContent())
                        .stream().collect(Collectors.toMap(r -> r.getComment().getId(), CommentReaction::getType)))
                .orElse(Map.of());
        UUID ownerId = video.getOwner().getId();
        return PageResponse.of(found.map(comment -> CommentResponse.from(comment, ownerId, mine.get(comment.getId()))));
    }

    /** Le commentaire, si le spectateur peut voir sa vidéo ; sinon 404. */
    private Comment findVisible(UUID commentId, Optional<UUID> viewerId) {
        Comment comment = comments.findWithVideoById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Commentaire introuvable"));
        videoService.checkVisible(comment.getVideo(), viewerId);
        return comment;
    }
}
