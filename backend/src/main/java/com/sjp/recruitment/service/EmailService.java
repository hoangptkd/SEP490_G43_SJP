package com.sjp.recruitment.service;

import com.sjp.recruitment.model.dto.request.JobOfferRequest;
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

    public void sendPasswordResetEmail(String email, String resetLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject("Dat lai mat khau Smart Recruitment Portal");
        message.setText("""
                Chao ban,

                Vui long bam link sau de dat lai mat khau:
                %s

                Neu ban khong yeu cau dat lai mat khau, hay bo qua email nay.
                """.formatted(resetLink));

        try {
            mailSender.send(message);
            log.info("Email dat lai mat khau da duoc gui toi {}", email);
        } catch (Exception e) {
            log.error("Khong the gui email dat lai mat khau toi {}. Loi: {}. Link reset de test: {}",
                    email, e.getMessage(), resetLink);
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

    public void sendInterviewRescheduledEmail(String email, String candidateName, String jobTitle, String companyName, String newScheduledAt, String location, String meetingLink, String note) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject("Lich phong van moi cho vi tri " + jobTitle + " tai " + companyName);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Chao %s,\n\n", candidateName));
        sb.append(String.format("Nha tuyen dung %s da dong y voi yeu cau doi lich phong van cua ban cho vi tri %s.\n\n", companyName, jobTitle));
        sb.append("Duoi day la thong tin lich phong van moi:\n");
        sb.append(String.format("- Thoi gian moi: %s\n", newScheduledAt));
        if (location != null && !location.isBlank()) {
            sb.append(String.format("- Dia diem: %s\n", location));
        }
        if (meetingLink != null && !meetingLink.isBlank()) {
            sb.append(String.format("- Link hop truc tuyen: %s\n", meetingLink));
        }
        if (note != null && !note.isBlank()) {
            sb.append(String.format("- Luu y tu nha tuyen dung: %s\n", note));
        }
        sb.append("\nVui long kiem tra he thong Smart Recruitment Portal va luu lai lich moi.\n\n");
        sb.append("Tran trong,\n");
        sb.append(String.format("Doi ngu Tuyen dung %s", companyName));

        message.setText(sb.toString());

        try {
            mailSender.send(message);
            log.info("Email cap nhat lich phong van (doi lich thanh cong) da duoc gui toi {}", email);
        } catch (Exception e) {
            log.error("Khong the gui email cap nhat lich phong van toi {}. Loi: {}", email, e.getMessage());
        }
    }

    public void sendInterviewRescheduleRejectedEmail(String email, String candidateName, String jobTitle, String companyName, String oldScheduledAt, String location, String meetingLink, String note) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject("Ket qua yeu cau doi lich phong van vi tri " + jobTitle + " tai " + companyName);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Chao %s,\n\n", candidateName));
        sb.append(String.format("Ve yeu cau doi lich phong van cho vi tri %s, chung toi rat tiec hien tai nha tuyen dung khong the sap xep thoi gian khac phu hop hon.\n\n", jobTitle));
        if (note != null && !note.isBlank()) {
            sb.append(String.format("Phan hoi tu nha tuyen dung: %s\n\n", note));
        }
        sb.append("Vi vay, lich phong van hien tai se van duoc giu nguyen nhu sau:\n");
        sb.append(String.format("- Thoi gian: %s\n", oldScheduledAt));
        if (location != null && !location.isBlank()) {
            sb.append(String.format("- Dia diem: %s\n", location));
        }
        if (meetingLink != null && !meetingLink.isBlank()) {
            sb.append(String.format("- Link hop truc tuyen: %s\n", meetingLink));
        }
        sb.append("\nMong ban co the sap xep thoi gian de tham gia buoi phong van nhu da dinh. Vui long dang nhap he thong Smart Recruitment Portal de xac nhan hoac xem xet buoc tiep theo.\n\n");
        sb.append("Tran trong,\n");
        sb.append(String.format("Doi ngu Tuyen dung %s", companyName));

        message.setText(sb.toString());

        try {
            mailSender.send(message);
            log.info("Email tu choi doi lich phong van da duoc gui toi {}", email);
        } catch (Exception e) {
            log.error("Khong the gui email tu choi doi lich phong van toi {}. Loi: {}", email, e.getMessage());
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

    public void sendInterviewResultPassedEmail(String email, String candidateName, String jobTitle, String companyName) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject("Ket qua phong van vi tri " + jobTitle + " tai " + companyName);
        message.setText("""
                Chao %s,

                Chuc mung ban da hoan thanh xuat sac buoi phong van cho vi tri %s tai %s.
                Chung toi rat an tuong voi nang luc cua ban va xin thong bao ban da vuot qua vong phong van.
                
                Chung toi se som lien he lai de trao doi them hoac gui Loi moi lam viec (Job Offer) chinh thuc den ban. Vui long kiem tra email thuong xuyen nhe!

                Tran trong,
                Doi ngu Tuyen dung %s
                """.formatted(candidateName, jobTitle, companyName, companyName));

        try {
            mailSender.send(message);
            log.info("Email thong bao dau phong van da duoc gui toi {}", email);
        } catch (Exception e) {
            log.error("Khong the gui email thong bao dau phong van toi {}. Loi: {}", email, e.getMessage());
        }
    }

    public void sendJobOfferEmail(String email, String candidateName, String jobTitle, String companyName, JobOfferRequest request) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject("Thu moi lam viec (Job Offer) cho vi tri " + jobTitle + " tai " + companyName);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Chao %s,\n\n", candidateName));
        sb.append(String.format("Chuc mung ban da xuat sac vuot qua cac vong phong van. Chung toi rat vui mung duoc gui den ban loi moi lam viec cho vi tri %s tai %s voi cac thong tin sau:\n\n", jobTitle, companyName));

        sb.append(String.format("- Chuc danh: %s\n", request.positionTitle()));

        if (request.salary() != null) {
            String currency = request.salaryCurrency() != null ? request.salaryCurrency() : "VND";
            String type = request.salaryType() != null ? request.salaryType() : "";
            sb.append(String.format("- Muc luong: %s %s %s\n", request.salary(), currency, type));
        } else {
            sb.append("- Muc luong: Thoa thuan\n");
        }

        if (request.startDate() != null) {
            sb.append(String.format("- Ngay bat dau lam viec: %s\n", request.startDate()));
        }
        if (request.workingLocation() != null && !request.workingLocation().isBlank()) {
            sb.append(String.format("- Dia diem lam viec: %s\n", request.workingLocation()));
        }
        if (request.benefits() != null && !request.benefits().isBlank()) {
            sb.append(String.format("- Phuc loi: %s\n", request.benefits()));
        }
        if (request.employerNote() != null && !request.employerNote().isBlank()) {
            sb.append(String.format("- Loi nhan tu cong ty: %s\n", request.employerNote()));
        }

        sb.append("\n");

        if (request.offerLetterUrl() != null && !request.offerLetterUrl().isBlank()) {
            sb.append(String.format("Ban co the xem chi tiet thu moi lam viec tai day: %s\n\n", request.offerLetterUrl()));
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
