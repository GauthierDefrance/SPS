package org.sps.backend.video;

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
import org.sps.backend.user.CurrentUserService;

import org.springframework.web.context.request.WebRequest;

import java.io.InputStream;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/*
 * Le contrôleur ne fait que traduire HTTP <-> service : lire l'URL et le corps, savoir qui est
 * connecté, choisir le code de retour. Toute la logique est dans VideoService.
 *
 * Authentication : Spring la passe automatiquement si on la déclare en paramètre
 * (null pour un visiteur anonyme). CurrentUserService la transforme en id d'utilisateur.
 */
@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
public class VideoRestController {

    // Les fichiers HLS d'une vidéo ne changent jamais une fois produits : le navigateur peut les garder.
    // "private" : un cache partagé (proxy, CDN) ne doit pas les servir, une vidéo peut être privée.
    private static final CacheControl HLS_CACHE = CacheControl.maxAge(Duration.ofDays(7)).cachePrivate();
    private static final MediaType M3U8 = MediaType.parseMediaType("application/vnd.apple.mpegurl");
    private static final MediaType MPEG_TS = MediaType.parseMediaType("video/mp2t");

    private final VideoService videoService;
    private final VideoUploadService uploadService;
    private final CurrentUserService currentUser;

    /**
     * POST /api/videos (multipart/form-data) : champs "title", "description" (optionnel),
     * "policy" (PUBLIC, UNLISTED ou PRIVATE, optionnel) et le fichier "file".
     * Réponse 202 Accepted : la vidéo est reçue, son traitement se fait ensuite en arrière-plan.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public VideoResponse upload(@Valid @ModelAttribute VideoUploadForm form,
                                @RequestParam("file") MultipartFile file,
                                Authentication authentication) {
        return uploadService.upload(currentUser.requireId(authentication), form, file);
    }

    /** POST /api/videos/X8z_K9pLm2Q/retry : relance le traitement d'une vidéo en échec (propriétaire). */
    @PostMapping("/{publicId}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public VideoResponse retry(@PathVariable String publicId, Authentication authentication) {
        return uploadService.retry(publicId, currentUser.requireId(authentication));
    }

    // --- Lecture HLS ---
    /*
     * Le lecteur (hls.js) charge d'abord master.m3u8, la liste des qualités, puis la playlist de la
     * qualité choisie (720p/index.m3u8), puis ses segments de 4 secondes (720p/seg_000.ts...).
     * Il change de qualité à chaque segment selon le débit : on n'envoie jamais la vidéo entière.
     * Les chemins sont relatifs : master.m3u8 contient "720p/index.m3u8", résolu en .../hls/720p/index.m3u8.
     */

    @GetMapping("/{publicId}/hls/master.m3u8")
    public ResponseEntity<StreamingResponseBody> hlsMaster(@PathVariable String publicId, Authentication authentication) {
        return hlsFile(publicId, "master.m3u8", M3U8, authentication);
    }

    // Les expressions régulières n'acceptent que des noms attendus : impossible de demander "../autre-video"
    @GetMapping("/{publicId}/hls/{quality:\\d+p}/{file:index\\.m3u8|seg_\\d+\\.ts}")
    public ResponseEntity<StreamingResponseBody> hlsRendition(@PathVariable String publicId,
                                                              @PathVariable String quality,
                                                              @PathVariable String file,
                                                              Authentication authentication) {
        return hlsFile(publicId, quality + "/" + file, file.endsWith(".ts") ? MPEG_TS : M3U8, authentication);
    }

    private ResponseEntity<StreamingResponseBody> hlsFile(String publicId, String path, MediaType type,
                                                          Authentication authentication) {
        // Ouvert avant d'écrire la réponse : un fichier absent ou interdit donne une erreur propre
        InputStream in = videoService.openHlsFile(publicId, path, currentUser.findId(authentication));
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(HLS_CACHE)
                .body(out -> {
                    try (in) {
                        in.transferTo(out);
                    }
                });
    }

    /**
     * GET /api/videos?q=chat&duration=SHORT&uploaded=WEEK&sort=VIEWS&page=0&size=20
     * Recherche dans le catalogue. Tous les paramètres sont optionnels :
     *   duration : ANY, SHORT (< 4 min), MEDIUM (4-20 min), LONG (> 20 min)
     *   uploaded : ANY, DAY, WEEK, MONTH, YEAR
     *   sort     : RECENT (par défaut), OLDEST, VIEWS, LIKES
     */
    @GetMapping
    public PageResponse<VideoCard> search(@RequestParam(required = false) String q,
                                          @RequestParam(defaultValue = "ANY") VideoSearch.DurationFilter duration,
                                          @RequestParam(defaultValue = "ANY") VideoSearch.UploadedFilter uploaded,
                                          @RequestParam(defaultValue = "RECENT") VideoSearch.SortOrder sort,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return videoService.search(q, duration, uploaded, sort, page, size);
    }

    // --- Miniature ---

