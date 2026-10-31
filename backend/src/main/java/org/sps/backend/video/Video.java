package org.sps.backend.video;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.sps.backend.user.User;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/*
 * Deux identifiants :
 *  - id (UUID)       : clé primaire interne, utilisée par les relations entre tables. Jamais exposée.
 *  - publicId (11 c) : identifiant court des URLs et de l'API, comme sur YouTube ("X8z_K9pLm2Q").
 *                      Il sert aussi de dossier dans le stockage : {publicId}/original, {publicId}/720p/...
 *
 * Les URLs (vidéo, HLS, miniature) ne sont pas stockées : elles se calculent à partir de publicId
 * dans les réponses de l'API, et restent justes si leur format change.
 *
 * Pas de @Data sur une entité JPA (voir User) : seulement les getters et setters.
 */
@Entity
@Table(name = "videos")
@Getter
@Setter
@NoArgsConstructor
public class Video {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;                        // Identifiant interne, jamais exposé

    @Column(name = "public_id", nullable = false, unique = true, length = VideoIdGenerator.LENGTH, updatable = false)
    private String publicId;                // Identifiant public, ex. "X8z_K9pLm2Q"

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, updatable = false)
    private User owner;                     // Le client qui a posté la vidéo

    @Column(nullable = false)
    private String title;                   // Le titre de la vidéo

    @Column(length = 5000)
    private String description;             // La description de la vidéo

    private double duration;                // La durée de la vidéo en secondes

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;              // La date de création (instant UTC, sans ambiguïté de fuseau)

    @Column(name = "published_at")
    private Instant publishedAt;            // La date de publication ; null : brouillon, visible par son propriétaire seul

    // --- Compteurs ---
    private long likes = 0;                 // Le nombre de likes de la vidéo
    private long dislikes = 0;              // Le nombre de dislikes de la vidéo
    private long views = 0;                 // Le nombre de vues de la vidéo

    // --- Fichiers ---
    @Column(name = "original_file_size")
    private Long originalFileSize;          // La taille du fichier original en octets

    @Column(name = "raw_file_key", length = 512)
    private String rawFileKey;              // La clé du fichier original dans le stockage

    // --- Statut Vidéo ---
    @Enumerated(EnumType.STRING)            // L'état de transcodage de la vidéo
    @Column(nullable = false, length = 20)
    private TranscodeStatus status = TranscodeStatus.PENDING;

    @Enumerated(EnumType.STRING)            // La politique de visibilité de la vidéo
    @Column(nullable = false, length = 20)
    private VideoPolicy policy = VideoPolicy.PUBLIC;

    // --- Suivi du traitement (mis à jour par les messages des workers) ---
    @Column(name = "processing_progress", nullable = false)
    private int processingProgress = 0;     // 0 à 100 %

    @Column(name = "processing_step", length = 50)
    private String processingStep;          // Étape en cours, ex. "ENCODAGE 720p"

    @Column(name = "processing_error", length = 500)
    private String processingError;         // Raison d'un échec

    @Column(name = "processed_by", length = 100)
    private String processedBy;             // Le worker qui a traité (ou traite) la vidéo

    @Column(name = "processing_updated_at")
    private Instant processingUpdatedAt;    // Dernière nouvelle reçue d'un worker

    // --- Miniature (voir ThumbnailChoice) ---
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ThumbnailChoice thumbnail = ThumbnailChoice.AUTO;   // la miniature affichée

    @Column(name = "thumbnail_candidates", nullable = false)
    private int thumbnailCandidates = 0;    // nombre d'images AUTO_n produites par le worker

    @Column(name = "custom_thumbnail_at")
    private Instant customThumbnailAt;      // envoi de la miniature personnalisée (null : aucune)

    @Column(name = "thumbnail_updated_at")
    private Instant thumbnailUpdatedAt;     // dernier changement de miniature (sert au cache du navigateur)

    public boolean isPublished() {
        return publishedAt != null;
    }

    /** Les miniatures que le propriétaire peut choisir, dans l'ordre d'affichage. */
    public List<ThumbnailChoice> thumbnailOptions() {
        List<ThumbnailChoice> options = new ArrayList<>();
        if (status == TranscodeStatus.READY) {
            options.add(ThumbnailChoice.AUTO);
            for (ThumbnailChoice choice : ThumbnailChoice.values()) {
                if (choice.candidateNumber() > 0 && choice.candidateNumber() <= thumbnailCandidates) {
                    options.add(choice);
                }
            }
        }
        if (customThumbnailAt != null) {
            options.add(ThumbnailChoice.CUSTOM);
        }
        return options;
    }

    /** Hauteurs des qualités HLS disponibles (144, 360, 720...). Chargées seulement quand on y accède. */
    @ElementCollection
    @CollectionTable(name = "video_renditions", joinColumns = @JoinColumn(name = "video_id"))
    @Column(name = "height")
    private Set<Integer> renditions = new HashSet<>();

    /**
     * Appelé par JPA juste avant le premier INSERT. Le service fixe normalement publicId lui-même
     * (VideoService.newPublicId vérifie qu'il est libre) ; sinon on en tire un ici.
     * En cas de doublon, la contrainte UNIQUE de la base refuse l'insertion.
     */
    @PrePersist
    void assignPublicId() {
        if (publicId == null) {
            publicId = VideoIdGenerator.generateId();
        }
    }
}
