package com.esign.payment.config;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Application-level exception that carries an HTTP status code.
 * <p>
 * Replaces raw {@code RuntimeException} throws throughout the services so that
 * {@link GlobalExceptionHandler} can return a meaningful HTTP status instead of
 * a blanket 500 Internal Server Error.
 */
@Getter
public class ServiceException extends RuntimeException {

    private final HttpStatus status;

    public ServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public ServiceException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }
}

