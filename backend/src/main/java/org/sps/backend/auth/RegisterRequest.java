package org.sps.backend.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corps de POST /api/auth/register. Les annotations sont vérifiées par @Valid dans le
 * contrôleur : si une règle n'est pas respectée, Spring répond 400 sans appeler le service.
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password,  // 72 : BCrypt ignore les octets au-delà
        @NotBlank @Size(max = 100) String name,
        @Size(max = 100) String surname
) { }
