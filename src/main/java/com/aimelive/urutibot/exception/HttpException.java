package com.aimelive.urutibot.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class HttpException extends RuntimeException {
    private HttpStatus error = HttpStatus.INTERNAL_SERVER_ERROR;

    public HttpException(HttpStatus error, String message) {
        super(message);
        this.error = error;
    }
    public HttpException(String message) {
        super(message);
    }
}
