package org.sps.backend.common;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI (/swagger-ui.html) : ajoute le bouton "Authorize" où coller le token d'accès
 * obtenu avec POST /api/auth/login. Il est ensuite envoyé avec chaque requête de test.
 */
@Configuration
@OpenAPIDefinition(info = @Info(title = "SPS API"), security = @SecurityRequirement(name = "bearer"))
@SecurityScheme(name = "bearer", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
