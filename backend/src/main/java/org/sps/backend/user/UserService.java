package org.sps.backend.user;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.sps.backend.common.PageResponse;
import org.sps.backend.storage.ImageService;
import org.sps.backend.storage.S3Properties;
import org.sps.backend.storage.S3StorageService;
import org.sps.backend.video.TranscodeStatus;
import org.sps.backend.video.VideoPolicy;
import org.sps.backend.video.VideoReactionRepository;
import org.sps.backend.video.VideoRepository;
import org.sps.backend.video.VideoSummary;
import org.sps.backend.video.WatchHistoryRepository;
import org.sps.backend.video.WatchedVideo;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Profils, abonnements et listes personnelles (vidéos aimées, historique).
 * Ces listes ne sont pas des champs de User : elles peuvent contenir des milliers d'éléments,
 * on les lit donc page par page avec des requêtes dédiées.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private static final int AVATAR_SIZE = 256;

    private final UserRepository users;
    private final VideoRepository videos;
    private final VideoReactionRepository reactions;
    private final WatchHistoryRepository history;
    private final ImageService images;
    private final S3StorageService storage;
    private final S3Properties s3;

    // --- Profils ---

    @Transactional(readOnly = true)
    public UserProfile profile(UUID userId, Optional<UUID> viewerId) {
        User user = get(userId);
        return new UserProfile(user.getId(), user.getName(), user.getSurname(),
                UserSummary.avatarUrl(user.getId(), user.getAvatarUpdatedAt()),
                users.countSubscribers(userId),
                videos.countByOwner_IdAndPolicyAndStatusAndPublishedAtIsNotNull(userId, VideoPolicy.PUBLIC, TranscodeStatus.READY),
                viewerId.map(viewer -> users.isSubscribed(viewer, userId)).orElse(false));
    }

    @Transactional(readOnly = true)
    public MeResponse me(UUID userId) {
        User user = get(userId);
        return new MeResponse(user.getId(), user.getEmail(), user.getName(), user.getSurname(),
                UserSummary.avatarUrl(user.getId(), user.getAvatarUpdatedAt()),
                users.countSubscribers(userId),
                users.countSubscriptions(userId),
                videos.countByOwner_Id(userId));
    }

    // --- Photo de profil ---

    /** Photo de profil : redessinée en JPEG carré 256×256 (recadrée au centre), dans le bucket images. */
    @Transactional
    public MeResponse uploadAvatar(UUID userId, MultipartFile file) {
        User user = get(userId);
        byte[] jpeg = images.toJpeg(file, AVATAR_SIZE, AVATAR_SIZE);
        storage.upload(s3.bucketImages(), avatarKey(userId), new ByteArrayInputStream(jpeg), jpeg.length, "image/jpeg");
        user.setAvatarUpdatedAt(Instant.now());
        return me(userId);
    }

    @Transactional
    public MeResponse deleteAvatar(UUID userId) {
        User user = get(userId);
        if (user.getAvatarUpdatedAt() != null) {
            storage.delete(s3.bucketImages(), avatarKey(userId));
            user.setAvatarUpdatedAt(null);
        }
        return me(userId);
    }

    @Transactional(readOnly = true)
    public InputStream openAvatar(UUID userId) {
        if (get(userId).getAvatarUpdatedAt() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pas de photo de profil");
        }
        return storage.read(s3.bucketImages(), avatarKey(userId));
    }

    private static String avatarKey(UUID userId) {
        return "avatars/" + userId + ".jpg";
    }

    // --- Abonnements ---

    @Transactional(readOnly = true)
    public PageResponse<UserSummary> subscriptions(UUID userId, int page, int size) {
        get(userId); // 404 si l'utilisateur n'existe pas
        return PageResponse.of(users.findSubscriptions(userId, PageResponse.request(page, size)));
    }

    @Transactional
    public void subscribe(UUID followerId, UUID channelId) {
        if (followerId.equals(channelId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Impossible de s'abonner à sa propre chaîne");
        }
        get(channelId);
        users.subscribe(followerId, channelId);
    }

    @Transactional
    public void unsubscribe(UUID followerId, UUID channelId) {
        users.unsubscribe(followerId, channelId);
    }

    // --- Listes personnelles ---

    @Transactional(readOnly = true)
    public PageResponse<VideoSummary> likedVideos(UUID userId, int page, int size) {
        return PageResponse.of(reactions.findLikedVideos(userId, PageResponse.request(page, size)));
    }

    @Transactional(readOnly = true)
    public PageResponse<WatchedVideo> history(UUID userId, int page, int size) {
        return PageResponse.of(history.findHistory(userId, PageResponse.request(page, size)));
    }

    @Transactional
    public void clearHistory(UUID userId) {
        history.clearHistory(userId);
    }

    private User get(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable"));
    }
}
