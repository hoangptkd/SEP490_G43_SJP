package com.sjp.recruitment.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "companies")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String website;
    private String industry;
    
    // Cột location giữ nguyên với ý nghĩa là Head Office
    private String location;

    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CompanyLocation> locations = new ArrayList<>();

    @Column(name = "company_size")
    private Integer companySize;

    @Column(name = "tax_code")
    private String taxCode;

    @Column(name = "verification_status", nullable = false)
    private String verificationStatus = "unverified";

    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CompanyDocument> documents = new ArrayList<>();

    @Column(nullable = false)
    private String status = "pending";

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public boolean isVerified() {
        return "verified".equalsIgnoreCase(verificationStatus);
    }

    public void setVerified(boolean verified) {
        this.verificationStatus = verified ? "verified" : "unverified";
    }
}
