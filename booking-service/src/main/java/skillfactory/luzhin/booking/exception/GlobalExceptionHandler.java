package skillfactory.luzhin.booking.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.util.stream.Collectors;

/**
 * Преобразует ошибки Booking Service в ответы формата ProblemDetail.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Обрабатывает неверные учётные данные.
     *
     * @param exception ошибка аутентификации
     * @param request HTTP-запрос
     * @return ответ с кодом 401
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleInvalidCredentials(
            InvalidCredentialsException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.UNAUTHORIZED, exception.getMessage(), request);
    }

    /**
     * Обрабатывает отсутствие запрошенного ресурса.
     *
     * @param exception ошибка отсутствующего ресурса
     * @param request HTTP-запрос
     * @return ответ с кодом 404
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(
            ResourceNotFoundException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    /**
     * Обрабатывает конфликт бизнес-правил.
     *
     * @param exception ошибка конфликта
     * @param request HTTP-запрос
     * @return ответ с кодом 409
     */
    @ExceptionHandler(BusinessConflictException.class)
    public ResponseEntity<ProblemDetail> handleConflict(
            BusinessConflictException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.CONFLICT, exception.getMessage(), request);
    }

    /**
     * Обрабатывает ошибки Bean Validation в теле запроса.
     *
     * @param exception ошибка валидации
     * @param request HTTP-запрос
     * @return ответ с кодом 400 и описанием некорректных полей
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        return response(HttpStatus.BAD_REQUEST, detail, request);
    }

    /**
     * Обрабатывает ошибки валидации параметров запроса.
     *
     * @param exception ошибка валидации
     * @param request HTTP-запрос
     * @return ответ с кодом 400
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
    }

    /**
     * Обрабатывает некорректные параметры и нечитаемое тело запроса.
     *
     * @param exception ошибка разбора запроса
     * @param request HTTP-запрос
     * @return ответ с кодом 400
     */
    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ProblemDetail> handleMalformedRequest(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "Request parameters or body are invalid", request);
    }

    /**
     * Обрабатывает нарушение ограничений базы данных.
     *
     * @param exception ошибка целостности данных
     * @param request HTTP-запрос
     * @return ответ с кодом 409
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(
            DataIntegrityViolationException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.CONFLICT, "Request violates a data integrity constraint", request);
    }

    /**
     * Формирует описание ошибки конкретного поля.
     *
     * @param error ошибка поля
     * @return текстовое описание ошибки
     */
    private String formatFieldError(FieldError error) {
        return "%s: %s".formatted(error.getField(), error.getDefaultMessage());
    }

    /**
     * Создаёт ответ ProblemDetail с указанным HTTP-статусом.
     *
     * @param status HTTP-статус
     * @param detail описание ошибки
     * @param request HTTP-запрос
     * @return ответ с данными об ошибке
     */
    private ResponseEntity<ProblemDetail> response(
            HttpStatus status,
            String detail,
            HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(status).body(problem);
    }
}
