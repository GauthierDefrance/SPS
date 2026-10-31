package org.sps.backend.video;

import jakarta.validation.constraints.PositiveOrZero;

/** Corps de PUT /api/users/me/history/{id} : {"positionSeconds": 42.5}. */
public record WatchPositionRequest(@PositiveOrZero double positionSeconds) { }
