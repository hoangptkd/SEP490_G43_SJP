package com.sjp.recruitment.service;

import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.response.PageResponse;
import com.sjp.recruitment.model.dto.response.PublicCompanyResponse;
import com.sjp.recruitment.model.entity.Company;
import com.sjp.recruitment.model.entity.Job;
import com.sjp.recruitment.repository.CompanyLocationRepository;
import com.sjp.recruitment.repository.CompanyRepository;
import com.sjp.recruitment.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PublicCompanyService {
    private final CompanyRepository companyRepository;
    private final CompanyLocationRepository companyLocationRepository;
    private final JobRepository jobRepository;
    private final DtoMapper dtoMapper;

    @Transactional(readOnly = true)
    public PublicCompanyResponse getCompany(String id, int page, int size) {
        UUID companyId;
        try {
            companyId = UUID.fromString(id);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "COMPANY_ID_INVALID", "Mã công ty không hợp lệ");
        }
        Company company = companyRepository.findById(companyId)
                .filter(item -> !"deleted".equalsIgnoreCase(item.getStatus()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COMPANY_NOT_FOUND", "Không tìm thấy công ty"));
        Page<Job> jobs = jobRepository.findPublicJobsByCompanyId(
                companyId,
                LocalDate.now(),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50), Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return new PublicCompanyResponse(
                company.getId().toString(),
                company.getName(),
                company.getDescription(),
                company.getWebsite(),
                company.getIndustry(),
                company.getLocation(),
                company.getCompanySize(),
                company.getLogoUrl(),
                company.isVerified(),
                companyLocationRepository.findByCompanyIdOrderByHeadquarterDescCreatedAtDesc(companyId)
                        .stream().map(dtoMapper::toCompanyLocationResponse).toList(),
                PageResponse.from(jobs, job -> dtoMapper.toJobResponse(job, false, false, null))
        );
    }
}
