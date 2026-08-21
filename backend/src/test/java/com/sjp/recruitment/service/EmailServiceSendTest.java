package com.sjp.recruitment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailServiceSendTest {

    @Mock private JavaMailSender mailSender;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender);
        ReflectionTestUtils.setField(emailService, "mailFrom", "noreply@srp.test");
    }

    @Test
    void sendVerificationEmail_sendsLinkToRecipient() {
        emailService.sendVerificationEmail("user@srp.test", "https://srp.test/verify?token=abc");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertEquals("noreply@srp.test", message.getFrom());
        assertEquals("user@srp.test", message.getTo()[0]);
        assertTrue(message.getSubject().contains("Xác minh"));
        assertTrue(message.getText().contains("https://srp.test/verify?token=abc"));
    }

    @Test
    void sendVerificationEmail_swallowsMailFailure() {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertDoesNotThrow(() -> emailService.sendVerificationEmail("user@srp.test", "https://srp.test/verify"));
    }

    @Test
    void sendPasswordResetEmail_sendsResetLink() {
        emailService.sendPasswordResetEmail("user@srp.test", "https://srp.test/reset?token=xyz");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertTrue(captor.getValue().getSubject().contains("Đặt lại mật khẩu"));
        assertTrue(captor.getValue().getText().contains("https://srp.test/reset?token=xyz"));
    }
}
