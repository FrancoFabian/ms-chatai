package mx.mrw.chattodolist.exception;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import jakarta.servlet.http.HttpServletRequest;
import mx.mrw.chattodolist.api.dto.ErrorResponse;
import mx.mrw.chattodolist.support.RequestContext;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException exception, HttpServletRequest request) {
        String message = "AI_NOT_CONFIGURED".equals(exception.getErrorCode()) ? null : exception.getMessage();
        ErrorResponse body = new ErrorResponse(
                requestId(request),
                exception.getErrorCode(),
                message,
                null);
        return ResponseEntity.status(exception.getStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        Map<String, String> fieldErrors = exception.getBindingResult()
            .getFieldErrors()
            .stream()
            .collect(Collectors.toMap(
                    FieldError::getField,
                    fieldError -> fieldError.getDefaultMessage() == null ? "Invalid value" : fieldError.getDefaultMessage(),
                    (first, second) -> first));

        ErrorResponse body = new ErrorResponse(
                requestId(request),
                "INVALID_REQUEST",
                "Request validation failed",
                fieldErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleInvalidJson(HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                requestId(request),
                "INVALID_JSON",
                "Invalid JSON payload",
                null);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException exception,
            HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                requestId(request),
                "MEDIA_TOO_LARGE",
                "File exceeds upload limit",
                null);
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnhandledException(Exception exception, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                requestId(request),
                "INTERNAL_ERROR",
                "Unexpected server error",
                null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestContext.REQUEST_ID);
        return requestId == null ? null : requestId.toString();
    }
}
