package com.px.base.config;

import com.px.base.dto.ResponseDTO;
import com.px.base.security.ForbiddenException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ResponseDTO<Void> forbidden(ForbiddenException e) {
        return ResponseDTO.error(403, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseDTO<Void> badRequest(IllegalArgumentException e) {
        return ResponseDTO.error(400, e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ResponseDTO<Void> conflict(IllegalStateException e) {
        return ResponseDTO.error(409, e.getMessage());
    }
}
