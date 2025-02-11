package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models

import org.apache.commons.lang3.builder.EqualsBuilder
import org.apache.commons.lang3.builder.HashCodeBuilder

class DpsService(
  val url: String? = null,
  val name: String? = null,
  var orderPosition: Int? = null,
  var businessName: String? = null,
  var content: Any? = null,
) {
  override fun equals(other: Any?): Boolean = EqualsBuilder.reflectionEquals(this, other)

  override fun hashCode(): Int = HashCodeBuilder.reflectionHashCode(this)
}
