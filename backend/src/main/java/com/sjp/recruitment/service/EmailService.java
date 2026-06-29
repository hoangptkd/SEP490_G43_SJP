package com.sjp.recruitment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail-from}")
    private String mailFrom;

    public void sendVerificationEmail(String email, String verificationLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject("Xac minh tai khoan Smart Recruitment Portal");
        message.setText("""
                Chao ban,

                Vui long bam link sau de xac minh email:
                %s

                Neu ban khong tao tai khoan, hay bo qua email nay.
                """.formatted(verificationLink));
        mailSender.send(message);
    }
}
