package com.sjp.recruitment.controller;

import com.sjp.recruitment.model.dto.response.CategoryResponse;
import com.sjp.recruitment.model.entity.Category;
import com.sjp.recruitment.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryRepository categoryRepository;

    @GetMapping
    @Transactional
    public ResponseEntity<List<CategoryResponse>> getAllCategories() {
        List<Category> categories = categoryRepository.findByStatusOrderByNameAsc("active");
        if (categories.isEmpty() && categoryRepository.count() == 0) {
            categories = seedDefaultCategories();
        }
        List<CategoryResponse> responses = categories.stream()
                .map(c -> new CategoryResponse(
                        c.getId(),
                        c.getName(),
                        c.getSlug(),
                        c.getParentId(),
                        c.getDescription(),
                        c.getStatus()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    private List<Category> seedDefaultCategories() {
        Category it = saveCat("💻 Công nghệ thông tin - IT", "it-phan-mem", null, "Ngành công nghệ phần mềm và hệ thống");
        saveCat("Lập trình Web (Frontend/Backend)", "lap-trinh-web", it.getId(), "Lập trình website và ứng dụng web");
        saveCat("Lập trình Mobile App", "mobile-app", it.getId(), "Phát triển ứng dụng iOS và Android");
        saveCat("System Admin / DevOps / Cloud", "devops-cloud", it.getId(), "Quản trị hệ thống và hạ tầng đám mây");
        saveCat("AI / Machine Learning / Data", "ai-data-science", it.getId(), "Kỹ sư trí tuệ nhân tạo và xử lý dữ liệu");

        Category marketing = saveCat("📈 Marketing / Truyền thông / Quảng cáo", "marketing-truyen-thong", null, "Ngành marketing và truyền thông thương hiệu");
        saveCat("Digital Marketing / SEO / Ads", "digital-marketing-seo", marketing.getId(), "Quản lý chiến dịch quảng cáo kỹ thuật số");
        saveCat("Content Creator / Copywriter", "content-creator", marketing.getId(), "Sáng tạo nội dung truyền thông");
        saveCat("Brand Manager / PR", "brand-manager-pr", marketing.getId(), "Quản lý thương hiệu và quan hệ công chúng");

        Category finance = saveCat("💰 Tài chính / Kế toán / Ngân hàng", "tai-chinh-ngan-hang", null, "Ngành tài chính, kế toán và ngân hàng");
        saveCat("Kế toán / Kiểm toán viên", "ke-toan-kiem-toan", finance.getId(), "Kế toán tổng hợp, kiểm toán độc lập");
        saveCat("Chuyên viên Phân tích Tài chính (FP&A)", "phan-tich-tai-chinh", finance.getId(), "Phân tích và hoạch định tài chính doanh nghiệp");

        Category sales = saveCat("🤝 Kinh doanh / Bán hàng / CSKH", "kinh-doanh-ban-hang", null, "Ngành kinh doanh và chăm sóc khách hàng");
        saveCat("Chuyên viên Phát triển Kinh doanh (B2B/B2C)", "phat-trien-kinh-doanh", sales.getId(), "Tìm kiếm và mở rộng tệp khách hàng");
        saveCat("Chuyên viên Tư vấn & Chăm sóc Khách hàng", "cs-customer-support", sales.getId(), "Hỗ trợ và chăm sóc khách hàng dịch vụ");

        Category hr = saveCat("👥 Nhân sự / Hành chính / Pháp lý", "nhan-su-hanh-chinh", null, "Ngành quản trị nhân sự và hành chính văn phòng");
        saveCat("Chuyên viên Tuyển dụng (TA / Recruiter)", "chuyen-vien-tuyen-dung", hr.getId(), "Tìm kiếm và thu hút nhân tài cho doanh nghiệp");
        saveCat("Chuyên viên Lương thưởng & Phúc lợi (C&B)", "luong-thuong-phuc-loi", hr.getId(), "Tính lương, bảo hiểm và chế độ đãi ngộ");

        return categoryRepository.findByStatusOrderByNameAsc("active");
    }

    private Category saveCat(String name, String slug, UUID parentId, String description) {
        Category cat = new Category();
        cat.setName(name);
        cat.setSlug(slug);
        cat.setParentId(parentId);
        cat.setDescription(description);
        cat.setStatus("active");
        return categoryRepository.save(cat);
    }
}
