package com.sjp.recruitment.controller;

import com.sjp.recruitment.model.dto.request.AdminCategoryRequest;
import com.sjp.recruitment.model.dto.response.AdminCategoryResponse;
import com.sjp.recruitment.service.AdminOpsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminCategoryControllerTest {

    @Mock private AdminOpsService adminOpsService;
    @InjectMocks private AdminCategoryController controller;

    @Test
    void list_delegatesToService() {
        List<AdminCategoryResponse> expected = List.of(mock(AdminCategoryResponse.class));
        when(adminOpsService.listCategories("all")).thenReturn(expected);
        assertSame(expected, controller.list("all").getBody());
    }

    @Test
    void create_delegatesToService() {
        AdminCategoryRequest request = mock(AdminCategoryRequest.class);
        AdminCategoryResponse expected = mock(AdminCategoryResponse.class);
        when(adminOpsService.createCategory(request)).thenReturn(expected);
        assertSame(expected, controller.create(request).getBody());
    }

    @Test
    void update_delegatesToService() {
        AdminCategoryRequest request = mock(AdminCategoryRequest.class);
        AdminCategoryResponse expected = mock(AdminCategoryResponse.class);
        when(adminOpsService.updateCategory("cat-1", request)).thenReturn(expected);
        assertSame(expected, controller.update("cat-1", request).getBody());
    }
}
