package com.sjp.recruitment.controller;

import com.sjp.recruitment.model.dto.response.PublicCompanyResponse;
import com.sjp.recruitment.service.PublicCompanyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PublicCompanyControllerTest {

    @Mock private PublicCompanyService publicCompanyService;
    @InjectMocks private PublicCompanyController controller;

    @Test
    void getCompany_delegatesWithPagination() {
        PublicCompanyResponse expected = mock(PublicCompanyResponse.class);
        when(publicCompanyService.getCompany("c-1", 0, 10)).thenReturn(expected);
        assertSame(expected, controller.getCompany("c-1", 0, 10).getBody());
    }
}
