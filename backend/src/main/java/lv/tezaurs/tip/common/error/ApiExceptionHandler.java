package lv.tezaurs.tip.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(SenseModelException.class)
    ResponseEntity<ApiError> modelUnavailable(SenseModelException exception,
            HttpServletRequest request) {
        return error(HttpStatus.BAD_GATEWAY, "MODEL_UNAVAILABLE", exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ApiError> notFound(NotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ApiError> conflict(ConflictException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "CONFLICT", exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiError> concurrentUpdate(HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "CONFLICT",
                "Dati pa šo laiku ir mainījušies. Pārlādējiet lapu un mēģiniet vēlreiz.", request, List.of());
    }

    @ExceptionHandler(CorpusUnavailableException.class)
    ResponseEntity<ApiError> corpusUnavailable(CorpusUnavailableException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_GATEWAY, "CORPUS_UNAVAILABLE", exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<ApiError.FieldViolation> fields = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError.FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Pārbaudiet ievadītos datus.", request, fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Pieprasījuma dati nav derīgi.", request, List.of());
    }

    @ExceptionHandler({HandlerMethodValidationException.class, ConstraintViolationException.class,
            ServletRequestBindingException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> invalidParameters(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "Pārbaudiet pieprasījuma parametrus un obligātās galvenes.", request, List.of());
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message,
                                            HttpServletRequest request, List<ApiError.FieldViolation> fields) {
        return ResponseEntity.status(status).body(new ApiError(
                Instant.now(), status.value(), code, message, request.getRequestURI(), fields));
    }
}
