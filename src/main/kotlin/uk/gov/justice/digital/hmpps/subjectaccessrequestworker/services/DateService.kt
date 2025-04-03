package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Service to abstract date generation - Functionally redundant but allows control over date values in tests.
 */
@Service
class DateService {
  private val reportGenerationDateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy")

  fun now(): LocalDate = LocalDate.now()
  fun reportGenerationDate(): String = now().format(reportGenerationDateFormat)
}
