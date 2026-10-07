package com.matheus.orderFlow.user;

import com.matheus.orderFlow.shared.exception.DomainValidationException;
import com.matheus.orderFlow.shared.exception.NotFoundException;
import com.matheus.orderFlow.shared.exception.UserAlreadyExistsException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    private UserResponse user(UUID id, String name, String email) {
        return new UserResponse(id, name, email, Instant.now(), Instant.now());
    }

    @Test
    void shouldGetUserById() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.getUser(id)).thenReturn(user(id, "Matheus", "matheus@example.com"));

        mockMvc.perform(get("/users/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Matheus"))
                .andExpect(jsonPath("$.email").value("matheus@example.com"));
    }

    @Test
    void shouldNeverExposeThePasswordHash() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.getUser(id)).thenReturn(user(id, "Matheus", "matheus@example.com"));

        String response = mockMvc.perform(get("/users/{id}", id))
                .andReturn().getResponse().getContentAsString();

        assertFalse(response.contains("password"));
        assertFalse(response.contains("hash"));
    }

    @Test
    void shouldReturnNotFoundForUnknownUser() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.getUser(id)).thenThrow(new NotFoundException(id));

        mockMvc.perform(get("/users/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnBadRequestWhenTheIdIsNotAUuid() throws Exception {
        mockMvc.perform(get("/users/{id}", "nao-e-uuid"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    @Test
    void shouldUpdateName() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.updateName(id, "Matheus Souza"))
                .thenReturn(user(id, "Matheus Souza", "matheus@example.com"));

        mockMvc.perform(put("/users/{id}/name", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"name\": \"Matheus Souza\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Matheus Souza"));

        verify(userService).updateName(id, "Matheus Souza");
    }

    @Test
    void shouldReturnBadRequestWhenTheNameIsRejected() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.updateName(any(), any()))
                .thenThrow(new DomainValidationException("name", "User name cannot be empty"));

        mockMvc.perform(put("/users/{id}/name", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"name\": \" \" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").isNotEmpty());
    }

    @Test
    void shouldUpdateEmail() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.updateEmail(id, "novo@example.com"))
                .thenReturn(user(id, "Matheus", "novo@example.com"));

        mockMvc.perform(put("/users/{id}/email", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"email\": \"novo@example.com\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("novo@example.com"));
    }

    @Test
    void shouldReturnConflictWhenTheNewEmailIsTaken() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.updateEmail(any(), any()))
                .thenThrow(new UserAlreadyExistsException("Email already registered"));

        mockMvc.perform(put("/users/{id}/email", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"email\": \"ocupado@example.com\" }"))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldUpdatePasswordWithoutEchoingIt() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.updatePassword(id, "outraSenhaForte1"))
                .thenReturn(user(id, "Matheus", "matheus@example.com"));

        String response = mockMvc.perform(put("/users/{id}/password", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"password\": \"outraSenhaForte1\" }"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertFalse(response.contains("outraSenhaForte1"));
        verify(userService).updatePassword(id, "outraSenhaForte1");
    }

    @Test
    void shouldReturnBadRequestWhenTheNewPasswordIsWeak() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.updatePassword(any(), any()))
                .thenThrow(new DomainValidationException("password", "too short"));

        mockMvc.perform(put("/users/{id}/password", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"password\": \"123\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").isNotEmpty());
    }
}
