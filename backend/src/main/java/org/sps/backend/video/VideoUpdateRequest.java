package org.sps.backend.video;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Corps de PATCH /api/videos/{id} : seuls les champs présents (non null) sont modifiés.
 * Ex. {"policy": "PRIVATE"} change seulement la visibilité.
 *
 * @param title       nouveau titre ; s'il est fourni, il ne peut pas être vide ("\\S" : au moins un caractère visible)
 * @param description nouvelle description ; "" l'efface
 */
public record VideoUpdateRequest(
        @Size(max = 255) @Pattern(regexp = "(?s).*\\S.*", message = "ne doit pas être vide") String title,
        @Size(max = 5000) String description,
        VideoPolicy policy
) { }
