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
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.sjp.recruitment.model.dto.response.CompanyDocumentResponse;
import com.sjp.recruitment.model.entity.CompanyDocument;
import com.sjp.recruitment.repository.CompanyDocumentRepository;
import com.sjp.recruitment.model.dto.request.JobRequest;
import com.sjp.recruitment.model.dto.response.JobResponse;
import com.sjp.recruitment.repository.JobRepository;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;
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
    private final CompanyDocumentRepository companyDocumentRepository;
    private final JobRepository jobRepository;
    private final JobService jobService;
    private final Cloudinary cloudinary;
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
                    company.setVerificationStatus("unverified");
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
        if (request.logoUrl() != null) {
            company.setLogoUrl(request.logoUrl());
        }

        markCompanyPendingReviewIfNeeded(company);

        return toCompanyProfileResponse(companyRepository.save(company));
    }

    private void markCompanyPendingReviewIfNeeded(Company company) {
        String verificationStatus = company.getVerificationStatus();
        if (verificationStatus == null
                || "unverified".equalsIgnoreCase(verificationStatus)
                || "rejected".equalsIgnoreCase(verificationStatus)) {
            company.setVerificationStatus("pending");
            if (company.getStatus() == null
                    || "pending".equalsIgnoreCase(company.getStatus())
                    || "rejected".equalsIgnoreCase(company.getStatus())) {
                company.setStatus("pending");
            }
        }
    }

    @Transactional
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
                company.getLogoUrl(),
                company.isVerified(),
                company.getVerificationStatus(),
                company.getStatus(),
                locResponses
        );
    }

    @Transactional(readOnly = true)
    public List<CompanyDocumentResponse> getCompanyDocuments() {
        Employer employer = getCurrentEmployerOrRegisterPlaceholder();
        return companyDocumentRepository.findByCompanyIdOrderByUploadedAtDesc(employer.getCompany().getId())
                .stream()
                .map(dtoMapper::toCompanyDocumentResponse)
                .toList();
    }

    @Transactional
    public CompanyDocumentResponse uploadCompanyDocument(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "Vui lòng chọn file để tải lên");
        }
        Employer employer = getCurrentEmployerOrRegisterPlaceholder();
        if (!employer.isOwner()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Chỉ chủ sở hữu công ty mới có quyền tải lên tài liệu xác thực");
        }
        Company company = employer.getCompany();

        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document.pdf";
        String contentType = file.getContentType() != null ? file.getContentType() : "application/pdf";
        String fileType = contentType.contains("pdf") ? "pdf" : "image";

        String fileUrl;
        String publicId = null;
        try {
            String resourceType = "pdf".equalsIgnoreCase(fileType) ? "raw" : "image";
            Map uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "folder", "sjp/company_docs",
                    "resource_type", resourceType
            ));
            fileUrl = (String) uploadResult.get("secure_url");
            publicId = (String) uploadResult.get("public_id");
        } catch (Exception e) {
            // Fallback cho local development nếu chưa cấu hình Cloudinary API key
            fileUrl = "pdf".equalsIgnoreCase(fileType) ? "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf" : "https://res.cloudinary.com/demo/image/upload/sample.jpg";
            publicId = "local_" + UUID.randomUUID();
        }

        CompanyDocument doc = new CompanyDocument();
        doc.setCompany(company);
        doc.setFileName(fileName);
        doc.setFileUrl(fileUrl);
        doc.setFileType(fileType);
        doc.setPublicId(publicId);
        doc.setStatus("pending");
        doc.setUploadedAt(java.time.LocalDateTime.now());

        doc = companyDocumentRepository.save(doc);

        // Cập nhật trạng thái xác thực công ty thành pending
        markCompanyPendingReviewIfNeeded(company);
        companyRepository.save(company);

        return dtoMapper.toCompanyDocumentResponse(doc);
    }

    @Transactional
    public void deleteCompanyDocument(String id) {
        Employer employer = getCurrentEmployerOrRegisterPlaceholder();
        if (!employer.isOwner()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Chỉ chủ sở hữu công ty mới có quyền xóa tài liệu");
        }
        UUID docId;
        try {
            docId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ID", "ID tài liệu không hợp lệ");
        }
        CompanyDocument doc = companyDocumentRepository.findByIdAndCompanyId(docId, employer.getCompany().getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy tài liệu"));

        if (!"pending".equalsIgnoreCase(doc.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_DELETE_REVIEWED", "Chỉ có thể xóa tài liệu đang chờ duyệt");
        }

        if (doc.getPublicId() != null && !doc.getPublicId().startsWith("local_")) {
            try {
                String resourceType = "pdf".equalsIgnoreCase(doc.getFileType()) ? "raw" : "image";
                cloudinary.uploader().destroy(doc.getPublicId(), ObjectUtils.asMap("resource_type", resourceType));
            } catch (Exception ignored) {}
        }
        companyDocumentRepository.delete(doc);
    }

    @Transactional
    public CompanyProfileResponse uploadCompanyLogo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "Vui lòng chọn file logo để tải lên");
        }
        Employer employer = getCurrentEmployerOrRegisterPlaceholder();
        if (!employer.isOwner()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Chỉ chủ sở hữu công ty mới có quyền cập nhật logo");
        }
        Company company = employer.getCompany();

        String fileUrl;
        try {
            Map uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "folder", "sjp/company_logos",
                    "resource_type", "image"
            ));
            fileUrl = (String) uploadResult.get("secure_url");
        } catch (Exception e) {
            fileUrl = "https://images.unsplash.com/photo-1548092372-0d1bd40894a3?auto=format&fit=crop&w=300&h=300&q=80";
        }

        company.setLogoUrl(fileUrl);
        company = companyRepository.save(company);
        return toCompanyProfileResponse(company);
    }

    @Transactional(readOnly = true)
    public List<JobResponse> getCompanyJobs() {
        Employer employer = getCurrentEmployerOrRegisterPlaceholder();
        return jobRepository.findByCompanyIdOrderByCreatedAtDesc(employer.getCompany().getId())
                .stream()
                .map(job -> dtoMapper.toJobResponse(job, false, false, null))
                .toList();
    }

    @Transactional
    public JobResponse createJob(JobRequest request) {
        Employer employer = getCurrentEmployerOrRegisterPlaceholder();
        Company company = employer.getCompany();
        if (!company.isVerified() && !"verified".equalsIgnoreCase(company.getVerificationStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "COMPANY_NOT_VERIFIED", "Công ty của bạn chưa được Admin xác thực. Chỉ các công ty đã được Admin xác thực mới có quyền đăng tin tuyển dụng.");
        }
        request.setEmployerId(String.valueOf(employer.getId()));
        return jobService.createJobResponse(request);
    }

    @Transactional
    public JobResponse updateJob(String id, JobRequest request) {
        Employer employer = getCurrentEmployerOrRegisterPlaceholder();
        Company company = employer.getCompany();
        if (!company.isVerified() && !"verified".equalsIgnoreCase(company.getVerificationStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "COMPANY_NOT_VERIFIED", "Công ty của bạn chưa được Admin xác thực. Chỉ các công ty đã được Admin xác thực mới có quyền quản lý và đăng tin tuyển dụng.");
        }
        request.setEmployerId(String.valueOf(employer.getId()));
        return jobService.updateJobResponse(id, request);
    }

    @Transactional
    public JobResponse submitJobForReview(String id) {
        Employer employer = getCurrentEmployerOrRegisterPlaceholder();
        return jobService.submitJobForReview(id, employer);
    }

    @Transactional
    public void deleteJob(String id) {
        Employer employer = getCurrentEmployerOrRegisterPlaceholder();
        jobService.deleteJobForEmployer(id, employer);
    }
}
