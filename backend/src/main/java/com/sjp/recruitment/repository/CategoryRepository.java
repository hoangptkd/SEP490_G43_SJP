package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
    List<Category> findByStatusOrderByNameAsc(String status);
}
