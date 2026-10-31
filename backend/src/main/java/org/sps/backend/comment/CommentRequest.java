package org.sps.backend.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corps de POST /api/videos/{id}/comments et de PUT /api/comments/{id} : {"content": "Super vidéo !"}. */
public record CommentRequest(@NotBlank @Size(max = Comment.MAX_LENGTH) String content) { }
