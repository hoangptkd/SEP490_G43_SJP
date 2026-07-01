package com.sjp.recruitment.service;

import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.request.CompanyLocationRequest;
import com.sjp.recruitment.model.dto.request.CompanyProfileRequest;
import com.sjp.recruitment.model.dto.response.CompanyLocationResponse;
import com.sjp.recruitment.model.dto.response.CompanyProfileResponse;
import com.sjp.recruitment.model.entity.Company;
import com.sjp.recruitment.model.entity.CompanyLocation;
import com.sjp.recruitment.model.entity.Employer;
import com.sjp.recruitment.model.entity.User;
import com.sjp.recruitment.repository.CompanyLocationRepository;
import com.sjp.recruitment.repository.CompanyRepository;
import com.sjp.recruitment.repository.EmployerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmployerService {

    private final AuthService authService;
    private final EmployerRepository employerRepository;
    private final CompanyRepository companyRepository;
    private final CompanyLocationRepository companyLocationRepository;
    private final DtoMapper dtoMapper;

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
        
        if (request.location() != null && !request.location().isBlank()) {
            List<CompanyLocation> locs = companyLocationRepository.findByCompanyId(company.getId());
            Optional<CompanyLocation> targetHq = locs.stream()
                    .filter(loc -> loc.getBranchName().equalsIgnoreCase(request.location()) || loc.getId().toString().equals(request.location()))
                    .findFirst();
            if (targetHq.isPresent()) {
                for (CompanyLocation loc : locs) {
                    loc.setHeadquarter(loc.getId().equals(targetHq.get().getId()));
                    companyLocationRepository.save(loc);
                }
                company.setLocation(targetHq.get().getBranchName());
            } else {
                company.setLocation(request.location());
            }
        } else {
            company.setLocation(request.location());
        }

        company.setCompanySize(request.companySize());
        company.setTaxCode(request.taxCode());

        return toCompanyProfileResponse(companyRepository.save(company));
    }

    @Transactional(readOnly = true)
    public List<CompanyLocationResponse> getCompanyLocations() {
        Employer employer = getCurrentEmployerOrRegisterPlaceholder();
        return companyLocationRepository.findByCompanyIdOrderByHeadquarterDescCreatedAtDesc(employer.getCompany().getId())
                .stream()
                .map(dtoMapper::toCompanyLocationResponse)
                .toList();
    }

    @Transactional
    public CompanyLocationResponse createCompanyLocation(CompanyLocationRequest request) {
        Employer employer = getCurrentEmployerOrRegisterPlaceholder();
        if (!employer.isOwner()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Chỉ chủ sở hữu công ty mới có quyền thêm địa điểm");
        }
        Company company = employer.getCompany();
        if (request.headquarter()) {
            companyLocationRepository.findByCompanyIdAndHeadquarterTrue(company.getId())
                    .ifPresent(loc -> {
                        loc.setHeadquarter(false);
                        companyLocationRepository.save(loc);
                    });
            company.setLocation(request.branchName());
            companyRepository.save(company);
        } else if (companyLocationRepository.findByCompanyId(company.getId()).isEmpty()) {
            company.setLocation(request.branchName());
            companyRepository.save(company);
        }

        CompanyLocation location = new CompanyLocation();
        location.setCompany(company);
        location.setBranchName(request.branchName());
        location.setAddress(request.address());
        location.setCity(request.city());
        location.setDistrict(request.district());
        location.setCountry(request.country() != null && !request.country().isBlank() ? request.country() : "Vietnam");
        location.setHeadquarter(request.headquarter() || companyLocationRepository.findByCompanyId(company.getId()).isEmpty());

        return dtoMapper.toCompanyLocationResponse(companyLocationRepository.save(location));
    }

    @Transactional
    public CompanyLocationResponse updateCompanyLocation(String id, CompanyLocationRequest request) {
        Employer employer = getCurrentEmployerOrRegisterPlaceholder();
        if (!employer.isOwner()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Chỉ chủ sở hữu công ty mới có quyền sửa địa điểm");
        }
        UUID locId;
        try {
            locId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ID", "ID địa điểm không hợp lệ");
        }
        CompanyLocation location = companyLocationRepository.findByIdAndCompanyId(locId, employer.getCompany().getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy địa điểm làm việc"));

        if (request.headquarter() && !location.isHeadquarter()) {
            companyLocationRepository.findByCompanyIdAndHeadquarterTrue(employer.getCompany().getId())
                    .ifPresent(loc -> {
                        loc.setHeadquarter(false);
                        companyLocationRepository.save(loc);
                    });
            employer.getCompany().setLocation(request.branchName());
            companyRepository.save(employer.getCompany());
        } else if (location.isHeadquarter() && !request.headquarter()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "HEADQUARTER_REQUIRED", "Không thể hủy trụ sở chính, vui lòng đặt chi nhánh khác làm trụ sở chính");
        } else if (location.isHeadquarter()) {
            employer.getCompany().setLocation(request.branchName());
            companyRepository.save(employer.getCompany());
        }

        location.setBranchName(request.branchName());
        location.setAddress(request.address());
        location.setCity(request.city());
        location.setDistrict(request.district());
        if (request.country() != null && !request.country().isBlank()) {
            location.setCountry(request.country());
        }
        location.setHeadquarter(location.isHeadquarter() || request.headquarter());

        return dtoMapper.toCompanyLocationResponse(companyLocationRepository.save(location));
    }

    @Transactional
    public void deleteCompanyLocation(String id) {
        Employer employer = getCurrentEmployerOrRegisterPlaceholder();
        if (!employer.isOwner()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Chỉ chủ sở hữu công ty mới có quyền xóa địa điểm");
        }
        UUID locId;
        try {
            locId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ID", "ID địa điểm không hợp lệ");
        }
        CompanyLocation location = companyLocationRepository.findByIdAndCompanyId(locId, employer.getCompany().getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy địa điểm làm việc"));

        if (location.isHeadquarter()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_DELETE_HEADQUARTER", "Không thể xóa trụ sở chính. Vui lòng đặt chi nhánh khác làm trụ sở chính trước");
        }
        companyLocationRepository.delete(location);
    }

    private CompanyProfileResponse toCompanyProfileResponse(Company company) {
        List<CompanyLocationResponse> locResponses = company.getLocations() == null ? List.of() :
                company.getLocations().stream()
                        .sorted(Comparator.comparing(CompanyLocation::isHeadquarter).reversed()
                                .thenComparing(CompanyLocation::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                        .map(dtoMapper::toCompanyLocationResponse)
                        .toList();
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
                company.getStatus(),
                locResponses
        );
    }
}
