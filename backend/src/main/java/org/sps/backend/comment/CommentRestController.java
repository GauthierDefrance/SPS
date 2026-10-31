package org.sps.backend.comment;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.sps.backend.common.PageResponse;
import org.sps.backend.user.CurrentUserService;
import org.sps.backend.video.ReactionRequest;
import org.sps.backend.video.ReactionResponse;

import java.util.UUID;

/*
 * Deux familles d'URLs :
 *   /api/videos/{videoId}/comments : les commentaires d'une vidéo (lister, en ajouter un) ;
 *   /api/comments/{commentId}      : un commentaire précis (ses réponses, modifier, supprimer, liker).
 * La lecture est publique (comme la vidéo) ; tout le reste demande d'être connecté.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CommentRestController {

    private final CommentService commentService;
    private final CurrentUserService currentUser;

    /** GET /api/videos/X8z_K9pLm2Q/comments?sort=TOP&page=0&size=20 (sort : RECENT par défaut, ou TOP). */
    @GetMapping("/videos/{publicId}/comments")
    public CommentPage list(@PathVariable String publicId,
                            @RequestParam(defaultValue = "RECENT") CommentService.SortOrder sort,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "20") int size,
                            Authentication authentication) {
        return commentService.list(publicId, currentUser.findId(authentication), sort, page, size);
    }

    /** POST /api/videos/X8z_K9pLm2Q/comments {"content": "Super vidéo !"} */
    @PostMapping("/videos/{publicId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse create(@PathVariable String publicId,
                                  @Valid @RequestBody CommentRequest request,
                                  Authentication authentication) {
        return commentService.create(publicId, currentUser.requireId(authentication), request.content());
    }

    /** GET /api/comments/{id}/replies?page=0&size=20 : les réponses, de la plus ancienne à la plus récente. */
    @GetMapping("/comments/{commentId}/replies")
    public PageResponse<CommentResponse> replies(@PathVariable UUID commentId,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size,
                                                 Authentication authentication) {
        return commentService.replies(commentId, currentUser.findId(authentication), page, size);
    }

    /** POST /api/comments/{id}/replies {"content": "Merci !"} : répondre à un commentaire. */
    @PostMapping("/comments/{commentId}/replies")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse reply(@PathVariable UUID commentId,
                                 @Valid @RequestBody CommentRequest request,
                                 Authentication authentication) {
        return commentService.reply(commentId, currentUser.requireId(authentication), request.content());
    }

    /** PUT /api/comments/{id} {"content": "..."} : modifier son commentaire. */
    @PutMapping("/comments/{commentId}")
    public CommentResponse edit(@PathVariable UUID commentId,
                                @Valid @RequestBody CommentRequest request,
                                Authentication authentication) {
        return commentService.edit(commentId, currentUser.requireId(authentication), request.content());
    }

    /** DELETE /api/comments/{id} : par son auteur ou par le propriétaire de la vidéo. */
    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID commentId, Authentication authentication) {
        commentService.delete(commentId, currentUser.requireId(authentication));
    }

    /** PUT /api/comments/{id}/reaction {"type": "LIKE"} */
    @PutMapping("/comments/{commentId}/reaction")
    public ReactionResponse react(@PathVariable UUID commentId,
                                  @Valid @RequestBody ReactionRequest request,
                                  Authentication authentication) {
        return commentService.react(commentId, currentUser.requireId(authentication), request.type());
    }

    /** DELETE /api/comments/{id}/reaction : retirer son like ou son dislike. */
    @DeleteMapping("/comments/{commentId}/reaction")
    public ReactionResponse removeReaction(@PathVariable UUID commentId, Authentication authentication) {
        return commentService.removeReaction(commentId, currentUser.requireId(authentication));
    }
}
