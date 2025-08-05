package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config

import jakarta.validation.ValidationException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.CONFLICT
import org.springframework.http.HttpStatus.FORBIDDEN
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.ResponseEntity
import org.springframework.security.authorization.AuthorizationDeniedException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.resource.NoResourceFoundException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.controller.entity.BacklogRequestAlreadyExistsException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.controller.entity.BacklogRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.BacklogRequestReportService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

@RestControllerAdvice
class SubjectAccessRequestWorkerExceptionHandler {
  @ExceptionHandler(ValidationException::class)
  fun handleValidationException(e: Exception): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(BAD_REQUEST)
    .body(
      ErrorResponse(
        status = BAD_REQUEST,
        userMessage = "Validation failure: ${e.message}",
        developerMessage = e.message,
      ),
    ).also { log.info("Validation exception: {}", e.message) }

  @ExceptionHandler(NoResourceFoundException::class)
  fun handleNoResourceFoundException(e: NoResourceFoundException): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(NOT_FOUND)
    .body(
      ErrorResponse(
        status = NOT_FOUND,
        userMessage = "No resource found failure: ${e.message}",
        developerMessage = e.message,
      ),
    ).also { log.info("No resource found exception: {}", e.message) }

  @ExceptionHandler(Exception::class)
  fun handleException(e: Exception): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(INTERNAL_SERVER_ERROR)
    .body(
      ErrorResponse(
        status = INTERNAL_SERVER_ERROR,
        userMessage = "Unexpected error: ${e.message}",
        developerMessage = e.message,
      ),
    ).also { log.error("Unexpected exception", e) }

  @ExceptionHandler(BacklogRequestException::class)
  fun handleBacklogRequestException(e: BacklogRequestException): ResponseEntity<ErrorResponse> {
    if (e.cause is DataIntegrityViolationException) {
      return ResponseEntity
        .status(BAD_REQUEST)
        .body(
          ErrorResponse(
            status = BAD_REQUEST,
            userMessage = "Backlog Request data integrity violation error: ${e.message}",
            developerMessage = e.message,
          ),
        ).also { log.error("Backlog Request data integrity violation error", e) }
    } else if (e is BacklogRequestAlreadyExistsException) {
      return ResponseEntity
        .status(CONFLICT)
        .body(
          ErrorResponse(
            status = CONFLICT,
            userMessage = "Backlog Request already exists with these values: ${e.message}",
            developerMessage = e.message,
          ),
        ).also { log.error("Backlog Request data integrity violation error", e) }
    }
    return ResponseEntity
      .status(INTERNAL_SERVER_ERROR)
      .body(
        ErrorResponse(
          status = INTERNAL_SERVER_ERROR,
          userMessage = "Unexpected error: ${e.message}",
          developerMessage = e.message,
        ),
      ).also { log.error("Unexpected exception", e) }
  }

  @ExceptionHandler(AuthorizationDeniedException::class)
  fun handleAuthorizationDeniedException(e: Exception): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(FORBIDDEN)
    .body(
      ErrorResponse(
        status = FORBIDDEN,
        userMessage = "Access denied failure: ${e.message}",
        developerMessage = e.message,
      ),
    ).also { log.info("Access denied exception: {}", e.message) }

  @ExceptionHandler(BacklogRequestReportService.BacklogReportException::class)
  fun handleBacklogVersionNotFound(
    e: BacklogRequestReportService.BacklogReportException,
  ): ResponseEntity<ErrorResponse> {
    val status = when (e) {
      is BacklogRequestReportService.BacklogVersionIncompleteException -> BAD_REQUEST
      is BacklogRequestReportService.BacklogVersionNotFoundException -> NOT_FOUND
      else -> INTERNAL_SERVER_ERROR
    }

    return ResponseEntity
      .status(status)
      .body(
        ErrorResponse(
          status = status,
          userMessage = e.message,
          developerMessage = e.message,
        ),
      ).also { log.info("exception: {}, status: {}", e.message, status.value(), e) }
  }

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
