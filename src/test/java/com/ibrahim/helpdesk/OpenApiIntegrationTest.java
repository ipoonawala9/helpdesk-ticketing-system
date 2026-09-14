package com.ibrahim.helpdesk;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The published API contract at /v3/api-docs and its Swagger UI. */
class OpenApiIntegrationTest extends ApiIntegrationTestSupport {

    private String docs;

    @BeforeEach
    void loadDocs() throws Exception {
        docs = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("Swagger UI is served without a token")
    void swaggerUi() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("declares JWT bearer authentication, required by default")
    void bearerAuthentication() {
        Map<String, Object> scheme = JsonPath.read(docs, "$.components.securitySchemes.bearerAuth");
        assertThat(scheme).containsEntry("type", "http").containsEntry("scheme", "bearer").containsEntry("bearerFormat", "JWT");
        assertThat(JsonPath.<List<Object>>read(docs, "$.security[*].bearerAuth")).hasSize(1);
        assertThat(JsonPath.<String>read(docs, "$.info.title")).isEqualTo("HelpDesk Ticketing API");
    }

    @Test
    @DisplayName("login is the only operation that needs no token")
    void loginIsPublic() {
        assertThat(JsonPath.<List<Object>>read(docs, "$.paths['/api/auth/login'].post.security")).isEmpty();
        assertThat(JsonPath.<Map<String, Object>>read(docs, "$.paths['/api/auth/login'].post.responses"))
                .containsKeys("200", "400", "401").doesNotContainKey("403");

        assertThat(JsonPath.<Map<String, Object>>read(docs, "$.paths['/api/tickets/{id}'].get")).doesNotContainKey("security");
    }

    @Test
    @DisplayName("error responses are documented with the ApiErrorResponse schema")
    void errorResponses() {
        assertThat(JsonPath.<Map<String, Object>>read(docs, "$.components.schemas.ApiErrorResponse.properties"))
                .containsKeys("timestamp", "status", "error", "message", "path", "fieldErrors");

        Map<String, Object> getTicket = JsonPath.read(docs, "$.paths['/api/tickets/{id}'].get.responses");
        assertThat(getTicket).containsKeys("200", "401", "403", "404");
        assertThat(JsonPath.<String>read(docs,
                "$.paths['/api/tickets/{id}'].get.responses['404'].content['application/json'].schema.$ref"))
                .isEqualTo("#/components/schemas/ApiErrorResponse");

        assertThat(JsonPath.<Map<String, Object>>read(docs, "$.paths['/api/tickets/{id}/assign'].post.responses"))
                .containsKeys("400", "401", "403", "404", "409");
    }

    @Test
    @DisplayName("the authenticated user's id is never exposed as a request parameter")
    void currentUserIdHidden() {
        assertThat(docs).doesNotContain("currentUserId");
    }

    @Test
    @DisplayName("list operations document their filter, search and paging parameters")
    void listParameters() {
        assertThat(JsonPath.<List<String>>read(docs, "$.paths['/api/tickets'].get.parameters[*].name"))
                .contains("status", "priority", "category", "unassigned", "q", "page", "size", "sort");
        assertThat(JsonPath.<List<String>>read(docs, "$.paths['/api/users'].get.parameters[*].name"))
                .contains("role", "organizationId", "active", "q", "page", "size", "sort");
    }

    @Test
    @DisplayName("every operation has a summary and a tag")
    void operationsAreDescribed() {
        List<Map<String, Object>> operations = JsonPath.read(docs, "$.paths.*.*");
        assertThat(operations).isNotEmpty().allSatisfy(operation -> {
            assertThat(operation).containsKey("summary");
            assertThat(operation).containsKey("tags");
        });
    }
}
