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

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    public ResponseEntity<ApiError> handleConcurrentWrite(Exception exception) {
        log.warn("Concurrent or conflicting database update", exception);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(
                        "Dữ liệu đã thay đổi hoặc bị trùng. Vui lòng tải lại và thử lại.",
                        "CONCURRENT_UPDATE",
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
