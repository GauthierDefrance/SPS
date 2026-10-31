package org.sps.backend.user;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.sps.backend.common.PageResponse;
import org.sps.backend.video.OwnedVideo;
import org.sps.backend.video.VideoCard;
import org.sps.backend.video.VideoService;
import org.sps.backend.video.VideoSummary;
import org.sps.backend.video.WatchPositionRequest;
import org.sps.backend.video.WatchedVideo;

import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;

/*
 * Deux familles de routes :
 *  - /api/users/me/...      : l'utilisateur connecté (connexion obligatoire, voir SecurityConfig)
 *  - /api/users/{userId}... : le profil public d'une chaîne, visible par tous
 * "/me" est un chemin fixe : Spring le préfère à "/{userId}", il n'y a pas de confusion.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserRestController {

    private final UserService userService;
    private final VideoService videoService;
    private final CurrentUserService currentUser;

    // --- Moi ---

    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        return userService.me(currentUser.requireId(authentication));
    }

    /** PUT /api/users/me/avatar (multipart, champ "file") : changer sa photo de profil. */
    @PutMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MeResponse uploadAvatar(@RequestParam("file") MultipartFile file, Authentication authentication) {
        return userService.uploadAvatar(currentUser.requireId(authentication), file);
    }

    /** DELETE /api/users/me/avatar : retirer sa photo de profil (le site affiche alors l'initiale). */
    @DeleteMapping("/me/avatar")
    public MeResponse deleteAvatar(Authentication authentication) {
        return userService.deleteAvatar(currentUser.requireId(authentication));
    }

    /** Toutes mes vidéos, privées et en cours d'encodage comprises. */
    @GetMapping("/me/videos")
    public PageResponse<OwnedVideo> myVideos(Authentication authentication,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return videoService.myVideos(currentUser.requireId(authentication), page, size);
    }

    /** Les vidéos que j'ai likées, de la plus récente à la plus ancienne. */
    @GetMapping("/me/likes")
    public PageResponse<VideoSummary> likedVideos(Authentication authentication,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return userService.likedVideos(currentUser.requireId(authentication), page, size);
    }

    @GetMapping("/me/history")
    public PageResponse<WatchedVideo> history(Authentication authentication,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return userService.history(currentUser.requireId(authentication), page, size);
    }

    /** PUT /api/users/me/history/X8z_K9pLm2Q {"positionSeconds": 42.5} : sauvegarde de la position de lecture. */
    @PutMapping("/me/history/{publicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void saveWatchPosition(@PathVariable String publicId,
                                  @Valid @RequestBody WatchPositionRequest request,
                                  Authentication authentication) {
        videoService.saveWatchPosition(publicId, currentUser.requireId(authentication), request.positionSeconds());
    }

    @DeleteMapping("/me/history")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearHistory(Authentication authentication) {
        userService.clearHistory(currentUser.requireId(authentication));
    }

    /** Fil des abonnements : les dernières vidéos des chaînes que je suis. */
    @GetMapping("/me/feed")
    public PageResponse<VideoCard> feed(Authentication authentication,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return videoService.subscriptionFeed(currentUser.requireId(authentication), page, size);
    }

    @GetMapping("/me/subscriptions")
    public PageResponse<UserSummary> mySubscriptions(Authentication authentication,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return userService.subscriptions(currentUser.requireId(authentication), page, size);
    }

    /** PUT /api/users/me/subscriptions/{channelId} : s'abonner (le refaire ne change rien). */
    @PutMapping("/me/subscriptions/{channelId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void subscribe(@PathVariable UUID channelId, Authentication authentication) {
        userService.subscribe(currentUser.requireId(authentication), channelId);
    }

    @DeleteMapping("/me/subscriptions/{channelId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@PathVariable UUID channelId, Authentication authentication) {
        userService.unsubscribe(currentUser.requireId(authentication), channelId);
    }

    // --- Chaînes (public) ---

    /**
     * GET /api/users/{id}/avatar.jpg?v=... : la photo de profil. L'adresse change à chaque nouvelle
     * photo (paramètre v), le navigateur peut donc la garder un an sans jamais montrer l'ancienne.
     */
    @GetMapping("/{userId}/avatar.jpg")
    public ResponseEntity<StreamingResponseBody> avatar(@PathVariable UUID userId) {
        InputStream in = userService.openAvatar(userId);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(out -> {
                    try (in) {
                        in.transferTo(out);
                    }
                });
    }

    @GetMapping("/{userId}")
    public UserProfile profile(@PathVariable UUID userId, Authentication authentication) {
        return userService.profile(userId, currentUser.findId(authentication));
    }

    /** Les vidéos publiques et prêtes d'une chaîne. */
    @GetMapping("/{userId}/videos")
    public PageResponse<VideoSummary> channelVideos(@PathVariable UUID userId,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return videoService.channelVideos(userId, page, size);
    }

    /** Les chaînes que cet utilisateur suit. */
    @GetMapping("/{userId}/subscriptions")
    public PageResponse<UserSummary> subscriptions(@PathVariable UUID userId,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return userService.subscriptions(userId, page, size);
    }
}
