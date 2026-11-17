package com.matheus.orderFlow.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserExportIT extends AbstractIntegrationTest {
    private static final String EXPORT = "/internal/users/export";

    @Autowired
    private MockMvc mockMvc;

    private String register(String email) throws Exception {
        String response = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Ana", "email": "%s", "password": "password123" }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(response, "$.id");
    }

    private String export(String query) throws Exception {
        return mockMvc.perform(get(EXPORT + query))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void shouldExportTheEmailOfEveryUserInTheOrderTheyRegistered() throws Exception {
        String first = register("ana@example.com");
        String second = register("bia@example.com");

        String exported = export("");

        assertEquals(List.of(first, second), JsonPath.read(exported, "$.content[*].userId"));
        assertEquals(List.of("ana@example.com", "bia@example.com"), JsonPath.read(exported, "$.content[*].email"));
        assertEquals(2, JsonPath.<List<String>>read(exported, "$.content[*].updatedAt").size());
    }

    @Test
    void shouldExportNothingButTheIdTheEmailAndTheMomentOfTheLastChange() throws Exception {
        register("ana@example.com");

        Map<String, Object> user = JsonPath.read(export(""), "$.content[0]");

        assertThat(user).containsOnlyKeys("userId", "email", "updatedAt");
    }

    @Test
    void shouldWalkThroughTheUsersPageByPage() throws Exception {
        register("ana@example.com");
        register("bia@example.com");
        register("caio@example.com");

        String secondPage = export("?page=1&size=2");

        assertEquals(List.of("caio@example.com"), JsonPath.read(secondPage, "$.content[*].email"));
        assertEquals(2, (int) JsonPath.read(secondPage, "$.totalPages"));
    }

    @Test
    void shouldRejectASizeOutsideOneToFiveHundred() throws Exception {
        mockMvc.perform(get(EXPORT).param("size", "0")).andExpect(status().isBadRequest());
        mockMvc.perform(get(EXPORT).param("size", "501")).andExpect(status().isBadRequest());
    }

    @Test
    void shouldForbidRegularUsers() throws Exception {
        mockMvc.perform(get(EXPORT).header("Authorization", asUser()))
                .andExpect(status().isForbidden());
    }
}
