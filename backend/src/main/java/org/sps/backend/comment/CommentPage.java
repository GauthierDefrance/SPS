package org.sps.backend.comment;

import org.sps.backend.common.PageResponse;

import java.util.List;

/**
 * Une page de commentaires principaux, comme PageResponse, plus le nombre total de commentaires
 * de la vidéo réponses comprises (le "12 commentaires" de l'en-tête). totalItems ne compte que
 * les commentaires principaux : c'est lui qui sert à la pagination.
 */
public record CommentPage(
        List<CommentResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages,
        long totalComments
) {
    static CommentPage of(PageResponse<CommentResponse> page, long totalComments) {
        return new CommentPage(page.items(), page.page(), page.size(), page.totalItems(), page.totalPages(), totalComments);
    }
}
