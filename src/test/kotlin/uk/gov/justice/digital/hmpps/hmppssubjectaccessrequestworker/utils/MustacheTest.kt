package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.utils

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.integration.IntegrationTestBase

class MustacheTest : IntegrationTestBase() {

  @Test
  fun `Mustache compiles and returns 0`() {
    val response = Mustache().compile("template.mustache")
    Assertions.assertThat(response).isEqualTo(0)
    assertThrows<Exception> { Mustache().compile("notemplate.mustache") }
  }

  @Test
  fun `Template executes`() {
    val testItem = Item("Item 1", "02/02/02", "This is the first item.")
    val response = Mustache().execute(testItem)
    Assertions.assertThat(response).isEqualTo(0)
  }

  @Test
  fun `keyworker executes`() {
    val dummyData = """{"offenderKeyworkerId":23264,"offenderNo":"A8610DY","staffId":485588,"assignedDateTime":"2022-07-06T14:42:50.621973","active":true,"allocationReason":"AUTO","allocationType":"A","userId":"TWRIGHT","prisonId":"MDI","expiryDateTime":null,"deallocationReason":null,"creationDateTime":"2022-07-06T14:42:50.625039","createUserId":"TWRIGHT","modifyDateTime":"2022-07-06T14:42:50.625039","modifyUserId":"TWRIGHT"}"""
    val mapper = jacksonObjectMapper()
    val keyworker = mapper.readValue(dummyData, Keyworker::class.java)
    println(keyworker.offenderKeyworkerId)
    val response = Mustache().execute(keyworker)
    Assertions.assertThat(response).isEqualTo(0)
  }
}