package com.sjp.recruitment.service;

import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.response.AdminCompanyDetailResponse;
import com.sjp.recruitment.model.dto.response.AdminUserSummaryResponse;
import com.sjp.recruitment.model.dto.response.UserResponse;
import com.sjp.recruitment.model.entity.Company;
import com.sjp.recruitment.model.entity.CompanyDocument;
import com.sjp.recruitment.model.entity.User;
import com.sjp.recruitment.repository.CompanyDocumentRepository;
import com.sjp.recruitment.repository.CompanyIndustryRepository;
import com.sjp.recruitment.repository.CompanyLocationRepository;
import com.sjp.recruitment.repository.CompanyRepository;
import com.sjp.recruitment.repository.EmployerRepository;
import com.sjp.recruitment.repository.NotificationRepository;
import com.sjp.recruitment.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceApproveBanTest {

    @Mock private AuthService authService;
    @Mock private UserRepository userRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private CompanyDocumentRepository companyDocumentRepository;
    @Mock private CompanyLocationRepository companyLocationRepository;
    @Mock private CompanyIndustryRepository companyIndustryRepository;
    @Mock private EmployerRepository employerRepository;
    @Mock private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    @Mock private DtoMapper dtoMapper;
    @Mock private AdminOpsService adminOpsService;
    @Mock private NotificationRepository notificationRepository;

    @InjectMocks private AdminService adminService;

    @Test
    void suspendUser_rejectsSelfLock() {
        User admin = adminUser();
        stubAdmin(admin);

        ApiException ex = assertThrows(ApiException.class, () -> adminService.suspendUser(admin.getId().toString()));
        assertEquals("CANNOT_SUSPEND_SELF", ex.getCode());
    }

    @Test
    void suspendUser_setsSuspendedStatus() {
        User admin = adminUser();
        User target = new User();
        target.setId(UUID.randomUUID());
        target.setEmail("user@srp.test");
        target.setRole(User.UserRole.CANDIDATE);
        target.setStatus(User.UserStatus.ACTIVE);
        stubAdmin(admin);
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(userRepository.save(target)).thenReturn(target);

        AdminUserSummaryResponse response = adminService.suspendUser(target.getId().toString());

        assertEquals("SUSPENDED", response.status());
        assertEquals("CANDIDATE", response.role());
        verify(userRepository).save(target);
    }

    @Test
    void suspendUser_rejectsInvalidId() {
        stubAdmin(adminUser());

        ApiException ex = assertThrows(ApiException.class, () -> adminService.suspendUser("not-a-uuid"));
        assertEquals("INVALID_ID", ex.getCode());
    }

    @Test
    void approveCompany_rejectsAlreadyVerified() {
        User admin = adminUser();
        Company company = pendingCompany();
        company.setVerificationStatus("verified");
        stubAdmin(admin);
        when(companyRepository.findById(company.getId())).thenReturn(Optional.of(company));

        ApiException ex = assertThrows(ApiException.class, () -> adminService.approveCompany(company.getId().toString()));
        assertEquals("ALREADY_APPROVED", ex.getCode());
    }

    @Test
    void approveCompany_rejectsPendingDocuments() {
        User admin = adminUser();
        Company company = pendingCompany();
        CompanyDocument pending = new CompanyDocument();
        pending.setStatus("pending");
        stubAdmin(admin);
        when(companyRepository.findById(company.getId())).thenReturn(Optional.of(company));
        when(companyDocumentRepository.findByCompanyIdOrderByUploadedAtDesc(company.getId()))
                .thenReturn(List.of(pending));

        ApiException ex = assertThrows(ApiException.class, () -> adminService.approveCompany(company.getId().toString()));
        assertEquals("DOCUMENTS_PENDING", ex.getCode());
    }

    @Test
    void approveCompany_rejectsRejectedDocuments() {
        User admin = adminUser();
        Company company = pendingCompany();
        CompanyDocument rejected = new CompanyDocument();
        rejected.setStatus("rejected");
        stubAdmin(admin);
        when(companyRepository.findById(company.getId())).thenReturn(Optional.of(company));
        when(companyDocumentRepository.findByCompanyIdOrderByUploadedAtDesc(company.getId()))
                .thenReturn(List.of(rejected));

        ApiException ex = assertThrows(ApiException.class, () -> adminService.approveCompany(company.getId().toString()));
        assertEquals("DOCUMENTS_INCOMPLETE", ex.getCode());
    }

    @Test
    void approveCompany_marksCompanyVerified() {
        User admin = adminUser();
        Company company = pendingCompany();
        stubAdmin(admin);
        when(companyRepository.findById(company.getId())).thenReturn(Optional.of(company));
        when(companyDocumentRepository.findByCompanyIdOrderByUploadedAtDesc(company.getId())).thenReturn(List.of());
        when(companyDocumentRepository.findByCompanyIdAndStatusIgnoreCase(company.getId(), "pending")).thenReturn(List.of());
        when(employerRepository.findOwnerByCompanyId(company.getId())).thenReturn(Optional.empty());
        when(employerRepository.findByCompanyIdWithUser(company.getId())).thenReturn(List.of());
        when(companyLocationRepository.findByCompanyIdOrderByHeadquarterDescCreatedAtDesc(company.getId())).thenReturn(List.of());
        when(companyIndustryRepository.findByCompanyIdOrderByPrimaryDescCreatedAtDesc(company.getId())).thenReturn(List.of());

        AdminCompanyDetailResponse response = adminService.approveCompany(company.getId().toString());

        assertEquals("verified", company.getVerificationStatus());
        assertEquals("active", company.getStatus());
        assertEquals("verified", response.company().verificationStatus());
        verify(adminOpsService).writeAudit(admin.getId().toString(), "COMPANY_APPROVE", "company",
                company.getId().toString(), "pending", "verified");
    }

    @Test
    void approveCompany_rejectsInvalidId() {
        stubAdmin(adminUser());

        ApiException ex = assertThrows(ApiException.class, () -> adminService.approveCompany("bad-id"));
        assertEquals("INVALID_ID", ex.getCode());
    }

    private void stubAdmin(User admin) {
        when(authService.getCurrentUserResponse()).thenReturn(
                new UserResponse(admin.getId().toString(), admin.getEmail(), "ADMIN", "ACTIVE", true)
        );
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
    }

    private static User adminUser() {
        User admin = new User();
        admin.setId(UUID.randomUUID());
        admin.setEmail("admin@srp.test");
        admin.setRole(User.UserRole.ADMIN);
        admin.setStatus(User.UserStatus.ACTIVE);
        return admin;
    }

    private static Company pendingCompany() {
        Company company = new Company();
        company.setId(UUID.randomUUID());
        company.setName("SRP Tech");
        company.setVerificationStatus("pending");
        company.setStatus("pending");
        return company;
    }
}
