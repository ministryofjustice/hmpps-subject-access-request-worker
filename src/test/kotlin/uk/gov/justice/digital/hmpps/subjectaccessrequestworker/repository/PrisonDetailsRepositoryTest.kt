package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.PrisonDetail

@DataJpaTest
class PrisonDetailsRepositoryTest @Autowired constructor(
  val prisonDetailsRepository: PrisonDetailsRepository,
) {

  @Test
  fun `findByPrisonId returns prison detail for valid prison id`() {
    val prisonDetail = PrisonDetail(prisonId = "PDI", prisonName = "Portland (HMP & YOI)")
    prisonDetailsRepository.save(prisonDetail)

    val foundPrisonDetail = prisonDetailsRepository.findByPrisonId("PDI")
    assertThat(foundPrisonDetail).isNotNull
    assertThat(foundPrisonDetail?.prisonName).isEqualTo("Portland (HMP & YOI)")
  }

  @Test
  fun `findByPrisonId returns null for invalid prison id`() {
    val foundPrisonDetail = prisonDetailsRepository.findByPrisonId("INVALID_ID")
    assertThat(foundPrisonDetail).isNull()
  }
}
