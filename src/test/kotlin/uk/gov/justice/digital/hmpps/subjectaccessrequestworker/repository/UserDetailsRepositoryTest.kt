package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.UserDetail

@DataJpaTest
class UserDetailsRepositoryTest @Autowired constructor(
  val userDetailsRepository: UserDetailsRepository,
) {

  @Test
  fun `findByUsername returns user detail for valid username`() {
    val userDetail = UserDetail(username = "AZ123PO", lastName = "Smith")
    userDetailsRepository.save(userDetail)

    val foundUserDetail = userDetailsRepository.findByUsername("AZ123PO")
    assertThat(foundUserDetail).isNotNull
    assertThat(foundUserDetail?.lastName).isEqualTo("Smith")
  }

  @Test
  fun `findByUsername returns null for invalid username`() {
    val foundUserDetail = userDetailsRepository.findByUsername("INVALID_USERNAME")
    assertThat(foundUserDetail).isNull()
  }
}
