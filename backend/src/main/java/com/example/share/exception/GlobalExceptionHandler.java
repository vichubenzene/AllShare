package com.example.share.exception;

import com.example.share.dto.ErrorResponse;
import com.example.share.logging.RequestLogFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(
            ApiException ex,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (ex.getStatus() == HttpStatus.TOO_MANY_REQUESTS) {
            response.setHeader("Retry-After", "60");
        }
        return error(request, ex.getCode(), ex.getStatus(), ex.getMessage(), ex.getShareUrl());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleTooLarge(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return error(request, ErrorCode.FILE_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds the 50 MB limit.", null);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponse> handleMultipart(MultipartException ex, HttpServletRequest request) {
        return error(request, ErrorCode.INVALID_FILE, HttpStatus.BAD_REQUEST, "The upload could not be read.", null);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            TypeMismatchException.class,
            HttpMediaTypeNotSupportedException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex, HttpServletRequest request) {
        return error(request, ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "Invalid request.", null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException ex, HttpServletRequest request) {
        return error(request, ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Not found.", null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return error(request, ErrorCode.VALIDATION_ERROR, HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed.", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("event=unhandled_error type={}", ex.getClass().getSimpleName(), ex);
        return error(request, ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong.", null);
    }

    private static ResponseEntity<ErrorResponse> error(
            HttpServletRequest request,
            ErrorCode code,
            HttpStatusCode status,
            String message,
            String shareUrl
    ) {
        request.setAttribute(RequestLogFilter.ERROR_CODE_ATTRIBUTE, code);
        return ResponseEntity.status(status).body(new ErrorResponse(code, message, shareUrl));
    }
}
