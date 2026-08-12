package com.sjp.recruitment.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException exception) {
        String upgradeHint = "PLAN_LIMIT_REACHED".equals(exception.getCode())
                ? "Xem các gói dịch vụ để tăng hạn mức"
                : null;
        ResponseEntity.BodyBuilder response = ResponseEntity.status(exception.getStatus());
        if (exception.getRetryAfterSeconds() != null) {
            response.header(HttpHeaders.RETRY_AFTER, String.valueOf(exception.getRetryAfterSeconds()));
        }
        return response
                .body(new ApiError(
                        exception.getMessage(),
                        exception.getCode(),
                        Instant.now().toString(),
                        upgradeHint,
                        exception.getRetryAfterSeconds()
                ));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiError> handleValidation(Exception exception) {
        return ResponseEntity.badRequest()
                .body(new ApiError("Du lieu khong hop le", "VALIDATION_ERROR", Instant.now().toString(), null, null));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUpload(MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("File tải lên vượt quá dung lượng cho phép", "FILE_TOO_LARGE", Instant.now().toString(), null, null));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(ObjectOptimisticLockingFailureException exception) {
        log.warn("Optimistic locking conflict", exception);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(
                        "Dữ liệu đã thay đổi. Vui lòng tải lại và thử lại.",
                        "CONCURRENT_UPDATE",
                        Instant.now().toString(),
                        null,
                        null
                ));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException exception) {
        log.warn("Data integrity violation", exception);
        String raw = exception.getMostSpecificCause() != null
                ? exception.getMostSpecificCause().getMessage()
                : exception.getMessage();
        String detail = raw == null ? "" : raw.toLowerCase();
        if (detail.contains("plans_name_key") || detail.contains("plans_name_target_role_key")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiError(
                            "Đã tồn tại gói cùng tên cho đối tượng này. Vui lòng chọn tên khác hoặc đối tượng khác.",
                            "PLAN_EXISTS",
                            Instant.now().toString(),
                            null,
                            null
                    ));
        }
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(
                        "Dữ liệu bị trùng hoặc không hợp lệ. Vui lòng kiểm tra lại.",
                        "DATA_CONFLICT",
                        Instant.now().toString(),
                        null,
                        null
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception) {
        log.error("Unexpected API error", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError(
                        "Hệ thống đang gặp sự cố. Vui lòng thử lại sau.",
                        "INTERNAL_ERROR",
                        Instant.now().toString(),
                        null,
                        null
                ));
    }

    public record ApiError(String message, String code, String timestamp, String upgradeHint, Long retryAfterSeconds) {
    }
}
