package ae.sharjah.srta.gateway.web;

import ae.sharjah.srta.gateway.exception.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates flow failures into the same shapes the OSB pipeline produced:
 * a 401 body for unauthorized callers and a 500 "Error Call Service OSB"
 * fault for anything else.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorized(UnauthorizedException ex) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("statusCode", 401);
        result.put("errorMessage", "401 UNAUTHORIZED");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("result", result);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unhandled error while proxying SRTA request", ex);
        Map<String, Object> fault = new LinkedHashMap<>();
        fault.put("errorCode", 500);
        fault.put("reason", "Error Call Service OSB");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(fault);
    }
}
