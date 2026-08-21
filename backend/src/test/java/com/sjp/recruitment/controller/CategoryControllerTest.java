package com.sjp.recruitment.controller;

import com.sjp.recruitment.model.entity.Category;
import com.sjp.recruitment.repository.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryControllerTest {

    @Mock private CategoryRepository categoryRepository;
    @InjectMocks private CategoryController controller;

    @Test
    void getAllCategories_returnsMappedResponses() {
        Category cat = new Category();
        cat.setId(UUID.randomUUID());
        cat.setName("IT");
        cat.setSlug("it");
        cat.setDescription("Information Technology");
        cat.setStatus("active");
        when(categoryRepository.findByStatusOrderByNameAsc("active")).thenReturn(List.of(cat));

        ResponseEntity<?> response = controller.getAllCategories();

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void getAllCategories_seedsDefaultsWhenEmpty() {
        when(categoryRepository.findByStatusOrderByNameAsc("active"))
                .thenReturn(List.of())
                .thenReturn(List.of(new Category()));
        when(categoryRepository.count()).thenReturn(0L);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> {
            Category c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        controller.getAllCategories();

        verify(categoryRepository, atLeast(5)).save(any(Category.class));
    }
}
