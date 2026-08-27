package com.sjp.recruitment.model.dto.request;

import com.sjp.recruitment.model.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class RegisterRequest {
    public static class IndustryDto {
        private String categoryId;
        private String categoryName;
        public String getCategoryId() { return categoryId; }
        public void setCategoryId(String categoryId) { this.categoryId = categoryId; }
        public String getCategoryName() { return categoryName; }
        public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    }
    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String password;

    @NotNull
    private User.UserRole role;

    private String fullName;

    private String phone;

    private String gender;

    private String industry; // Keep as fallback/primary

    private List<IndustryDto> industries;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public User.UserRole getRole() { return role; }
    public void setRole(User.UserRole role) { this.role = role; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }
    public List<IndustryDto> getIndustries() { return industries; }
    public void setIndustries(List<IndustryDto> industries) { this.industries = industries; }
}
