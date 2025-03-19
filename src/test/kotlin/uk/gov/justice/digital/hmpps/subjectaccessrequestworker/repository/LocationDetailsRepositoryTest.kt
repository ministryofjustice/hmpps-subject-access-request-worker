package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.LocationDetail

private const val DPS_ID = "00000be5-081c-4374-8214-18af310d3d4a"
private const val NOMIS_ID = 389406

@DataJpaTest
class LocationDetailsRepositoryTest @Autowired constructor(
  val locationDetailsRepository: LocationDetailsRepository,
) {

  @Test
  fun `findByDpsId returns location details for valid dps id`() {
    val locationDetails = LocationDetail(dpsId = DPS_ID, nomisId = NOMIS_ID, name = "PROPERTY BOX 27")
    locationDetailsRepository.save(locationDetails)

    val foundLocationDetails = locationDetailsRepository.findByDpsId(DPS_ID)
    assertThat(foundLocationDetails).isEqualTo(locationDetails)
  }

  @Test
  fun `findByDpsId returns null for invalid dps id`() {
    val foundLocationDetails = locationDetailsRepository.findByDpsId("INVALID_ID")
    assertThat(foundLocationDetails).isNull()
  }

  @Test
  fun `findByNomisId returns location details for valid nomis id`() {
    val locationDetails = LocationDetail(dpsId = DPS_ID, nomisId = NOMIS_ID, name = "PROPERTY BOX 27")
    locationDetailsRepository.save(locationDetails)

    val foundLocationDetails = locationDetailsRepository.findByNomisId(NOMIS_ID)
    assertThat(foundLocationDetails).isEqualTo(locationDetails)
  }

  @Test
  fun `findByNomisId returns null for invalid nomis id`() {
    val foundLocationDetails = locationDetailsRepository.findByNomisId(4354)
    assertThat(foundLocationDetails).isNull()
  }
}
