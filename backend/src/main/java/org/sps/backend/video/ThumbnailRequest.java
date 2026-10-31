package org.sps.backend.video;

import jakarta.validation.constraints.NotNull;

/** Corps de PUT /api/videos/{id}/thumbnail : {"choice": "AUTO_2"}. */
public record ThumbnailRequest(@NotNull ThumbnailChoice choice) { }
