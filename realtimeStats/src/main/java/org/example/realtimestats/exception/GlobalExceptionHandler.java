package org.example.realtimestats.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessException(BusinessException e) {
        log.error("业务异常：{}", e.getMessage(), e);
        Map<String, Object> error = new HashMap<>();
        error.put("code", e.getCode());
        error.put("message", e.getMessage());
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    /**
     * 处理参数校验异常（@Valid注解抛出）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        log.error("参数校验异常：{}", e.getMessage(), e);
        BindingResult bindingResult = e.getBindingResult();
        Map<String, Object> error = new HashMap<>();
        error.put("code", 400);
        error.put("message", "参数校验失败");
        error.put("errors", bindingResult.getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage, (oldValue, newValue) -> oldValue)));
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    /**
     * 处理参数绑定异常
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Map<String, Object>> handleBindException(BindException e) {
        log.error("参数绑定异常：{}", e.getMessage(), e);
        BindingResult bindingResult = e.getBindingResult();
        Map<String, Object> error = new HashMap<>();
        error.put("code", 400);
        error.put("message", "参数绑定失败");
        error.put("errors", bindingResult.getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage, (oldValue, newValue) -> oldValue)));
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    /**
     * 处理约束违反异常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolationException(ConstraintViolationException e) {
        log.error("约束违反异常：{}", e.getMessage(), e);
        Set<ConstraintViolation<?>> violations = e.getConstraintViolations();
        Map<String, Object> error = new HashMap<>();
        error.put("code", 400);
        error.put("message", "参数校验失败");
        error.put("errors", violations.stream()
                .collect(Collectors.toMap(
                        v -> v.getPropertyPath().toString(),
                        ConstraintViolation::getMessage,
                        (oldValue, newValue) -> oldValue)));
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    /**
     * 处理其他所有异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        log.error("系统异常：{}", e.getMessage(), e);
        Map<String, Object> error = new HashMap<>();
        error.put("code", 500);
        error.put("message", "系统内部错误");
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}