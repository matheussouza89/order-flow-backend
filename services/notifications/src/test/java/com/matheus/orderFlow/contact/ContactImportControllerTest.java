package com.matheus.orderFlow.contact;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ContactImportController.class,
        excludeAutoConfiguration = OAuth2ResourceServerWebSecurityAutoConfiguration.class)
class ContactImportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ContactImportService contactImportService;

    @Test
    void shouldAnswerHowManyContactsWereWritten() throws Exception {
        when(contactImportService.importAll()).thenReturn(12);

        mockMvc.perform(post("/admin/notifications/contacts/import"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        { "imported": 12 }
                        """));
    }
}
