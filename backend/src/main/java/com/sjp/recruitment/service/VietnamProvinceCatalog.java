package com.sjp.recruitment.service;

import com.sjp.recruitment.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class VietnamProvinceCatalog {
    private static final List<String> PROVINCES = List.of(
            "Thành phố Hà Nội", "Tỉnh Cao Bằng", "Tỉnh Tuyên Quang", "Tỉnh Điện Biên",
            "Tỉnh Lai Châu", "Tỉnh Sơn La", "Tỉnh Lào Cai", "Tỉnh Thái Nguyên",
            "Tỉnh Lạng Sơn", "Tỉnh Quảng Ninh", "Tỉnh Bắc Ninh", "Tỉnh Phú Thọ",
            "Thành phố Hải Phòng", "Tỉnh Hưng Yên", "Tỉnh Ninh Bình", "Tỉnh Thanh Hóa",
            "Tỉnh Nghệ An", "Tỉnh Hà Tĩnh", "Tỉnh Quảng Trị", "Thành phố Huế",
            "Thành phố Đà Nẵng", "Tỉnh Quảng Ngãi", "Tỉnh Gia Lai", "Tỉnh Khánh Hòa",
            "Tỉnh Đắk Lắk", "Tỉnh Lâm Đồng", "Tỉnh Đồng Nai", "Thành phố Hồ Chí Minh",
            "Tỉnh Tây Ninh", "Tỉnh Đồng Tháp", "Tỉnh Vĩnh Long", "Tỉnh An Giang",
            "Thành phố Cần Thơ", "Tỉnh Cà Mau"
    );
    private static final Set<String> NORMALIZED = PROVINCES.stream()
            .map(VietnamProvinceCatalog::normalize)
            .collect(Collectors.toUnmodifiableSet());

    public boolean contains(String value) {
        return value != null && NORMALIZED.contains(normalize(value));
    }

    public void requireAllValid(List<String> values) {
        if (values == null || values.stream().anyMatch(value -> !contains(value))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PREFERRED_LOCATION",
                    "Địa điểm làm việc phải được chọn từ danh sách tỉnh/thành phố hợp lệ");
        }
    }

    private static String normalize(String value) {
        String decomposed = Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "").replace('đ', 'd').replaceAll("\\s+", " ");
    }
}
