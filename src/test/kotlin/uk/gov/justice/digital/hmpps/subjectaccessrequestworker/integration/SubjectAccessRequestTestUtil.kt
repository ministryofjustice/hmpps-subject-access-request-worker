package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration

import org.assertj.core.api.Assertions.assertThat
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode.ErrorCode
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import java.math.BigInteger
import java.security.MessageDigest

const val HASH_ALGORITHM = "SHA-512"
const val HEX_RADIX = 16

fun fileHash(bytes: ByteArray): String = BigInteger(
  1,
  MessageDigest
    .getInstance(HASH_ALGORITHM)
    .digest(bytes),
).toString(HEX_RADIX)

fun <T : Throwable?> assertExpectedSubjectAccessRequestException(
  actual: SubjectAccessRequestException,
  expectedPrefix: String,
  expectedCause: Class<T>,
  expectedEvent: ProcessingEvent? = null,
  expectedErrorCode: ErrorCode,
  expectedSubjectAccessRequest: SubjectAccessRequest? = null,
  expectedParams: Map<String, *>? = null,
) {
  assertThat(actual.cause)
    .withFailMessage("actual.cause was null expected type: ${expectedCause.simpleName}")
    .isNotNull
  assertThat(actual.cause)
    .withFailMessage("actual.cause did not match expected type: expected: ${expectedCause.simpleName}, actual: ${actual.cause!!::class.java.simpleName}")
    .isInstanceOf(expectedCause)

  assertException(
    actual,
    expectedPrefix,
    expectedEvent,
    expectedErrorCode,
    expectedSubjectAccessRequest,
    expectedParams,
  )
}

fun assertExpectedSubjectAccessRequestExceptionWithCauseNull(
  actual: SubjectAccessRequestException,
  expectedPrefix: String,
  expectedEvent: ProcessingEvent? = null,
  expectedErrorCode: ErrorCode,
  expectedSubjectAccessRequest: SubjectAccessRequest? = null,
  expectedParams: Map<String, *>? = null,
) {
  assertThat(actual.cause).isNull()

  assertException(
    actual,
    expectedPrefix,
    expectedEvent,
    expectedErrorCode,
    expectedSubjectAccessRequest,
    expectedParams,
  )
}

private fun assertException(
  actual: SubjectAccessRequestException,
  expectedPrefix: String,
  expectedEvent: ProcessingEvent? = null,
  expectedErrorCode: ErrorCode,
  expectedSubjectAccessRequest: SubjectAccessRequest? = null,
  expectedParams: Map<String, *>? = null,
) {
  assertThat(actual.message)
    .startsWith(expectedPrefix)

  assertThat(actual.event)
    .isEqualTo(expectedEvent)

  assertThat(actual.subjectAccessRequest?.id)
    .isEqualTo(expectedSubjectAccessRequest?.id)

  assertThat(actual.subjectAccessRequest?.sarCaseReferenceNumber)
    .isEqualTo(expectedSubjectAccessRequest?.sarCaseReferenceNumber)

  assertThat(actual.subjectAccessRequest?.contextId)
    .isEqualTo(expectedSubjectAccessRequest?.contextId)

  assertThat(actual.errorCode).isEqualTo(expectedErrorCode)

  when (expectedParams) {
    null -> assertThat(actual.params).isNull()
    else -> assertThat(actual.params)
      .containsAllEntriesOf(expectedParams)
  }
}
