package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.DateConversionHelper
import java.time.LocalDate

/**
 * Service to abstract date generation - Functionally redundant but allows control over date values in tests.
 */
@Service
class DateService {

  private val dateConverter: DateConversionHelper = DateConversionHelper()

  fun now(): LocalDate = LocalDate.now()
}
