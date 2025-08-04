package com.aimelive.urutibot.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ControllerAdvice
public class HttpExceptionAdvice {

    @ExceptionHandler(HttpException.class)
    public ResponseEntity<Map<String,String>> exceptionHandler(HttpException exception){

        Map<String,String> errorMap = new HashMap<>();
        errorMap.put("error", exception.getError().name());
        errorMap.put("message", exception.getMessage());

        return new ResponseEntity<>(errorMap, exception.getError());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> maxUploadSizeExceeded(MaxUploadSizeExceededException exception) {
        Map<String,String> errorMap = new HashMap<>();
        errorMap.put("error", exception.getLocalizedMessage());
        errorMap.put("message", exception.getMessage());
        return ResponseEntity.badRequest().body(errorMap);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Map<String, String>> handleMissingServletRequestPartException(MissingServletRequestPartException exception) {
        Map<String, String> errorMap = new HashMap<>();
        errorMap.put("error", "Missing part");
        errorMap.put("message", exception.getMessage());
        return ResponseEntity.badRequest().body(errorMap);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> notValid(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> errors = new ArrayList<>();

        ex.getAllErrors().forEach(err -> errors.add(err.getDefaultMessage()));

        Map<String, Object> result = new HashMap<>();
        result.put("error", "VALIDATION ERROR");
        result.put("message", errors);

        return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
    }
}
