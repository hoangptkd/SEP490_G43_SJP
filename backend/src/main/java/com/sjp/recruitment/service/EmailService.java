package com.sjp.recruitment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
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
        
        try {
            mailSender.send(message);
            log.info("Email xac minh da duoc gui toi {}", email);
        } catch (Exception e) {
            log.error("Khong the gui email xac minh toi {}. Loi: {}. Link xac minh de test: {}", 
                    email, e.getMessage(), verificationLink);
        }
    }

    public void sendApplicationRejectionEmail(String email, String candidateName, String jobTitle, String companyName) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject("Ket qua ung tuyen vi tri " + jobTitle + " tai " + companyName);
        message.setText("""
                Chao %s,

                Cam on ban da quan tam va ung tuyen vao vi tri %s tai %s.
                Chung toi da xem xet ky ho so cua ban, tuy nhien, hien tai ky nang va kinh nghiem cua ban chua thuc su phu hop voi yeu cau cua vi tri nay.
                
                Chung toi se luu lai ho so cua ban va lien he lai neu co vi tri khac phu hop hon trong tuong lai.
                
                Chuc ban nhieu thanh cong tren con duong su nghiep.
                
                Tran trong,
                Doi ngu Tuyen dung %s
                """.formatted(candidateName, jobTitle, companyName, companyName));
        
        try {
            mailSender.send(message);
            log.info("Email tu choi da duoc gui toi {}", email);
        } catch (Exception e) {
            log.error("Khong the gui email tu choi toi {}. Loi: {}", email, e.getMessage());
        }
    }

    public void sendInterviewInvitationEmail(String email, String candidateName, String jobTitle, String companyName, String scheduledAt, String location, String meetingLink, String note) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject("Thu moi phong van vi tri " + jobTitle + " tai " + companyName);
        
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Chao %s,\n\n", candidateName));
        sb.append(String.format("Chuc mung ban da vuot qua vong loai ho so cho vi tri %s tai %s.\n", jobTitle, companyName));
        sb.append("Chung toi tran trong moi ban tham gia buoi phong van de trao doi chi tiet hon ve cong viec.\n\n");
        sb.append("Thong tin buoi phong van:\n");
        sb.append(String.format("- Thoi gian: %s\n", scheduledAt));
        if (location != null && !location.isBlank()) {
            sb.append(String.format("- Dia diem: %s\n", location));
        }
        if (meetingLink != null && !meetingLink.isBlank()) {
            sb.append(String.format("- Link hop truc tuyen: %s\n", meetingLink));
        }
        if (note != null && !note.isBlank()) {
            sb.append(String.format("- Luu y tu nha tuyen dung: %s\n", note));
        }
        sb.append("\nVui long xac nhan hoac de xuat thoi gian khac neu ban khong the tham gia, thong qua he thong Smart Recruitment Portal.\n\n");
        sb.append("Tran trong,\n");
        sb.append(String.format("Doi ngu Tuyen dung %s", companyName));
        
        message.setText(sb.toString());
        
        try {
            mailSender.send(message);
            log.info("Email moi phong van da duoc gui toi {}", email);
        } catch (Exception e) {
            log.error("Khong the gui email moi phong van toi {}. Loi: {}", email, e.getMessage());
        }
    }

    public void sendInterviewResultFailedEmail(String email, String candidateName, String jobTitle, String companyName) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject("Ket qua phong van vi tri " + jobTitle + " tai " + companyName);
        message.setText("""
                Chao %s,

                Cam on ban da danh thoi gian tham gia buoi phong van cho vi tri %s tai %s.
                Chung toi rat an tuong voi nhung chia se cua ban. Tuy nhien, sau khi can nhac ky luong cac ung vien, chung toi rat tiec phai thong bao rang ban chua phu hop voi vi tri nay o thoi diem hien tai.
                
                Chung toi se luu lai ho so cua ban va mong co co hoi hop tac cung ban trong tuong lai.
                
                Chuc ban thanh cong.
                
                Tran trong,
                Doi ngu Tuyen dung %s
                """.formatted(candidateName, jobTitle, companyName, companyName));
        
        try {
            mailSender.send(message);
            log.info("Email bao rot phong van da duoc gui toi {}", email);
        } catch (Exception e) {
            log.error("Khong the gui email bao rot phong van toi {}. Loi: {}", email, e.getMessage());
        }
    }

    public void sendJobOfferEmail(String email, String candidateName, String jobTitle, String companyName, String offerLetterUrl) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject("Thu moi lam viec (Job Offer) cho vi tri " + jobTitle + " tai " + companyName);
        
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Chao %s,\n\n", candidateName));
        sb.append(String.format("Chuc mung ban da xuat sac vuot qua cac vong phong van. Chung toi rat vui mung duoc gui den ban loi moi lam viec cho vi tri %s tai %s.\n\n", jobTitle, companyName));
        
        if (offerLetterUrl != null && !offerLetterUrl.isBlank()) {
            sb.append(String.format("Ban co the xem chi tiet thu moi lam viec tai day: %s\n\n", offerLetterUrl));
        }
        
        sb.append("Vui long dang nhap vao he thong Smart Recruitment Portal de xem chi tiet va xac nhan phan hoi cua ban.\n\n");
        sb.append("Mong som duoc chao don ban gia nhap vao doi ngu chung toi.\n\n");
        sb.append("Tran trong,\n");
        sb.append(String.format("Doi ngu Tuyen dung %s", companyName));
        
        message.setText(sb.toString());
        
        try {
            mailSender.send(message);
            log.info("Email Job Offer da duoc gui toi {}", email);
        } catch (Exception e) {
            log.error("Khong the gui email Job Offer toi {}. Loi: {}", email, e.getMessage());
        }
    }
}
