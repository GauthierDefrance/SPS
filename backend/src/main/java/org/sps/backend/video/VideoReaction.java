package org.sps.backend.video;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.sps.backend.user.User;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "video_reactions",
        // Contrainte d'unicité : Un utilisateur ne peut avoir qu'UNE seule ligne (like ou dislike) par vidéo
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "video_id"})
)
@Getter
@Setter
@NoArgsConstructor
public class VideoReaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "video_id", nullable = false)
    private Video video;

    // STRING : on stocke "LIKE" / "DISLIKE". Par défaut JPA stockerait la position (0, 1),
    // et ajouter une valeur à l'enum décalerait toutes les données existantes.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ReactionType type;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;              // Date de la réaction, pour trier "Vidéos que j'aime"
}
