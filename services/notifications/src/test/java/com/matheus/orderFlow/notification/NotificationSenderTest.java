package com.matheus.orderFlow.notification;

import com.matheus.orderFlow.contact.ContactService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationSenderTest {
    private static final UUID USER = UUID.randomUUID();

    @Mock
    private NotificationService notificationService;

    @Mock
    private ContactService contactService;

    @Mock
    private MailSender mailSender;

    private NotificationSender sender;
    private Notification notification;

    @BeforeEach
    void setUp() {
        sender = new NotificationSender(notificationService, contactService, mailSender,
                new NotificationMessages("nao-responda@orderflow.local"));
        notification = new Notification(UUID.randomUUID(), OrderStatus.CONFIRMED, 1L, USER,
                new BigDecimal("150.00"), Instant.now());
        ReflectionTestUtils.setField(notification, "id", UUID.randomUUID());
        lenient().when(notificationService.due()).thenReturn(List.of(notification));
    }

    @Test
    void shouldSendToTheCurrentEmailOfTheCustomer() {
        when(contactService.emailOf(USER)).thenReturn(Optional.of("ana@example.com"));
        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);

        sender.sendDue();

        verify(mailSender).send(message.capture());
        assertArrayEquals(new String[]{"ana@example.com"}, message.getValue().getTo());
        assertEquals("nao-responda@orderflow.local", message.getValue().getFrom());
        verify(notificationService).recordSent(notification.getId());
    }

    @Test
    void shouldCountAnAttemptWithoutSendingWhenTheContactIsUnknown() {
        when(contactService.emailOf(USER)).thenReturn(Optional.empty());

        sender.sendDue();

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        verify(notificationService).recordNoRecipient(notification.getId());
    }

    @Test
    void shouldNotSendAStatusOvertakenByANewerOneAlreadySent() {
        when(notificationService.supersedeIfOvertaken(notification.getId())).thenReturn(true);

        sender.sendDue();

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        verify(notificationService, never()).recordSent(any());
        verify(notificationService, never()).recordNoRecipient(any());
    }

    @Test
    void shouldCountAnAttemptWithoutSendingWhenTheFactDidNotNameTheCustomer() {
        Notification anonymous = new Notification(UUID.randomUUID(), OrderStatus.SHIPPED, 2L, null, null,
                Instant.now());
        ReflectionTestUtils.setField(anonymous, "id", UUID.randomUUID());
        when(notificationService.due()).thenReturn(List.of(anonymous));

        sender.sendDue();

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        verify(notificationService).recordNoRecipient(anonymous.getId());
    }

    @Test
    void shouldCountAnAttemptAndKeepTheErrorWhenTheMailServerFails() {
        when(contactService.emailOf(USER)).thenReturn(Optional.of("ana@example.com"));
        doThrow(new MailSendException("Connection refused")).when(mailSender).send(any(SimpleMailMessage.class));

        sender.sendDue();

        verify(notificationService).recordFailure(eq(notification.getId()), contains("Connection refused"));
        verify(notificationService, never()).recordSent(any());
    }
}
