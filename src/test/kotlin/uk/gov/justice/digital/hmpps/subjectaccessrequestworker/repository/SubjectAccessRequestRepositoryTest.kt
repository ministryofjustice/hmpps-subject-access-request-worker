package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.test.context.ActiveProfiles
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.Status
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@ActiveProfiles("test")
@DataJpaTest
class SubjectAccessRequestRepositoryTest {

  @Autowired
  private lateinit var subjectAccessRequestRepository: SubjectAccessRequestRepository

  @BeforeEach
  fun setup() {
    subjectAccessRequestRepository.save(unclaimedSar)
    subjectAccessRequestRepository.save(claimedSarWithPendingStatus)
    subjectAccessRequestRepository.save(completedSar)
    subjectAccessRequestRepository.save(sarWithPendingStatusClaimedEarlier)
    subjectAccessRequestRepository.save(sarWithSearchableCaseReference)
    subjectAccessRequestRepository.save(sarWithSearchableNdeliusId)
  }

  @AfterEach
  fun tearDown() {
    subjectAccessRequestRepository.deleteAll()
  }

  @Nested
  inner class FindUnclaimed {
    @Test
    fun `returns only SAR entries that are pending and unclaimed or claimed before the given claimDateTime`() {
      val expectedUnclaimed: List<SubjectAccessRequest> = listOf(unclaimedSar, sarWithPendingStatusClaimedEarlier)
      assertThat(subjectAccessRequestRepository.findAll().size).isEqualTo(6)
      assertThat(
        subjectAccessRequestRepository.findUnclaimed(
          claimDateTime,
        ),
      ).usingRecursiveFieldByFieldElementComparatorIgnoringFields("contextId").isEqualTo(expectedUnclaimed)
    }
  }

  @Nested
  inner class UpdateSubjectAccessRequestIfClaimDateTimeLessThanWithClaimDateTimeIsAndClaimAttemptsIs {
    @Test
    fun `updates claimDateTime and claimAttempts if claimDateTime before threshold`() {
      val expectedUpdatedRecord = SubjectAccessRequest(
        id = sarWithPendingStatusClaimedEarlier.id,
        status = Status.Pending,
        dateFrom = dateFrom,
        dateTo = dateTo,
        sarCaseReferenceNumber = "1234abc",
        services = "{1,2,4}",
        nomisId = "",
        ndeliusCaseReferenceId = "1",
        requestedBy = "Test",
        requestDateTime = requestTime,
        claimAttempts = 2,
        claimDateTime = LocalDateTime.parse("01/02/2024 00:00", dateTimeFormatter),
      )

      val numberOfDbRecordsUpdated =
        subjectAccessRequestRepository.updateClaimDateTimeAndClaimAttemptsIfBeforeThreshold(
          sarWithPendingStatusClaimedEarlier.id,
          LocalDateTime.parse("30/06/2023 00:00", dateTimeFormatter),
          LocalDateTime.parse("01/02/2024 00:00", dateTimeFormatter),
        )

      assertThat(numberOfDbRecordsUpdated).isEqualTo(1)
      assertThat(subjectAccessRequestRepository.getReferenceById(sarWithPendingStatusClaimedEarlier.id))
        .usingRecursiveComparison().ignoringFields("contextId").isEqualTo(expectedUpdatedRecord)
    }

    @Test
    fun `does not update claimDateTime or claimAttempts if claimDateTime after threshold`() {
      val thresholdClaimDateTime = LocalDateTime.parse("30/06/2023 00:00", dateTimeFormatter)
      val currentDateTime = LocalDateTime.parse("01/02/2024 00:00", dateTimeFormatter)

      val expectedUpdatedRecord = SubjectAccessRequest(
        id = claimedSarWithPendingStatus.id,
        status = Status.Pending,
        dateFrom = dateFrom,
        dateTo = dateTo,
        sarCaseReferenceNumber = "1234abc",
        services = "{1,2,4}",
        nomisId = "",
        ndeliusCaseReferenceId = "1",
        requestedBy = "Test",
        requestDateTime = requestTime,
        claimAttempts = 1,
        claimDateTime = claimDateTime,
      )

      val numberOfDbRecordsUpdated =
        subjectAccessRequestRepository.updateClaimDateTimeAndClaimAttemptsIfBeforeThreshold(
          claimedSarWithPendingStatus.id,
          thresholdClaimDateTime,
          currentDateTime,
        )

      assertThat(numberOfDbRecordsUpdated).isEqualTo(0)
      assertThat(subjectAccessRequestRepository.findAll().size).isEqualTo(6)
      assertThat(subjectAccessRequestRepository.getReferenceById(claimedSarWithPendingStatus.id))
        .usingRecursiveComparison().ignoringFields("contextId").isEqualTo(expectedUpdatedRecord)
    }

    @Test
    fun `its not possible to claim a subject access request with status 'Completed'`() {
      val numberOfDbRecordsUpdated =
        subjectAccessRequestRepository.updateClaimDateTimeAndClaimAttemptsIfBeforeThreshold(
          completedSar.id,
          LocalDateTime.parse("01/02/2024 00:30", dateTimeFormatter),
          LocalDateTime.parse("01/02/2024 00:35", dateTimeFormatter),
        )

      assertThat(numberOfDbRecordsUpdated).isEqualTo(0)
      assertThat(subjectAccessRequestRepository.findAll().size).isEqualTo(6)

      val result = subjectAccessRequestRepository.getReferenceById(completedSar.id)
      assertThat(result.claimDateTime).isEqualTo(LocalDateTime.parse("01/01/2024 00:05", dateTimeFormatter))
      assertThat(result.claimAttempts).isEqualTo(1)
    }
  }

