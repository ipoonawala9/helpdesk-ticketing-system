package com.ibrahim.helpdesk.exception;

import com.ibrahim.helpdesk.security.auth.InvalidCredentialsException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(OrganizationNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleOrganizationNotFound(
            OrganizationNotFoundException ex, HttpServletRequest request) {

        return notFound(ex.getMessage(), request);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleUserNotFound(
            UserNotFoundException ex, HttpServletRequest request) {

        return notFound(ex.getMessage(), request);
    }

    @ExceptionHandler(TicketNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleTicketNotFound(
            TicketNotFoundException ex, HttpServletRequest request) {

        return notFound(ex.getMessage(), request);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessRule(
            BusinessRuleException ex, HttpServletRequest request) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(400, "Bad Request", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<ApiErrorResponse> handleForbiddenOperation(
            ForbiddenOperationException ex, HttpServletRequest request) {

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiErrorResponse.of(403, "Forbidden", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(InvalidTicketStateException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidTicketState(
            InvalidTicketStateException ex, HttpServletRequest request) {

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(409, "Conflict", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(EmailAlreadyInUseException.class)
    public ResponseEntity<ApiErrorResponse> handleEmailAlreadyInUse(
            EmailAlreadyInUseException ex, HttpServletRequest request) {

        return conflict(ex.getMessage(), request);
    }

    /**
     * A database constraint rejected a write, for example two users created
     * with the same email at the same moment. Constraint details are not
     * echoed back.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest request) {

        log.warn("Constraint violation for {} {}: {}", request.getMethod(), request.getRequestURI(),
                ex.getMostSpecificCause().getMessage());
        return conflict("The request conflicts with existing data", request);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException ex, HttpServletRequest request) {

        return unauthorized(ex.getMessage(), "Bearer", request);
    }

    /**
     * No valid access token: either none was sent, or it is malformed, expired,
     * signed with the wrong key, or belongs to a user who no longer exists or
     * is inactive.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(
            AuthenticationException ex, HttpServletRequest request) {

        if (ex instanceof OAuth2AuthenticationException) {
            return unauthorized("Invalid or expired access token", "Bearer error=\"invalid_token\"", request);
        }
        return unauthorized("Authentication is required", "Bearer", request);
    }

    /** Authenticated, but the user's role does not allow this endpoint. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiErrorResponse.of(403, "Forbidden",
                        "You do not have permission to perform this action", request.getRequestURI()));
    }

    /**
     * Bean Validation failures on an @Valid request body. Every rejected field
     * is reported at once rather than one per round trip.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors().forEach(error ->
                fieldErrors.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));

        return ResponseEntity
                .badRequest()
                .body(ApiErrorResponse.validation(
                        "Validation failed", request.getRequestURI(), fieldErrors));
    }

    /**
     * Unparseable body, including an unknown enum constant such as an invalid
     * ticket category. The underlying Jackson message is not echoed back
     * because it exposes internal type names.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        return ResponseEntity
                .badRequest()
                .body(ApiErrorResponse.of(400, "Bad Request",
                        "Malformed or unreadable request body", request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        return ResponseEntity
                .badRequest()
                .body(ApiErrorResponse.of(400, "Bad Request",
                        "Invalid value for parameter '" + ex.getName() + "'", request.getRequestURI()));
    }

    /**
     * Spring reports an unmapped URL as a missing static resource; for an API
     * the useful message is which endpoint does not exist.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoEndpoint(
            NoResourceFoundException ex, HttpServletRequest request) {

        return notFound("No endpoint " + request.getMethod() + " " + request.getRequestURI(), request);
    }

    /**
     * Last resort. Errors raised by Spring MVC itself, such as an unknown URL,
     * an unsupported method or content type, or a missing query parameter,
     * carry their own HTTP status and keep it. Anything else is a genuine
     * server error: it is logged in full but reported to the client without
     * detail.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception ex, HttpServletRequest request) {

        if (ex instanceof ErrorResponse frameworkError) {
            HttpStatusCode code = frameworkError.getStatusCode();
            HttpStatus status = HttpStatus.resolve(code.value());
            String error = status != null ? status.getReasonPhrase() : String.valueOf(code.value());
            String detail = frameworkError.getBody().getDetail();

            return ResponseEntity
                    .status(code)
                    .body(ApiErrorResponse.of(code.value(), error,
                            detail != null ? detail : error, request.getRequestURI()));
        }

        log.error("Unhandled exception for {} {}", request.getMethod(), request.getRequestURI(), ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of(500, "Internal Server Error",
                        "An unexpected error occurred", request.getRequestURI()));
    }

    private ResponseEntity<ApiErrorResponse> conflict(String message, HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(409, "Conflict", message, request.getRequestURI()));
    }

    private ResponseEntity<ApiErrorResponse> unauthorized(
            String message, String wwwAuthenticate, HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, wwwAuthenticate)
                .body(ApiErrorResponse.of(401, "Unauthorized", message, request.getRequestURI()));
    }

    private ResponseEntity<ApiErrorResponse> notFound(String message, HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(404, "Not Found", message, request.getRequestURI()));
    }
}
