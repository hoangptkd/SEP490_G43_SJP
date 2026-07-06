package com.sjp.recruitment.service;

import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.request.CompanyReviewRequest;
import com.sjp.recruitment.model.dto.response.AdminCompanyDetailResponse;
import com.sjp.recruitment.model.dto.response.AdminCompanyOwnerResponse;
import com.sjp.recruitment.model.dto.response.AdminCompanySummaryResponse;
import com.sjp.recruitment.model.dto.response.CompanyDocumentResponse;
import com.sjp.recruitment.model.dto.response.CompanyLocationResponse;
import com.sjp.recruitment.model.dto.response.CompanyProfileResponse;
import com.sjp.recruitment.model.dto.response.UserResponse;
import com.sjp.recruitment.model.entity.Company;
import com.sjp.recruitment.model.entity.CompanyDocument;
import com.sjp.recruitment.model.entity.Employer;
import com.sjp.recruitment.model.entity.User;
import com.sjp.recruitment.repository.CompanyDocumentRepository;
import com.sjp.recruitment.repository.CompanyLocationRepository;
import com.sjp.recruitment.repository.CompanyRepository;
import com.sjp.recruitment.repository.EmployerRepository;
import com.sjp.recruitment.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final CompanyDocumentRepository companyDocumentRepository;
    private final CompanyLocationRepository companyLocationRepository;
    private final EmployerRepository employerRepository;
    private final DtoMapper dtoMapper;

    @Transactional(readOnly = true)
    public List<AdminCompanySummaryResponse> listCompanies(String status) {
        requireAdmin();
        List<Company> companies = resolveCompanies(status);
        return companies.stream().map(this::toSummaryResponse).toList();
    }

    @Transactional(readOnly = true)
    public AdminCompanyDetailResponse getCompanyDetail(String id) {
        requireAdmin();
        Company company = findCompany(id);
        return toDetailResponse(company);
    }

    @Transactional
    public AdminCompanyDetailResponse approveCompany(String id) {
        User admin = requireAdminUser();
        Company company = findCompany(id);
        if ("verified".equalsIgnoreCase(company.getVerificationStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ALREADY_APPROVED", "Hồ sơ công ty đã được duyệt trước đó");
        }

        LocalDateTime now = LocalDateTime.now();
        company.setVerificationStatus("verified");
        company.setStatus("active");
        companyRepository.save(company);

        reviewPendingDocuments(company, admin, "approved", null, now);
        updateEmployerVerification(company.getId(), "verified");

        return toDetailResponse(company);
    }

    @Transactional
    public AdminCompanyDetailResponse rejectCompany(String id, CompanyReviewRequest request) {
        User admin = requireAdminUser();
        if (request == null || !StringUtils.hasText(request.reason())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REASON_REQUIRED", "Vui lòng nhập lý do từ chối");
        }

        Company company = findCompany(id);
        LocalDateTime now = LocalDateTime.now();
        String reason = request.reason().trim();

        company.setVerificationStatus("rejected");
        company.setStatus("rejected");
        companyRepository.save(company);

        reviewPendingDocuments(company, admin, "rejected", reason, now);
        updateEmployerVerification(company.getId(), "rejected");

        return toDetailResponse(company);
    }

    private List<Company> resolveCompanies(String status) {
        if (!StringUtils.hasText(status) || "all".equalsIgnoreCase(status)) {
            return companyRepository.findAllByOrderByUpdatedAtDesc();
        }
        return companyRepository.findByVerificationStatusIgnoreCaseOrderByUpdatedAtDesc(status.trim().toLowerCase(Locale.ROOT));
    }

    private Company findCompany(String id) {
        UUID companyId;
        try {
            companyId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ID", "ID công ty không hợp lệ");
        }
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy công ty"));
    }

    private void requireAdmin() {
        UserResponse user = authService.getCurrentUserResponse();
        if (!"ADMIN".equalsIgnoreCase(user.role())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Chỉ dành cho quản trị viên");
        }
    }

    private User requireAdminUser() {
        requireAdmin();
        UserResponse current = authService.getCurrentUserResponse();
        UUID userId;
        try {
            userId = UUID.fromString(current.id());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "Không tìm thấy người dùng quản trị");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "Không tìm thấy người dùng quản trị"));
    }

    private void reviewPendingDocuments(Company company, User admin, String status, String reason, LocalDateTime reviewedAt) {
        List<CompanyDocument> pendingDocs = companyDocumentRepository
                .findByCompanyIdAndStatusIgnoreCase(company.getId(), "pending");
        for (CompanyDocument doc : pendingDocs) {
            doc.setStatus(status);
            doc.setRejectReason("rejected".equalsIgnoreCase(status) ? reason : null);
            doc.setReviewedAt(reviewedAt);
            doc.setReviewedBy(admin);
            companyDocumentRepository.save(doc);
        }
    }

    private void updateEmployerVerification(UUID companyId, String status) {
        employerRepository.findOwnerByCompanyId(companyId)
                .ifPresent(owner -> {
                    owner.setVerificationStatus(status);
                    employerRepository.save(owner);
                });
    }

    private AdminCompanySummaryResponse toSummaryResponse(Company company) {
        Employer owner = findPrimaryEmployer(company.getId());

        String ownerEmail = owner != null && owner.getUser() != null ? owner.getUser().getEmail() : null;
        String ownerName = owner != null && owner.getUser() != null ? owner.getUser().getFullName() : null;
        long documentCount = companyDocumentRepository.countByCompanyId(company.getId());
        long pendingDocumentCount = companyDocumentRepository.countByCompanyIdAndStatusIgnoreCase(company.getId(), "pending");

        return new AdminCompanySummaryResponse(
                String.valueOf(company.getId()),
                company.getName(),
                company.getIndustry(),
                company.getTaxCode(),
                company.getVerificationStatus(),
                company.getStatus(),
                ownerEmail,
                ownerName,
                (int) documentCount,
                (int) pendingDocumentCount,
                company.getCreatedAt(),
                company.getUpdatedAt()
        );
    }

    private AdminCompanyDetailResponse toDetailResponse(Company company) {
        List<CompanyLocationResponse> locations = companyLocationRepository
                .findByCompanyIdOrderByHeadquarterDescCreatedAtDesc(company.getId())
                .stream()
                .map(dtoMapper::toCompanyLocationResponse)
                .toList();

        CompanyProfileResponse profile = new CompanyProfileResponse(
                String.valueOf(company.getId()),
                company.getName(),
                company.getDescription(),
                company.getWebsite(),
                company.getIndustry(),
                company.getLocation(),
                company.getCompanySize(),
                company.getTaxCode(),
                company.getLogoUrl(),
                company.isVerified(),
                company.getVerificationStatus(),
                company.getStatus(),
                locations
        );

        List<CompanyDocumentResponse> documents = companyDocumentRepository
                .findByCompanyIdOrderByUploadedAtDesc(company.getId())
                .stream()
                .map(dtoMapper::toCompanyDocumentResponse)
                .toList();

        AdminCompanyOwnerResponse ownerResponse = buildOwnerResponse(company.getId());

        return new AdminCompanyDetailResponse(
                profile,
                documents,
                ownerResponse,
                company.getCreatedAt(),
                company.getUpdatedAt()
        );
    }

    private AdminCompanyOwnerResponse buildOwnerResponse(UUID companyId) {
        Employer owner = findPrimaryEmployer(companyId);
        if (owner == null || owner.getUser() == null) {
            return null;
        }
        User user = owner.getUser();
        return new AdminCompanyOwnerResponse(
                String.valueOf(owner.getId()),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                owner.getPosition(),
                owner.getVerificationStatus()
        );
    }

    private Employer findPrimaryEmployer(UUID companyId) {
        return employerRepository.findOwnerByCompanyId(companyId)
                .orElseGet(() -> employerRepository.findByCompanyIdWithUser(companyId).stream().findFirst().orElse(null));
    }
}
