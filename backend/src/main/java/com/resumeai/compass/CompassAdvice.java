package com.resumeai.compass;

import java.util.Map;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@Order(-10)
@RestControllerAdvice(basePackages = "com.resumeai.compass")
public class CompassAdvice {
    @ExceptionHandler(CompassException.class)
    ResponseEntity<?> expected(CompassException e) { return error(e.status, e.code, e.getMessage()); }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<?> malformed(Exception e) { return error(400,"INVALID_INPUT","参数或请求格式无效"); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> constraint(Exception e) { return error(409,"CONFLICT","资料被引用或发生并发修改，请刷新后重试"); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unexpected(Exception e) { return error(500,"OPERATION_FAILED","操作未完成，请稍后重试或检查服务状态"); }
    private ResponseEntity<?> error(int status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("code",code,"message",message,"requestId", UUID.randomUUID().toString()));
    }
}