    /** GET /api/videos/X8z_K9pLm2Q/thumbnail.jpg : la miniature choisie par le propriétaire. */
    @GetMapping("/{publicId}/thumbnail.jpg")
    public ResponseEntity<StreamingResponseBody> thumbnail(@PathVariable String publicId, WebRequest request,
                                                           Authentication authentication) {
        return thumbnailFile(videoService.thumbnail(publicId, Optional.empty(), currentUser.findId(authentication)), request);
    }

    /** GET /api/videos/X8z_K9pLm2Q/thumbnails/auto_2.jpg : une des miniatures possibles (pour choisir). */
    @GetMapping("/{publicId}/thumbnails/{choice:auto|auto_[1-3]|custom}.jpg")
    public ResponseEntity<StreamingResponseBody> thumbnailOption(@PathVariable String publicId, @PathVariable String choice,
                                                                 WebRequest request, Authentication authentication) {
        ThumbnailChoice option = ThumbnailChoice.valueOf(choice.toUpperCase(Locale.ROOT));
        return thumbnailFile(videoService.thumbnail(publicId, Optional.of(option), currentUser.findId(authentication)), request);
    }

    /** PUT /api/videos/X8z_K9pLm2Q/thumbnail {"choice": "AUTO_2"} : choisir la miniature (propriétaire). */
    @PutMapping("/{publicId}/thumbnail")
    public VideoResponse chooseThumbnail(@PathVariable String publicId, @Valid @RequestBody ThumbnailRequest body,
                                         Authentication authentication) {
        return videoService.chooseThumbnail(publicId, currentUser.requireId(authentication), body.choice());
    }

    /** POST /api/videos/X8z_K9pLm2Q/thumbnail (multipart, champ "file") : envoyer sa miniature (propriétaire). */
    @PostMapping(value = "/{publicId}/thumbnail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public VideoResponse uploadThumbnail(@PathVariable String publicId, @RequestParam("file") MultipartFile file,
                                         Authentication authentication) {
        return videoService.uploadThumbnail(publicId, currentUser.requireId(authentication), file);
    }

    /*
     * Cache par ETag : "no-cache" ne veut pas dire "ne pas garder", mais "revérifier avant d'utiliser".
     * Le navigateur renvoie l'ETag qu'il a (If-None-Match) ; s'il est toujours bon, checkNotModified
     * prépare une réponse 304 vide et on ne lit même pas le stockage.
     */
    private ResponseEntity<StreamingResponseBody> thumbnailFile(VideoService.ThumbnailRef ref, WebRequest request) {
        if (request.checkNotModified(ref.etag())) {
            return null;   // Spring envoie le 304
        }
        InputStream in = videoService.openThumbnail(ref);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.noCache().cachePrivate())
                .eTag(ref.etag())
                .body(out -> {
                    try (in) {
                        in.transferTo(out);
                    }
                });
    }

    /** GET /api/videos/X8z_K9pLm2Q : le détail d'une vidéo. */
    @GetMapping("/{publicId}")
    public VideoResponse get(@PathVariable String publicId, Authentication authentication) {
        return videoService.get(publicId, currentUser.findId(authentication));
    }

    /** PATCH /api/videos/X8z_K9pLm2Q {"title": "...", "description": "...", "policy": "UNLISTED"} (propriétaire). */
    @PatchMapping("/{publicId}")
    public VideoResponse update(@PathVariable String publicId,
                                @Valid @RequestBody VideoUpdateRequest request,
                                Authentication authentication) {
        return videoService.update(publicId, currentUser.requireId(authentication), request);
    }

    /** POST /api/videos/X8z_K9pLm2Q/publish : publie un brouillon traité (propriétaire). 409 s'il n'est pas prêt. */
    @PostMapping("/{publicId}/publish")
    public VideoResponse publish(@PathVariable String publicId, Authentication authentication) {
        return videoService.publish(publicId, currentUser.requireId(authentication));
    }

    /** DELETE /api/videos/X8z_K9pLm2Q : supprime la vidéo et ses fichiers (propriétaire). */
    @DeleteMapping("/{publicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String publicId, Authentication authentication) {
        videoService.delete(publicId, currentUser.requireId(authentication));
    }

    /** POST /api/videos/X8z_K9pLm2Q/view : à appeler par le lecteur au début de la lecture. */
    @PostMapping("/{publicId}/view")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void view(@PathVariable String publicId, Authentication authentication) {
        videoService.recordView(publicId, currentUser.findId(authentication));
    }

    /** PUT /api/videos/X8z_K9pLm2Q/reaction {"type": "LIKE"} : liker ou disliker. */
    @PutMapping("/{publicId}/reaction")
    public ReactionResponse react(@PathVariable String publicId,
                                  @Valid @RequestBody ReactionRequest request,
                                  Authentication authentication) {
        return videoService.react(publicId, currentUser.requireId(authentication), request.type());
    }

    /** DELETE /api/videos/X8z_K9pLm2Q/reaction : retirer son like ou son dislike. */
    @DeleteMapping("/{publicId}/reaction")
    public ReactionResponse removeReaction(@PathVariable String publicId, Authentication authentication) {
        return videoService.removeReaction(publicId, currentUser.requireId(authentication));
    }
}
