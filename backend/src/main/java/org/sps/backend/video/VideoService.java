package org.sps.backend.video;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.sps.backend.common.PageResponse;
import org.sps.backend.storage.ImageService;
import org.sps.backend.storage.S3Properties;
import org.sps.backend.storage.S3StorageService;
import org.sps.backend.user.UserRepository;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/*
 * La logique métier des vidéos : qui peut voir quoi, compteurs de vues et de réactions.
 * Les méthodes reçoivent l'id de l'utilisateur (Optional pour un visiteur anonyme),
 * jamais l'objet HTTP : le service ne sait rien de la sécurité ni des requêtes web.
 *
 * @Transactional : toute la méthode s'exécute dans une seule transaction (tout réussit ou tout
 * est annulé). readOnly = true pour les lectures : Hibernate saute certaines vérifications.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoService {

    private static final Sort NEWEST_FIRST = Sort.by("createdAt").descending();
    private static final Sort NEWEST_PUBLISHED_FIRST = Sort.by("publishedAt").descending();

    private final VideoRepository videos;
    private final VideoReactionRepository reactions;
    private final WatchHistoryRepository history;
    private final UserRepository users;
    private final S3StorageService storage;
    private final S3Properties s3;
    private final ImageService images;

    /**
     * Tire un identifiant public libre. Les collisions sont rarissimes (66 bits de hasard),
     * mais on vérifie quand même ; la contrainte UNIQUE en base reste le dernier filet de sécurité.
     */
    String newPublicId() {
        String publicId;
        do {
            publicId = VideoIdGenerator.generateId();
        } while (videos.existsByPublicId(publicId));
        return publicId;
    }

    // --- Lecture ---

    @Transactional(readOnly = true)
    public VideoResponse get(String publicId, Optional<UUID> viewerId) {
        Video video = findVisible(publicId, viewerId);
        ReactionType myReaction = viewerId
                .flatMap(userId -> reactions.findByUser_IdAndVideo_Id(userId, video.getId()))
                .map(VideoReaction::getType)
                .orElse(null);
        return VideoResponse.from(video, myReaction);
    }

    /**
     * Recherche dans le catalogue (vidéos publiques et prêtes) : texte dans le titre et filtres
     * optionnels de durée et de date, triée selon order.
     */
    @Transactional(readOnly = true)
    public PageResponse<VideoCard> search(String text, VideoSearch.DurationFilter duration,
                                          VideoSearch.UploadedFilter uploaded, VideoSearch.SortOrder order,
                                          int page, int size) {
        var request = PageResponse.request(page, size, (order == null ? VideoSearch.SortOrder.RECENT : order).sort);
        return PageResponse.of(videos.findAll(VideoSearch.catalog(text, duration, uploaded), request).map(VideoCard::from));
    }

    /** Fil des abonnements : les dernières vidéos des chaînes que l'utilisateur suit. */
    @Transactional(readOnly = true)
    public PageResponse<VideoCard> subscriptionFeed(UUID userId, int page, int size) {
        return PageResponse.of(videos.findAll(VideoSearch.fromChannelsFollowedBy(userId),
                PageResponse.request(page, size, NEWEST_PUBLISHED_FIRST)).map(VideoCard::from));
    }

    /** Les vidéos d'une chaîne, vues par un visiteur : publiées, publiques et prêtes seulement. */
    @Transactional(readOnly = true)
    public PageResponse<VideoSummary> channelVideos(UUID ownerId, int page, int size) {
        if (!users.existsById(ownerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Chaîne introuvable");
        }
        return PageResponse.of(videos.findByOwner_IdAndPolicyAndStatusAndPublishedAtIsNotNull(
                ownerId, VideoPolicy.PUBLIC, TranscodeStatus.READY, PageResponse.request(page, size, NEWEST_PUBLISHED_FIRST)));
    }

    /** "Mes vidéos" : toutes celles du propriétaire, quel que soit leur statut ou leur visibilité. */
    @Transactional(readOnly = true)
    public PageResponse<OwnedVideo> myVideos(UUID ownerId, int page, int size) {
        return PageResponse.of(videos.findAllByOwner_Id(ownerId, PageResponse.request(page, size, NEWEST_FIRST))
                .map(OwnedVideo::from));
    }

    // --- Modification et suppression (propriétaire uniquement) ---

    /** Change le titre, la description ou la visibilité ; les champs absents ne bougent pas. */
    @Transactional
    public VideoResponse update(String publicId, UUID userId, VideoUpdateRequest request) {
        Video video = findOwned(publicId, userId);
        if (request.title() != null) {
            video.setTitle(request.title().strip());
        }
        if (request.description() != null) {
            video.setDescription(request.description().isBlank() ? null : request.description().strip());
        }
        if (request.policy() != null) {
            video.setPolicy(request.policy());
        }
        // Pas de save() : la vidéo est une entité "gérée", Hibernate écrit les changements à la fin de la transaction
        return get(publicId, Optional.of(userId));
    }

    /**
     * Publie un brouillon : la vidéo devient visible selon sa visibilité (catalogue si PUBLIC...).
     * Seulement une fois traitée, pour que le propriétaire ait pu choisir sa miniature parmi les
     * images extraites. Publier une vidéo déjà publiée ne change rien (ni sa date de publication).
     */
    @Transactional
    public VideoResponse publish(String publicId, UUID userId) {
        Video video = findOwned(publicId, userId);
        if (!video.isPublished()) {
            if (video.getStatus() != TranscodeStatus.READY) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "La vidéo pourra être publiée à la fin de son traitement");
            }
            video.setPublishedAt(Instant.now());
        }
        return get(publicId, Optional.of(userId));
    }

    /**
     * Supprime la vidéo : d'abord la ligne en base (les réactions, commentaires et l'historique
     * partent avec, grâce aux ON DELETE CASCADE), puis ses fichiers dans le stockage.
     * Pas de @Transactional sur toute la méthode : la base doit être à jour avant de toucher aux
     * fichiers, sinon un échec du COMMIT laisserait une vidéo en base sans ses fichiers.
     * Si un worker traite encore la vidéo, ses fichiers sont nettoyés à son message READY
     * (voir VideoProcessingListener).
     */
    public void delete(String publicId, UUID userId) {
        Video video = findOwned(publicId, userId);
        videos.deleteById(video.getId());
        try {
            if (video.getRawFileKey() != null) {
                storage.delete(s3.bucketRaw(), video.getRawFileKey());
            }
            storage.deletePrefix(s3.bucketHls(), publicId + "/");
        } catch (S3StorageService.StorageException e) {
            // La vidéo n'existe plus pour personne ; il ne reste que des fichiers orphelins à nettoyer
            log.warn("Vidéo {} supprimée, mais pas tous ses fichiers", publicId, e);
        }
    }

    // --- Miniature ---

    /** Où lire une miniature dans le stockage, et son "ETag" (sa version, pour le cache du navigateur). */
    public record ThumbnailRef(String key, String etag) { }

    /**
     * La miniature d'une vidéo visible : celle choisie par le propriétaire (which vide), ou une
     * option précise (aperçu des choix possibles). 404 si cette option n'existe pas pour la vidéo.
     *
     * L'ETag change dès que l'image peut changer (nouveau choix, nouvel envoi, nouveau traitement) :
     * le navigateur redemande l'image à chaque fois avec If-None-Match, et reçoit un simple
     * "304 Not Modified" (sans l'image) tant qu'elle n'a pas changé.
     */
    @Transactional(readOnly = true)
    public ThumbnailRef thumbnail(String publicId, Optional<ThumbnailChoice> which, Optional<UUID> viewerId) {
        Video video = findVisible(publicId, viewerId);
        ThumbnailChoice choice = which.orElse(video.getThumbnail());
        if (!video.thumbnailOptions().contains(choice)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Miniature introuvable");
        }
        Instant fileDate = choice == ThumbnailChoice.CUSTOM ? video.getCustomThumbnailAt() : video.getProcessingUpdatedAt();
        String etag = "\"" + choice + "-" + epochMillis(video.getThumbnailUpdatedAt()) + "-" + epochMillis(fileDate) + "\"";
        return new ThumbnailRef(publicId + "/" + choice.path, etag);
    }

    public InputStream openThumbnail(ThumbnailRef ref) {
        return storage.read(s3.bucketHls(), ref.key());
    }

    /** Le propriétaire choisit la miniature affichée parmi les options disponibles. */
    @Transactional
    public VideoResponse chooseThumbnail(String publicId, UUID userId, ThumbnailChoice choice) {
        Video video = findOwned(publicId, userId);
        if (!video.thumbnailOptions().contains(choice)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cette miniature n'est pas disponible pour cette vidéo");
        }
        video.setThumbnail(choice);
        video.setThumbnailUpdatedAt(Instant.now());
        return get(publicId, Optional.of(userId));
    }

    /**
     * Le propriétaire envoie sa propre miniature : redessinée en JPEG 1280×720 (16:9, recadrée au
     * centre), rangée à côté des autres fichiers de la vidéo, puis choisie comme miniature.
     */
    @Transactional
    public VideoResponse uploadThumbnail(String publicId, UUID userId, MultipartFile file) {
        Video video = findOwned(publicId, userId);
        byte[] jpeg = images.toJpeg(file, 1280, 720);
        storage.upload(s3.bucketHls(), publicId + "/" + ThumbnailChoice.CUSTOM.path,
                new ByteArrayInputStream(jpeg), jpeg.length, "image/jpeg");
        Instant now = Instant.now();
        video.setCustomThumbnailAt(now);
        video.setThumbnail(ThumbnailChoice.CUSTOM);
        video.setThumbnailUpdatedAt(now);
        return get(publicId, Optional.of(userId));
    }

    private static long epochMillis(Instant instant) {
        return instant == null ? 0 : instant.toEpochMilli();
    }

    // --- Vues et historique ---

    /** Compte une vue ; pour un utilisateur connecté, ajoute aussi la vidéo à son historique. */
    @Transactional
    public void recordView(String publicId, Optional<UUID> viewerId) {
        Video video = findVisible(publicId, viewerId);
        videos.incrementViews(video.getId());
        viewerId.ifPresent(userId -> history.touch(userId, video.getId()));
    }

    /** Enregistre où l'utilisateur s'est arrêté, pour reprendre la lecture plus tard. */
    @Transactional
    public void saveWatchPosition(String publicId, UUID userId, double positionSeconds) {
        Video video = findVisible(publicId, Optional.of(userId));
        history.savePosition(userId, video.getId(), positionSeconds);
    }

    // --- Likes / dislikes ---

    /**
     * Met (ou change) la réaction de l'utilisateur. Les compteurs de la vidéo sont mis à jour
     * en SQL (videos.addReactions) pour rester justes même avec des clics simultanés.
     */
    @Transactional
    public ReactionResponse react(String publicId, UUID userId, ReactionType type) {
        Video video = findVisible(publicId, Optional.of(userId));
        Optional<VideoReaction> existing = reactions.findByUser_IdAndVideo_Id(userId, video.getId());

        long likesDelta = 0;
        long dislikesDelta = 0;
        if (existing.isEmpty()) {
            VideoReaction reaction = new VideoReaction();
            // getReferenceById : un "pointeur" vers l'utilisateur, sans requête pour le charger
            reaction.setUser(users.getReferenceById(userId));
            reaction.setVideo(video);
            reaction.setType(type);
            reactions.save(reaction);
            if (type == ReactionType.LIKE) likesDelta = 1; else dislikesDelta = 1;
        } else if (existing.get().getType() != type) {
            existing.get().setType(type);   // entité gérée : Hibernate enregistre le changement tout seul
            likesDelta = type == ReactionType.LIKE ? 1 : -1;
            dislikesDelta = -likesDelta;
        }

        if (likesDelta != 0 || dislikesDelta != 0) {
            videos.addReactions(video.getId(), likesDelta, dislikesDelta);
        }
        return new ReactionResponse(video.getLikes() + likesDelta, video.getDislikes() + dislikesDelta, type);
    }

    @Transactional
    public ReactionResponse removeReaction(String publicId, UUID userId) {
        Video video = findVisible(publicId, Optional.of(userId));
        Optional<VideoReaction> existing = reactions.findByUser_IdAndVideo_Id(userId, video.getId());
        if (existing.isEmpty()) {
            return new ReactionResponse(video.getLikes(), video.getDislikes(), null);
        }

        reactions.delete(existing.get());
        long likesDelta = existing.get().getType() == ReactionType.LIKE ? -1 : 0;
        long dislikesDelta = existing.get().getType() == ReactionType.DISLIKE ? -1 : 0;
        videos.addReactions(video.getId(), likesDelta, dislikesDelta);
        return new ReactionResponse(video.getLikes() + likesDelta, video.getDislikes() + dislikesDelta, null);
    }

    // --- Lecture HLS ---

    /**
     * Ouvre un fichier HLS (playlist ou segment) d'une vidéo prête, après avoir vérifié que le
     * spectateur a le droit de la voir. Les fichiers sont lus dans le bucket hls, sous {publicId}/.
     */
    @Transactional(readOnly = true)
    public InputStream openHlsFile(String publicId, String path, Optional<UUID> viewerId) {
        Video video = findVisible(publicId, viewerId);
        if (video.getStatus() != TranscodeStatus.READY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vidéo en cours de traitement");
        }
        return storage.read(s3.bucketHls(), publicId + "/" + path);
    }

    // --- Règle de visibilité ---

    /**
     * La vidéo si l'utilisateur a le droit de la voir, sinon 404. Une vidéo privée, ou pas encore
     * publiée (brouillon), n'est visible que par son propriétaire ; pour les autres, on répond "introuvable" plutôt que "interdit",
     * pour ne pas révéler qu'elle existe. UNLISTED : visible par quiconque a le lien.
     */
    public Video findVisible(String publicId, Optional<UUID> viewerId) {
        Video video = videos.findWithOwnerByPublicId(publicId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vidéo introuvable"));
        checkVisible(video, viewerId);
        return video;
    }

    /** Même règle, pour une vidéo déjà chargée (avec son propriétaire), ex. celle d'un commentaire. */
    public void checkVisible(Video video, Optional<UUID> viewerId) {
        boolean isOwner = viewerId.map(id -> id.equals(video.getOwner().getId())).orElse(false);
        if ((video.getPolicy() == VideoPolicy.PRIVATE || !video.isPublished()) && !isOwner) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vidéo introuvable");
        }
    }

    /** La vidéo si l'utilisateur en est le propriétaire ; 403 s'il peut la voir sans en être le propriétaire. */
    private Video findOwned(String publicId, UUID userId) {
        Video video = findVisible(publicId, Optional.of(userId));
        if (!video.getOwner().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seul le propriétaire peut modifier cette vidéo");
        }
        return video;
    }
}
