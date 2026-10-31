package org.sps.backend.comment;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.sps.backend.user.User;
import org.sps.backend.video.Video;

import java.time.Instant;
import java.util.UUID;

/** Un commentaire sous une vidéo. Comme Video, pas de @Data sur une entité JPA. */
@Entity
@Table(name = "video_comments")
@Getter
@Setter
@NoArgsConstructor
public class Comment {

    public static final int MAX_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "video_id", nullable = false, updatable = false)
    private Video video;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false, updatable = false)
    private User author;

    // Commentaire principal auquel celui-ci répond ; null pour un commentaire principal.
    // Un seul niveau : répondre à une réponse rattache la nouvelle réponse au même commentaire principal.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", updatable = false)
    private Comment parent;

    @Column(name = "reply_count", nullable = false)
    private long replyCount = 0;            // mis à jour en SQL (CommentRepository.addReplies)

    @Column(nullable = false, length = MAX_LENGTH)
    private String content;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "edited_at")
    private Instant editedAt;               // null tant que l'auteur ne l'a pas modifié

    // Compteurs mis à jour en SQL (CommentRepository.addReactions), comme pour les vidéos
    private long likes = 0;
    private long dislikes = 0;
}
