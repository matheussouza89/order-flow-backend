package com.matheus.orderFlow.user;

import com.matheus.orderFlow.shared.exception.DomainValidationException;
import com.matheus.orderFlow.shared.exception.InvalidLoginException;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    private static final String REGISTER_JSON = """
            {
                "name": "Matheus",
                "email": "matheus@example.com",
                "password": "senhaSegura1"
            }
            """;

    private static final String LOGIN_JSON = """
            { "email": "matheus@example.com", "password": "senhaSegura1" }
            """;

    private UserResponse user(UUID id) {
        return new UserResponse(id, "Matheus", "matheus@example.com",
                Instant.now(), Instant.now());
    }

    @Test
    void shouldRegisterReturningCreatedAndLocation() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.createUser(any(UserDto.class))).thenReturn(user(id));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWithUserId(id)))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.email").value("matheus@example.com"));
    }

    private static org.hamcrest.Matcher<String> endsWithUserId(UUID id) {
        return org.hamcrest.Matchers.endsWith("/users/" + id);
    }

    @Test
    void shouldNotEchoThePasswordOnRegistration() throws Exception {
        when(userService.createUser(any(UserDto.class))).thenReturn(user(UUID.randomUUID()));

        String response = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_JSON))
                .andReturn().getResponse().getContentAsString();

        assertFalse(response.contains("senhaSegura1"));
        assertFalse(response.contains("password"));
    }

    @Test
    void shouldPassTheBodyThroughToTheService() throws Exception {
        when(userService.createUser(any(UserDto.class))).thenReturn(user(UUID.randomUUID()));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_JSON))
                .andExpect(status().isCreated());

        verify(userService).createUser(
                new UserDto("Matheus", "matheus@example.com", "senhaSegura1"));
    }

    @Test
    void shouldReturnConflictWhenTheEmailIsTaken() throws Exception {
        when(userService.createUser(any(UserDto.class)))
                .thenThrow(new UserAlreadyExistsException("Email already registered"));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void shouldReturnBadRequestWhenTheDomainRejectsTheData() throws Exception {
        when(userService.createUser(any(UserDto.class)))
                .thenThrow(new DomainValidationException("password", "too short"));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value("too short"));
    }

    @Test
    void shouldReturnBadRequestForMalformedJson() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"name\": "))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    @Test
    void shouldReturnTheTokenOnLogin() throws Exception {
        when(userService.login("matheus@example.com", "senhaSegura1"))
                .thenReturn(new TokenResponse("token-assinado", "Bearer", 3600));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token-assinado"))
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    @Test
    void shouldReturnUnauthorizedWhenCredentialsAreInvalid() throws Exception {
        when(userService.login(any(), any()))
                .thenThrow(new InvalidLoginException("Invalid credentials"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }
}
