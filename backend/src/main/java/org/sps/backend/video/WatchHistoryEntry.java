package org.sps.backend.video;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.sps.backend.user.User;

import java.time.Instant;
import java.util.UUID;

/**
 * Une ligne de l'historique : cet utilisateur a regardé cette vidéo, la dernière fois à watchedAt,
 * et s'est arrêté à positionSeconds. Une seule ligne par (utilisateur, vidéo), mise à jour à
 * chaque visionnage (voir WatchHistoryRepository).
 */
@Entity
@Table(
        name = "watch_history",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "video_id"})
)
@Getter
@Setter
@NoArgsConstructor
public class WatchHistoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "video_id", nullable = false)
    private Video video;

    @Column(name = "position_seconds", nullable = false)
    private double positionSeconds;         // Où la lecture s'est arrêtée

    @Column(name = "watched_at", nullable = false)
    private Instant watchedAt;              // Dernier visionnage
}
