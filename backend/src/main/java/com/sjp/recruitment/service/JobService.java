package com.sjp.recruitment.service;

import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.request.JobRequest;
import com.sjp.recruitment.model.dto.response.CompanyResponse;
import com.sjp.recruitment.model.dto.response.JobPageResponse;
import com.sjp.recruitment.model.dto.response.JobResponse;
import com.sjp.recruitment.model.dto.response.RecommendationResponse;
import com.sjp.recruitment.model.dto.response.UserResponse;
import com.sjp.recruitment.model.entity.CandidateProfile;
import com.sjp.recruitment.model.entity.Company;
import com.sjp.recruitment.model.entity.Employer;
import com.sjp.recruitment.model.entity.Job;
import com.sjp.recruitment.model.entity.User;
import com.sjp.recruitment.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final SavedJobRepository savedJobRepository;
    private final ApplicationRepository applicationRepository;
    private final EmployerRepository employerRepository;
    private final DtoMapper dtoMapper;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Transactional(readOnly = true)
    public JobPageResponse search(String search, String location, BigDecimal minSalary, BigDecimal maxSalary,
                                  String experienceLevel, String skills, String sort, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", safeSize)
                .addValue("offset", (long) safePage * safeSize);

        String whereClause = buildRemoteJobWhereClause(
                search, location, minSalary, maxSalary, experienceLevel, skills, params);

        String countSql = "SELECT COUNT(*) FROM jobs j JOIN companies c ON c.id = j.company_id\n" + whereClause;
        Long totalElements = namedParameterJdbcTemplate.queryForObject(countSql, params, Long.class);

        String dataSql = """
                SELECT
                    j.id::text AS id,
                    j.title,
                    j.description,
                    j.requirements,
                    j.salary_min,
                    j.salary_max,
                    j.location,
                    j.experience_level,
                    j.deadline,
                    j.status,
                    c.id::text AS company_id,
                    c.name AS company_name,
                    c.website AS company_website,
                    c.location AS company_location,
                    COALESCE(array_remove(array_agg(DISTINCT s.name), NULL), ARRAY[]::text[]) AS skills
                FROM jobs j
                JOIN companies c ON c.id = j.company_id
                LEFT JOIN job_skills js ON js.job_id = j.id
                LEFT JOIN skills s ON s.id = js.skill_id
                """
                + whereClause
                + """
                GROUP BY
                    j.id, j.title, j.description, j.requirements, j.salary_min, j.salary_max,
                    j.location, j.experience_level, j.deadline, j.status,
                    c.id, c.name, c.website, c.location
                """
                + resolveRemoteJobOrder(sort)
                + " LIMIT :limit OFFSET :offset";

        List<JobResponse> content = namedParameterJdbcTemplate.query(dataSql, params, this::mapRemoteJobResponse);
        long total = totalElements == null ? 0 : totalElements;
        int totalPages = total == 0 ? 0 : (int) Math.ceil(total / (double) safeSize);
        return new JobPageResponse(content, safePage, safeSize, total, totalPages);
    }

    @Transactional(readOnly = true)
    public JobResponse findJobResponseById(String id) {
        try {
            UUID.fromString(id);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "JOB_ID_INVALID", "Ma viec lam khong hop le");
        }

        String sql = """
                SELECT
                    j.id::text AS id,
                    j.title,
                    j.description,
                    j.requirements,
                    j.salary_min,
                    j.salary_max,
                    j.location,
                    j.experience_level,
                    j.deadline,
                    j.status,
                    c.id::text AS company_id,
                    c.name AS company_name,
                    c.website AS company_website,
                    c.location AS company_location,
                    COALESCE(array_remove(array_agg(DISTINCT s.name), NULL), ARRAY[]::text[]) AS skills
                FROM jobs j
                JOIN companies c ON c.id = j.company_id
                LEFT JOIN job_skills js ON js.job_id = j.id
                LEFT JOIN skills s ON s.id = js.skill_id
                WHERE j.id = CAST(:id AS uuid)
                  AND j.status = 'published'
                GROUP BY
                    j.id, j.title, j.description, j.requirements, j.salary_min, j.salary_max,
                    j.location, j.experience_level, j.deadline, j.status,
                    c.id, c.name, c.website, c.location
                """;
        List<JobResponse> jobs = namedParameterJdbcTemplate.query(
                sql,
                new MapSqlParameterSource("id", id),
                this::mapRemoteJobResponse
        );
        return jobs.stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND", "Khong tim thay viec lam"));
    }

    @Transactional(readOnly = true)
    public Job findById(String id) {
        return jobRepository.findById(parseUuid(id, "JOB_ID_INVALID"))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND", "Khong tim thay viec lam"));
    }

    @Transactional(readOnly = true)
    public List<RecommendationResponse> recommendations() {
        CandidateProfile candidate = currentCandidate()
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "CANDIDATE_REQUIRED", "Chi ung vien moi co goi y viec lam"));
        boolean lowConfidence = candidate.getSkills() == null || candidate.getSkills().isEmpty();
        return jobRepository.findTop20ByStatusOrderByCreatedAtDesc("published")
                .stream()
                .map(job -> toRecommendation(candidate, job, lowConfidence))
                .sorted(Comparator.comparingInt(RecommendationResponse::matchScore).reversed())
                .limit(10)
                .toList();
    }

    @Transactional
    public Job create(JobRequest request) {
        Employer employer = employerRepository.findById(parseUuid(request.getEmployerId(), "EMPLOYER_ID_INVALID"))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EMPLOYER_NOT_FOUND", "Khong tim thay nha tuyen dung"));
        Company company = companyRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "COMPANY_REQUIRED", "Can co cong ty truoc khi tao viec lam"));
        Job job = new Job();
        job.setTitle(request.getTitle());
        job.setDescription(request.getDescription());
        job.setRequirements(request.getRequirements() == null ? List.of() : request.getRequirements());
        job.setSkills(request.getRequirements() == null ? List.of() : request.getRequirements());
        job.setSalaryMin(request.getSalaryMin());
        job.setSalaryMax(request.getSalaryMax());
        job.setLocation(request.getLocation());
        job.setExperienceLevel("fresher");
        job.setDeadline(LocalDate.now().plusDays(30));
        job.setStatus("published");
        job.setPublishedAt(LocalDateTime.now());
        job.setEmployer(employer);
        job.setCompany(company);
        return jobRepository.save(job);
    }

    @Transactional
    public Job update(String id, JobRequest request) {
        Job job = findById(id);
        job.setTitle(request.getTitle());
        job.setDescription(request.getDescription());
        job.setRequirements(request.getRequirements() == null ? List.of() : request.getRequirements());
        job.setSkills(request.getRequirements() == null ? List.of() : request.getRequirements());
        job.setSalaryMin(request.getSalaryMin());
        job.setSalaryMax(request.getSalaryMax());
        job.setLocation(request.getLocation());
        return jobRepository.save(job);
    }

    @Transactional
    public void delete(String id) {
        jobRepository.deleteById(parseUuid(id, "JOB_ID_INVALID"));
    }

    @Transactional(readOnly = true)
    public List<Job> findByEmployerId(String employerId) {
        return jobRepository.findByEmployerId(parseUuid(employerId, "EMPLOYER_ID_INVALID"), Pageable.unpaged()).getContent();
    }

    public JobResponse toJobResponse(Job job, CandidateProfile candidate) {
        boolean saved = candidate != null && savedJobRepository.existsByCandidateIdAndJobId(candidate.getId(), job.getId());
        boolean applied = candidate != null && applicationRepository.existsByCandidateIdAndJobId(candidate.getId(), job.getId());
        Integer score = candidate == null ? null : calculateMatchScore(candidate, job);
        return dtoMapper.toJobResponse(job, saved, applied, score);
    }

    public int calculateMatchScore(CandidateProfile candidate, Job job) {
        Set<String> candidateSkills = normalized(candidate.getSkills());
        Set<String> jobSkills = normalized(job.getSkills() == null || job.getSkills().isEmpty() ? job.getRequirements() : job.getSkills());
        if (candidateSkills.isEmpty() || jobSkills.isEmpty()) {
            return 20;
        }
        long matches = jobSkills.stream().filter(candidateSkills::contains).count();
        int skillScore = (int) Math.round((matches * 70.0) / jobSkills.size());
        int locationScore = candidate.getLocation() != null && job.getLocation() != null
                && job.getLocation().toLowerCase().contains(candidate.getLocation().toLowerCase()) ? 20 : 0;
        int base = matches > 0 ? 10 : 0;
        return Math.min(100, skillScore + locationScore + base);
    }

    private RecommendationResponse toRecommendation(CandidateProfile candidate, Job job, boolean lowConfidence) {
        Set<String> candidateSkills = normalized(candidate.getSkills());
        List<String> jobSkills = job.getSkills() == null ? List.of() : job.getSkills();
        List<String> matched = jobSkills.stream()
                .filter(skill -> candidateSkills.contains(skill.toLowerCase()))
                .toList();
        List<String> missing = jobSkills.stream()
                .filter(skill -> !candidateSkills.contains(skill.toLowerCase()))
                .limit(5)
                .toList();
        int score = calculateMatchScore(candidate, job);
        String reason = matched.isEmpty()
                ? "Hoan thien ho so ky nang de nhan goi y chinh xac hon."
                : "Phu hop vi ban co " + String.join(", ", matched) + ".";
        return new RecommendationResponse(toJobResponse(job, candidate), score, matched, missing, reason, lowConfidence);
    }

    private Optional<CandidateProfile> currentCandidate() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        User user;
        if (authentication.getPrincipal() instanceof User entityUser) {
            user = entityUser;
        } else if (authentication.getPrincipal() instanceof UserResponse userResponse) {
            user = userRepository.findByEmail(userResponse.email()).orElse(null);
        } else {
            user = null;
        }
        if (user == null || user.getRoleEnum() != User.UserRole.CANDIDATE || !user.isEmailVerified() || user.getStatusEnum() != User.UserStatus.ACTIVE) {
            return Optional.empty();
        }
        return candidateProfileRepository.findByUserId(user.getId());
    }

    private Sort resolveSort(String sort) {
        if ("salary".equalsIgnoreCase(sort)) {
            return Sort.by(Sort.Direction.DESC, "salaryMax");
        }
        if ("deadline".equalsIgnoreCase(sort)) {
            return Sort.by(Sort.Direction.ASC, "deadline");
        }
        return Sort.by(Sort.Direction.DESC, "createdAt");
    }

    private Set<String> parseSkillFilter(String skills) {
        if (skills == null || skills.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(skills.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    private Set<String> normalized(List<String> values) {
        if (values == null) {
            return Set.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(value -> value.trim().toLowerCase())
                .filter(value -> !value.isBlank())
                .collect(Collectors.toSet());
    }

    private String buildRemoteJobWhereClause(String search, String location, BigDecimal minSalary, BigDecimal maxSalary,
                                             String experienceLevel, String skills, MapSqlParameterSource params) {
        StringBuilder where = new StringBuilder("""
                WHERE j.status = 'published'
                  AND (j.deadline IS NULL OR j.deadline >= CURRENT_DATE)
                """);

        if (search != null && !search.isBlank()) {
            where.append("""
                  AND (
                    j.title ILIKE :search
                    OR j.description ILIKE :search
                    OR COALESCE(j.requirements, '') ILIKE :search
                    OR c.name ILIKE :search
                  )
                """);
            params.addValue("search", "%" + search.trim() + "%");
        }

        if (location != null && !location.isBlank()) {
            where.append("""
                  AND (
                    COALESCE(j.location, '') ILIKE :location
                    OR COALESCE(c.location, '') ILIKE :location
                  )
                """);
            params.addValue("location", "%" + location.trim() + "%");
        }

        if (minSalary != null) {
            where.append("  AND (j.salary_max IS NULL OR j.salary_max >= :minSalary)\n");
            params.addValue("minSalary", minSalary);
        }

        if (maxSalary != null) {
            where.append("  AND (j.salary_min IS NULL OR j.salary_min <= :maxSalary)\n");
            params.addValue("maxSalary", maxSalary);
        }

        if (experienceLevel != null && !experienceLevel.isBlank()) {
            where.append("  AND LOWER(COALESCE(j.experience_level, '')) = :experienceLevel\n");
            params.addValue("experienceLevel", experienceLevel.trim().toLowerCase(Locale.ROOT));
        }

        Set<String> skillFilters = parseSkillFilter(skills);
        if (!skillFilters.isEmpty()) {
            where.append("""
                  AND (
                    SELECT COUNT(DISTINCT LOWER(sf.name))
                    FROM job_skills jsf
                    JOIN skills sf ON sf.id = jsf.skill_id
                    WHERE jsf.job_id = j.id
                      AND LOWER(sf.name) IN (:skillFilters)
                  ) = :skillFilterCount
                """);
            params.addValue("skillFilters", skillFilters);
            params.addValue("skillFilterCount", skillFilters.size());
        }

        return where.toString();
    }

    private String resolveRemoteJobOrder(String sort) {
        if ("salary".equalsIgnoreCase(sort)) {
            return " ORDER BY j.salary_max DESC NULLS LAST, COALESCE(j.published_at, j.posted_at, j.created_at) DESC";
        }
        if ("deadline".equalsIgnoreCase(sort)) {
            return " ORDER BY j.deadline ASC NULLS LAST, COALESCE(j.published_at, j.posted_at, j.created_at) DESC";
        }
        return " ORDER BY COALESCE(j.published_at, j.posted_at, j.created_at) DESC";
    }

    private JobResponse mapRemoteJobResponse(ResultSet resultSet, int rowNumber) throws SQLException {
        return new JobResponse(
                resultSet.getString("id"),
                resultSet.getString("title"),
                resultSet.getString("description"),
                textToList(resultSet.getString("requirements")),
                textArrayToList(resultSet.getArray("skills")),
                resultSet.getBigDecimal("salary_min"),
                resultSet.getBigDecimal("salary_max"),
                resultSet.getString("location"),
                toFrontendExperienceLevel(resultSet.getString("experience_level")),
                readDeadline(resultSet),
                toFrontendStatus(resultSet.getString("status")),
                new CompanyResponse(
                        resultSet.getString("company_id"),
                        resultSet.getString("company_name"),
                        resultSet.getString("company_website"),
                        resultSet.getString("company_location")
                ),
                false,
                false,
                null
        );
    }

    private LocalDateTime readDeadline(ResultSet resultSet) throws SQLException {
        java.sql.Date deadline = resultSet.getDate("deadline");
        return deadline == null ? null : deadline.toLocalDate().atStartOfDay();
    }

    private List<String> textToList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("\\R"))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private List<String> textArrayToList(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        Object rawArray = array.getArray();
        if (!(rawArray instanceof Object[] values)) {
            return List.of();
        }
        return Arrays.stream(values)
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private String toFrontendExperienceLevel(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String toFrontendStatus(String status) {
        if (status == null) {
            return "DRAFT";
        }
        return switch (status.trim().toLowerCase(Locale.ROOT)) {
            case "published" -> "ACTIVE";
            case "closed" -> "CLOSED";
            case "expired" -> "EXPIRED";
            default -> "DRAFT";
        };
    }

    private UUID parseUuid(String value, String code) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, code, "Ma dinh danh khong hop le");
        }
    }
}
