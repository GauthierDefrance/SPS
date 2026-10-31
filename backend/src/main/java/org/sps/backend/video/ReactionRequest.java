package org.sps.backend.video;

import jakarta.validation.constraints.NotNull;

/** Corps de PUT /api/videos/{id}/reaction : {"type": "LIKE"} ou {"type": "DISLIKE"}. */
public record ReactionRequest(@NotNull ReactionType type) { }
