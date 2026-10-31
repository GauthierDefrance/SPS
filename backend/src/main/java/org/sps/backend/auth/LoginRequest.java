package org.sps.backend.auth;

import jakarta.validation.constraints.NotBlank;

/** Corps de POST /api/auth/login : {"email": "...", "password": "..."}. */
public record LoginRequest(@NotBlank String email, @NotBlank String password) { }
