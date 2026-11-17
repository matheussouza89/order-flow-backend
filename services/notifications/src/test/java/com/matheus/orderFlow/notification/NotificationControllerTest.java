package com.matheus.orderFlow.notification;

import com.matheus.orderFlow.shared.web.PageResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = NotificationController.class,
        excludeAutoConfiguration = OAuth2ResourceServerWebSecurityAutoConfiguration.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void shouldListTheFailedOnesByDefaultMostRecentFirst() throws Exception {
        UUID orderId = UUID.randomUUID();
        NotificationResponse failed = new NotificationResponse(UUID.randomUUID(), orderId, OrderStatus.CONFIRMED,
                NotificationState.FAILED, 5, "MailSendException: Connection refused", Instant.now());
        when(notificationService.list(eq(NotificationState.FAILED), any()))
                .thenReturn(new PageResponse<>(List.of(failed), 0, 20, 1, 1));
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);

        mockMvc.perform(get("/admin/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.content[0].orderStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.content[0].state").value("FAILED"))
                .andExpect(jsonPath("$.content[0].attempts").value(5))
                .andExpect(jsonPath("$.content[0].lastError").value("MailSendException: Connection refused"));

        verify(notificationService).list(eq(NotificationState.FAILED), pageable.capture());
        assertEquals(20, pageable.getValue().getPageSize());
        assertEquals(Sort.by(Sort.Direction.DESC, "updatedAt"), pageable.getValue().getSort());
    }

    @Test
    void shouldRefuseAnUnknownState() throws Exception {
        mockMvc.perform(get("/admin/notifications").param("state", "LOST"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(notificationService);
    }
}
