package com.quickdeliver.config;

import com.quickdeliver.common.ApiException;
import com.quickdeliver.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;

/** Gives every error the same {"success":false,"message":...} shape the frontend expects. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse> handleApi(ApiException e) {
        return json(e.getStatus(), e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse> handleBadBody(HttpMessageNotReadableException e) {
        return json(400, "Invalid or missing request body");
    }

    /** Port of the Express 404 fallback: unknown /api/* -> JSON 404, anything else -> redirect home. */
    @ExceptionHandler({NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class})
    public ResponseEntity<?> handleNotFound(HttpServletRequest request) {
        if (request.getRequestURI().startsWith("/api/")) {
            return json(404, "API route not found");
        }
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create("/")).build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse> handleAny(Exception e) {
        if (e instanceof ErrorResponse er) { // Spring's own 4xx errors (missing param, bad media type, ...)
            int status = er.getStatusCode().value();
            String detail = er.getBody().getDetail();
            return json(status, detail != null ? detail : "Request failed");
        }
        log.error("Unhandled error", e);
        return json(500, "Internal server error");
    }

    private static ResponseEntity<ApiResponse> json(int status, String message) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.fail(message));
    }
}
