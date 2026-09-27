package com.example.share.exception;

import com.example.share.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import jakarta.servlet.http.HttpServletResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex, HttpServletResponse response) {
        if (ex.getStatus() == HttpStatus.TOO_MANY_REQUESTS) {
            response.setHeader("Retry-After", "60");
        }
        return ResponseEntity.status(ex.getStatus()).body(new ErrorResponse(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleTooLarge(MaxUploadSizeExceededException ex) {
        return error(ErrorCode.FILE_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds the 50 MB limit.");
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponse> handleMultipart(MultipartException ex) {
        return error(ErrorCode.INVALID_FILE, HttpStatus.BAD_REQUEST, "The upload could not be read.");
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex) {
        return error(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "Invalid request.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("event=unhandled_error type={}", ex.getClass().getSimpleName());
        return error(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong.");
    }

    private static ResponseEntity<ErrorResponse> error(ErrorCode code, HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message));
    }
}
