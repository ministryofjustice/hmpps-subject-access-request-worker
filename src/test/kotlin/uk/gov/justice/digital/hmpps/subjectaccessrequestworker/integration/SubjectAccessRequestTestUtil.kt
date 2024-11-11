package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration

import org.assertj.core.api.Assertions.assertThat
import java.math.BigInteger
import java.security.MessageDigest

const val HASH_ALGORITHM = "SHA-512"
const val HEX_RADIX = 16

fun fileHash(bytes: ByteArray): String =
  BigInteger(
    1,
    MessageDigest
      .getInstance(HASH_ALGORITHM)
      .digest(bytes),
  ).toString(HEX_RADIX)

fun assertExpectedErrorMessage(actual: Throwable, prefix: String, vararg params: Pair<String, *>) {
  val formattedParams = params.joinToString(", ") { entry ->
    "${entry.first}=${entry.second}"
  }

  assertThat(actual.message).startsWith(prefix)
  assertThat(actual.message).endsWith(formattedParams)
}
