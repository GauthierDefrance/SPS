package org.sps.backend.video;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Champs texte du formulaire d'upload (multipart/form-data), à côté du fichier "file".
 * Spring remplit ce record à partir des champs du même nom.
 *
 * @param policy PUBLIC (par défaut), UNLISTED ou PRIVATE
 */
public record VideoUploadForm(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 5000) String description,
        VideoPolicy policy
) { }
