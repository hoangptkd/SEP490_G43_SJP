package com.sjp.recruitment.service;

import com.sjp.recruitment.model.dto.request.JobOfferRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail-from}")
    private String mailFrom;

    @Async
    public void sendVerificationEmail(String email, String verificationLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject("Xác minh tài khoản Smart Recruitment Portal");
        message.setText("""
                Chào bạn,

                Vui lòng bấm link sau để xác minh email:
                %s

                Nếu bạn không tạo tài khoản, hãy bỏ qua email này.
                """.formatted(verificationLink));

        try {
            mailSender.send(message);
            log.info("Email xác minh đã được gửi tới {}", email);
        } catch (Exception e) {
            log.error("Không thể gửi email xác minh tới {}. Lỗi: {}. Link xác minh để test: {}",
                    email, e.getMessage(), verificationLink);
        }
    }

    @Async
    public void sendPasswordResetEmail(String email, String resetLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject("Đặt lại mật khẩu Smart Recruitment Portal");
        message.setText("""
                Chào bạn,

                Vui lòng bấm link sau để đặt lại mật khẩu:
                %s

                Nếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này.
                """.formatted(resetLink));

        try {
            mailSender.send(message);
            log.info("Email đặt lại mật khẩu đã được gửi tới {}", email);
        } catch (Exception e) {
            log.error("Không thể gửi email đặt lại mật khẩu tới {}. Lỗi: {}. Link reset để test: {}",
                    email, e.getMessage(), resetLink);
        }
    }

    @Async
    public void sendApplicationRejectionEmail(String email, String candidateName, String jobTitle, String companyName) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject("Kết quả ứng tuyển vị trí " + jobTitle + " tại " + companyName);
        message.setText("""
                Chào %s,

                Cảm ơn bạn đã quan tâm và ứng tuyển vào vị trí %s tại %s.
                Chúng tôi đã xem xét kỹ hồ sơ của bạn, tuy nhiên, hiện tại kỹ năng và kinh nghiệm của bạn chưa thực sự phù hợp với yêu cầu của vị trí này.

                Chúng tôi sẽ lưu lại hồ sơ của bạn và liên hệ lại nếu có vị trí khác phù hợp hơn trong tương lai.

                Chúc bạn nhiều thành công trên con đường sự nghiệp.

                Trân trọng,
                Đội ngũ Tuyển dụng %s
                """.formatted(candidateName, jobTitle, companyName, companyName));

        try {
            mailSender.send(message);
            log.info("Email từ chối đã được gửi tới {}", email);
        } catch (Exception e) {
            log.error("Không thể gửi email từ chối tới {}. Lỗi: {}", email, e.getMessage());
        }
    }

    @Async
    public void sendInterviewInvitationEmail(String email, String candidateName, String jobTitle, String companyName, String scheduledAt, String location, String meetingLink, String note) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject("Thư mời phỏng vấn vị trí " + jobTitle + " tại " + companyName);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Chào %s,\n\n", candidateName));
        sb.append(String.format("Chúc mừng bạn đã vượt qua vòng loại hồ sơ cho vị trí %s tại %s.\n", jobTitle, companyName));
        sb.append("Chúng tôi trân trọng mời bạn tham gia buổi phỏng vấn để trao đổi chi tiết hơn về công việc.\n\n");
        sb.append("Thông tin buổi phỏng vấn:\n");
        sb.append(String.format("- Thời gian: %s\n", scheduledAt));
        if (location != null && !location.isBlank()) {
            sb.append(String.format("- Địa điểm: %s\n", location));
        }
        if (meetingLink != null && !meetingLink.isBlank()) {
            sb.append(String.format("- Link họp trực tuyến: %s\n", meetingLink));
        }
        if (note != null && !note.isBlank()) {
            sb.append(String.format("- Lưu ý từ nhà tuyển dụng: %s\n", note));
        }
        sb.append("\nVui lòng xác nhận hoặc đề xuất thời gian khác nếu bạn không thể tham gia, thông qua hệ thống Smart Recruitment Portal.\n\n");
        sb.append("Trân trọng,\n");
        sb.append(String.format("Đội ngũ Tuyển dụng %s", companyName));

        message.setText(sb.toString());

        try {
            mailSender.send(message);
            log.info("Email mời phỏng vấn đã được gửi tới {}", email);
        } catch (Exception e) {
            log.error("Không thể gửi email mời phỏng vấn tới {}. Lỗi: {}", email, e.getMessage());
        }
    }

    public void sendInterviewRescheduledEmail(String email, String candidateName, String jobTitle, String companyName, String newScheduledAt, String location, String meetingLink, String note) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject("Lịch phỏng vấn mới cho vị trí " + jobTitle + " tại " + companyName);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Chào %s,\n\n", candidateName));
        sb.append(String.format("Nhà tuyển dụng %s đã đồng ý với yêu cầu đổi lịch phỏng vấn của bạn cho vị trí %s.\n\n", companyName, jobTitle));
        sb.append("Dưới đây là thông tin lịch phỏng vấn mới:\n");
        sb.append(String.format("- Thời gian mới: %s\n", newScheduledAt));
        if (location != null && !location.isBlank()) {
            sb.append(String.format("- Địa điểm: %s\n", location));
        }
        if (meetingLink != null && !meetingLink.isBlank()) {
            sb.append(String.format("- Link họp trực tuyến: %s\n", meetingLink));
        }
        if (note != null && !note.isBlank()) {
            sb.append(String.format("- Lưu ý từ nhà tuyển dụng: %s\n", note));
        }
        sb.append("\nVui lòng truy cập Smart Recruitment Portal để xác nhận tham gia lịch phỏng vấn mới.\n\n");
        sb.append("Trân trọng,\n");
        sb.append(String.format("Đội ngũ Tuyển dụng %s", companyName));

        message.setText(sb.toString());

        try {
            mailSender.send(message);
            log.info("Email cập nhật lịch phỏng vấn (đổi lịch thành công) đã được gửi tới {}", email);
        } catch (Exception e) {
            log.error("Không thể gửi email cập nhật lịch phỏng vấn tới {}. Lỗi: {}", email, e.getMessage());
        }
    }

    public void sendInterviewRescheduleRejectedEmail(String email, String candidateName, String jobTitle, String companyName, String oldScheduledAt, String location, String meetingLink, String note) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject("Kết quả yêu cầu đổi lịch phỏng vấn vị trí " + jobTitle + " tại " + companyName);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Chào %s,\n\n", candidateName));
        sb.append(String.format("Về yêu cầu đổi lịch phỏng vấn cho vị trí %s, chúng tôi rất tiếc hiện tại nhà tuyển dụng không thể sắp xếp thời gian khác phù hợp hơn.\n\n", jobTitle));
        if (note != null && !note.isBlank()) {
            sb.append(String.format("Phản hồi từ nhà tuyển dụng: %s\n\n", note));
        }
        sb.append("Vì vậy, lịch phỏng vấn hiện tại sẽ vẫn được giữ nguyên như sau:\n");
        sb.append(String.format("- Thời gian: %s\n", oldScheduledAt));
        if (location != null && !location.isBlank()) {
            sb.append(String.format("- Địa điểm: %s\n", location));
        }
        if (meetingLink != null && !meetingLink.isBlank()) {
            sb.append(String.format("- Link họp trực tuyến: %s\n", meetingLink));
        }
        sb.append("\nMong bạn có thể sắp xếp thời gian để tham gia buổi phỏng vấn như đã định. Vui lòng đăng nhập hệ thống Smart Recruitment Portal để xác nhận hoặc xem xét bước tiếp theo.\n\n");
        sb.append("Trân trọng,\n");
        sb.append(String.format("Đội ngũ Tuyển dụng %s", companyName));

        message.setText(sb.toString());

        try {
            mailSender.send(message);
            log.info("Email từ chối đổi lịch phỏng vấn đã được gửi tới {}", email);
        } catch (Exception e) {
            log.error("Không thể gửi email từ chối đổi lịch phỏng vấn tới {}. Lỗi: {}", email, e.getMessage());
        }
    }

    public void sendInterviewResultFailedEmail(String email, String candidateName, String jobTitle, String companyName) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject("Kết quả phỏng vấn vị trí " + jobTitle + " tại " + companyName);
        message.setText("""
                Chào %s,

                Cảm ơn bạn đã dành thời gian tham gia buổi phỏng vấn cho vị trí %s tại %s.
                Chúng tôi rất ấn tượng với những chia sẻ của bạn. Tuy nhiên, sau khi cân nhắc kỹ lưỡng các ứng viên, chúng tôi rất tiếc phải thông báo rằng bạn chưa phù hợp với vị trí này ở thời điểm hiện tại.

                Chúng tôi sẽ lưu lại hồ sơ của bạn và mong có cơ hội hợp tác cùng bạn trong tương lai.

                Chúc bạn thành công.

                Trân trọng,
                Đội ngũ Tuyển dụng %s
                """.formatted(candidateName, jobTitle, companyName, companyName));

        try {
            mailSender.send(message);
            log.info("Email báo rớt phỏng vấn đã được gửi tới {}", email);
        } catch (Exception e) {
            log.error("Không thể gửi email báo rớt phỏng vấn tới {}. Lỗi: {}", email, e.getMessage());
        }
    }

    public void sendInterviewResultPassedEmail(String email, String candidateName, String jobTitle, String companyName) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject("Kết quả phỏng vấn vị trí " + jobTitle + " tại " + companyName);
        message.setText("""
                Chào %s,

                Chúc mừng bạn đã hoàn thành xuất sắc buổi phỏng vấn cho vị trí %s tại %s.
                Chúng tôi rất ấn tượng với năng lực của bạn và xin thông báo bạn đã vượt qua vòng phỏng vấn.
                
                Chúng tôi sẽ sớm liên hệ lại để trao đổi thêm hoặc gửi Lời mời làm việc (Job Offer) chính thức đến bạn. Vui lòng kiểm tra email thường xuyên nhé!

                Trân trọng,
                Đội ngũ Tuyển dụng %s
                """.formatted(candidateName, jobTitle, companyName, companyName));

        try {
            mailSender.send(message);
            log.info("Email thông báo đậu phỏng vấn đã được gửi tới {}", email);
        } catch (Exception e) {
            log.error("Không thể gửi email thông báo đậu phỏng vấn tới {}. Lỗi: {}", email, e.getMessage());
        }
    }

    public void sendJobOfferEmail(String email, String candidateName, String jobTitle, String companyName, String companyPhone, String companyEmail, JobOfferRequest request) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject("Thư mời làm việc (Job Offer) cho vị trí " + jobTitle + " tại " + companyName);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Chào %s,\n\n", candidateName));
        sb.append(String.format("Chúc mừng bạn đã xuất sắc vượt qua vòng phỏng vấn. Chúng tôi rất vui mừng được gửi đến bạn lời mời làm việc cho vị trí %s tại %s với các thông tin sau:\n\n", jobTitle, companyName));

        sb.append(String.format("- Chức danh: %s\n", request.positionTitle()));

        if (request.salary() != null) {
            String currency = request.salaryCurrency() != null ? request.salaryCurrency() : "VND";
            String type = request.salaryType() != null ? request.salaryType() : "";
            sb.append(String.format("- Mức lương: %s %s %s\n", request.salary(), currency, type));
        } else {
            sb.append("- Mức lương: Thỏa thuận\n");
        }

        if (request.startDate() != null) {
            sb.append(String.format("- Ngày bắt đầu làm việc: %s\n", request.startDate()));
        }
        if (request.workingLocation() != null && !request.workingLocation().isBlank()) {
            sb.append(String.format("- Địa điểm làm việc: %s\n", request.workingLocation()));
        }
        if (request.benefits() != null && !request.benefits().isBlank()) {
            sb.append(String.format("- Phúc lợi: %s\n", request.benefits()));
        }
        if (request.employerNote() != null && !request.employerNote().isBlank()) {
            sb.append(String.format("- Lời nhắn từ công ty: %s\n", request.employerNote()));
        }

        sb.append("\n");

        if (request.offerLetterUrl() != null && !request.offerLetterUrl().isBlank()) {
            sb.append(String.format("Bạn có thể xem chi tiết thư mời làm việc tại đây: %s\n\n", request.offerLetterUrl()));
        }

        sb.append("Nếu có bất kỳ thắc mắc hoặc cần trao đổi thêm, vui lòng liên hệ với công ty qua:\n");
        sb.append(String.format("- SĐT: %s\n", companyPhone != null && !companyPhone.isBlank() ? companyPhone : "Chưa cập nhật"));
        sb.append(String.format("- Email: %s\n\n", companyEmail != null && !companyEmail.isBlank() ? companyEmail : "Chưa cập nhật"));
        
        sb.append("Mong sớm được chào đón bạn gia nhập vào đội ngũ chúng tôi.\n\n");
        sb.append("Trân trọng,\n");
        sb.append(String.format("Đội ngũ Tuyển dụng %s", companyName));

        message.setText(sb.toString());

        try {
            mailSender.send(message);
            log.info("Email Job Offer đã được gửi tới {}", email);
        } catch (Exception e) {
            log.error("Không thể gửi email Job Offer tới {}. Lỗi: {}", email, e.getMessage());
        }
    }
}
