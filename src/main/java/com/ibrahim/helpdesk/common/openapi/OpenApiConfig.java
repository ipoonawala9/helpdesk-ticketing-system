package com.ibrahim.helpdesk.common.openapi;

import com.ibrahim.helpdesk.exception.ApiErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The OpenAPI document served at /v3/api-docs and rendered by Swagger UI at
 * /swagger-ui.html.
 */
@Configuration
public class OpenApiConfig {

    static final String BEARER_SCHEME = "bearerAuth";

    /** Operations that need no token. */
    private static final Set<String> PUBLIC_OPERATIONS = Set.of("POST /api/auth/login");

    /** Workflow and creation operations whose rules can reject a valid request as conflicting. */
    private static final List<String> CONFLICT_SUFFIXES =
            List.of("/assign", "/start", "/resolve", "/reopen", "/close", "/messages");

    @Bean
    public OpenAPI helpDeskOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("HelpDesk Ticketing API")
                        .version("1.0")
                        .description("""
                                Multi-organization support ticketing.

                                **Authentication:** call `POST /api/auth/login` with an email and \
                                password, then press **Authorize** and paste the returned `accessToken`. \
                                Every other operation requires it.

                                **Scope:** each user only ever sees data within their scope. A ticket, \
                                user or organization outside it is answered with `404`, exactly as for \
                                an id that does not exist.

                                **Errors** always use the `ApiErrorResponse` shape."""))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Access token from POST /api/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }

    /**
     * Declares the ApiErrorResponse schema and adds the error responses every
     * operation can return, so clients see the real contract rather than only
     * the success case. Public operations are marked as needing no token.
     */
    @Bean
    public OpenApiCustomizer standardErrorResponses() {
        return openApi -> {
            Map<String, Schema> errorSchemas = ModelConverters.getInstance().read(ApiErrorResponse.class);
            errorSchemas.forEach((name, schema) -> openApi.getComponents().addSchemas(name, schema));

            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
                String key = method.name() + " " + path;
                boolean isPublic = PUBLIC_OPERATIONS.contains(key);

                if (isPublic) {
                    operation.setSecurity(List.of());
                    addError(operation, "400", "Invalid request body");
                    addError(operation, "401", "Invalid email or password");
                    return;
                }

                addError(operation, "401", "Missing, invalid or expired access token");
                addError(operation, "403", "The caller's role may not use this operation, or this action is not allowed");
                if (path.contains("{")) {
                    addError(operation, "404", "Not found, or outside the caller's scope");
                }
                if (operation.getRequestBody() != null
                        || (operation.getParameters() != null && operation.getParameters().stream()
                                .anyMatch(parameter -> "query".equals(parameter.getIn())))) {
                    addError(operation, "400", "Validation failed or an invalid parameter");
                }
                if (method == PathItem.HttpMethod.POST
                        && (CONFLICT_SUFFIXES.stream().anyMatch(path::endsWith) || path.equals("/api/users"))) {
                    addError(operation, "409", "Conflicts with the ticket's current state, or with existing data");
                }
            }));
        };
    }

    private static void addError(Operation operation, String status, String description) {
        if (operation.getResponses().containsKey(status)) {
            return;
        }
        operation.getResponses().addApiResponse(status, new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("application/json",
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiErrorResponse")))));
    }
}
