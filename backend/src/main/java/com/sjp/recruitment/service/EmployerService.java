package com.sjp.recruitment.service;

import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.request.CompanyProfileRequest;
import com.sjp.recruitment.model.dto.response.CompanyProfileResponse;
import com.sjp.recruitment.model.entity.Company;
import com.sjp.recruitment.model.entity.Employer;
import com.sjp.recruitment.model.entity.User;
import com.sjp.recruitment.repository.CompanyRepository;
import com.sjp.recruitment.repository.EmployerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmployerService {

    private final AuthService authService;
    private final EmployerRepository employerRepository;
    private final CompanyRepository companyRepository;

    @Transactional
    public Employer getCurrentEmployerOrRegisterPlaceholder() {
        User user = authService.getCurrentUser();
        if (user.getRoleEnum() != User.UserRole.EMPLOYER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Chỉ dành cho Nhà tuyển dụng");
        }
        
        return employerRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    // Tạo một công ty tạm thời cho nhà tuyển dụng
                    String baseName = "Công ty của " + (user.getFullName() != null && !user.getFullName().isEmpty() ? user.getFullName() : user.getEmail());
                    String name = baseName;
                    int count = 1;
                    while (companyRepository.findByName(name).isPresent()) {
                        name = baseName + " (" + count + ")";
                        count++;
                    }

                    Company company = new Company();
                    company.setName(name);
                    company.setDescription("Chưa có mô tả");
                    company.setStatus("pending");
                    company.setVerified(false);
                    company = companyRepository.save(company);

                    // Tạo hồ sơ Employer
                    Employer employer = new Employer();
                    employer.setUser(user);
                    employer.setCompany(company);
                    employer.setOwner(true);
                    employer.setVerificationStatus("pending");
                    employer.setPosition("Quản trị viên");
                    return employerRepository.save(employer);
                });
    }

    @Transactional
    public CompanyProfileResponse getCompanyProfile() {
        Employer employer = getCurrentEmployerOrRegisterPlaceholder();
        Company company = employer.getCompany();
        return toCompanyProfileResponse(company);
    }

    @Transactional
    public CompanyProfileResponse updateCompanyProfile(CompanyProfileRequest request) {
        Employer employer = getCurrentEmployerOrRegisterPlaceholder();
        if (!employer.isOwner()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Chỉ chủ sở hữu công ty mới có quyền chỉnh sửa");
        }
        Company company = employer.getCompany();
        
        // Kiểm tra tên công ty trùng lặp
        if (!company.getName().equalsIgnoreCase(request.name())) {
            companyRepository.findByName(request.name()).ifPresent(existing -> {
                if (!existing.getId().equals(company.getId())) {
                    throw new ApiException(HttpStatus.CONFLICT, "COMPANY_NAME_EXISTS", "Tên công ty đã tồn tại");
                }
            });
        }

        company.setName(request.name());
        company.setDescription(request.description());
        company.setWebsite(request.website());
        company.setIndustry(request.industry());
        company.setLocation(request.location());
        company.setCompanySize(request.companySize());
        company.setTaxCode(request.taxCode());

        return toCompanyProfileResponse(companyRepository.save(company));
    }

    private CompanyProfileResponse toCompanyProfileResponse(Company company) {
        return new CompanyProfileResponse(
                String.valueOf(company.getId()),
                company.getName(),
                company.getDescription(),
                company.getWebsite(),
                company.getIndustry(),
                company.getLocation(),
                company.getCompanySize(),
                company.getTaxCode(),
                company.isVerified(),
                company.getStatus()
        );
    }
}