  private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
  private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
  private val dateFrom = LocalDate.parse("30/12/2023", dateFormatter)
  private val dateTo = LocalDate.parse("30/01/2024", dateFormatter)
  private val requestTime = LocalDateTime.parse("30/01/2024 00:00", dateTimeFormatter)
  private val requestTimeLater = LocalDateTime.parse("30/03/2024 00:00", dateTimeFormatter)
  private val claimDateTime = LocalDateTime.parse("30/01/2024 00:00", dateTimeFormatter)
  private val claimDateTimeEarlier = LocalDateTime.parse("30/01/2023 00:00", dateTimeFormatter)
  private val downloadDateTime = LocalDateTime.parse("01/06/2024 00:00", dateTimeFormatter)

  final val unclaimedSar = SubjectAccessRequest(
    id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
    status = Status.Pending,
    dateFrom = dateFrom,
    dateTo = dateTo,
    sarCaseReferenceNumber = "1234abc",
    services = "{1,2,4}",
    nomisId = "",
    ndeliusCaseReferenceId = "1",
    requestedBy = "Test",
    requestDateTime = requestTime,
    claimAttempts = 0,
  )
  final val claimedSarWithPendingStatus = SubjectAccessRequest(
    id = UUID.fromString("22222222-2222-2222-2222-222222222222"),
    status = Status.Pending,
    dateFrom = dateFrom,
    dateTo = dateTo,
    sarCaseReferenceNumber = "1234abc",
    services = "{1,2,4}",
    nomisId = "",
    ndeliusCaseReferenceId = "1",
    requestedBy = "Test",
    requestDateTime = requestTime,
    claimAttempts = 1,
    claimDateTime = claimDateTime,
  )
  final val completedSar = SubjectAccessRequest(
    id = UUID.fromString("33333333-3333-3333-3333-333333333333"),
    status = Status.Completed,
    dateFrom = dateFrom,
    dateTo = dateTo,
    sarCaseReferenceNumber = "1234abc",
    services = "{1,2,4}",
    nomisId = "",
    ndeliusCaseReferenceId = "1",
    requestedBy = "Test",
    requestDateTime = requestTime,
    claimAttempts = 1,
    claimDateTime = LocalDateTime.parse("01/01/2024 00:05", dateTimeFormatter),
    lastDownloaded = downloadDateTime,
  )
  final val sarWithPendingStatusClaimedEarlier = SubjectAccessRequest(
    id = UUID.fromString("44444444-4444-4444-4444-444444444444"),
    status = Status.Pending,
    dateFrom = dateFrom,
    dateTo = dateTo,
    sarCaseReferenceNumber = "1234abc",
    services = "{1,2,4}",
    nomisId = "",
    ndeliusCaseReferenceId = "1",
    requestedBy = "Test",
    requestDateTime = requestTime,
    claimAttempts = 1,
    claimDateTime = claimDateTimeEarlier,
  )
  final val sarWithSearchableCaseReference = SubjectAccessRequest(
    id = UUID.fromString("55555555-5555-5555-5555-555555555555"),
    status = Status.Completed,
    dateFrom = dateFrom,
    dateTo = dateTo,
    sarCaseReferenceNumber = "testForSearch",
    services = "{1,2,4}",
    nomisId = "",
    ndeliusCaseReferenceId = "1",
    requestedBy = "Test",
    requestDateTime = requestTime,
    claimAttempts = 1,
    claimDateTime = claimDateTimeEarlier,
  )
  final val sarWithSearchableNdeliusId = SubjectAccessRequest(
    id = UUID.fromString("66666666-6666-6666-6666-666666666666"),
    status = Status.Completed,
    dateFrom = dateFrom,
    dateTo = dateTo,
    sarCaseReferenceNumber = "1234abc",
    services = "{1,2,4}",
    nomisId = "",
    ndeliusCaseReferenceId = "testForSearch",
    requestedBy = "Test",
    requestDateTime = requestTimeLater,
    claimAttempts = 1,
    claimDateTime = claimDateTimeEarlier,
  )
}
