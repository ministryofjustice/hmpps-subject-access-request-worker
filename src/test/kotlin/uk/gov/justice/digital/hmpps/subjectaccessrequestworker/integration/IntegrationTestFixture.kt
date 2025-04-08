package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration

import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.GetSubjectAccessRequestParams
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.Status
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import java.time.LocalDate
import java.util.UUID

class IntegrationTestFixture {

  companion object {
    val testSubjectAccessRequestId = UUID.fromString("83f1f9af-1036-4273-8252-633f6c7cc1d6")
    val testNomisId = "nomis-666"
    val testNdeliusCaseReferenceNumber = "ndeliusCaseReferenceId-666"
    var testDateTo = LocalDate.of(2025, 1, 1)
    var testDateFrom = LocalDate.of(2024, 1, 1)
    val subjectName = "REACHER, Joe"

    val expectedSubjectAccessRequestParameters = GetSubjectAccessRequestParams(
      prn = testNomisId,
      crn = testNdeliusCaseReferenceNumber,
      dateFrom = testDateFrom,
      dateTo = testDateTo,
    )

    fun createSubjectAccessRequestForService(service: String, status: Status = Status.Pending) = SubjectAccessRequest(
      id = testSubjectAccessRequestId,
      dateFrom = testDateFrom,
      dateTo = testDateTo,
      sarCaseReferenceNumber = "666",
      services = service,
      nomisId = testNomisId,
      ndeliusCaseReferenceId = testNdeliusCaseReferenceNumber,
      requestedBy = "Me",
      status = status,
    )

    fun SubjectAccessRequest.toGetParams() = GetSubjectAccessRequestParams(
      prn = this.nomisId,
      crn = this.ndeliusCaseReferenceId,
      dateFrom = this.dateFrom,
      dateTo = this.dateTo,
    )

    @JvmStatic
    fun generateReportTestCases() = listOf(
      TestCase(
        serviceName = "keyworker-api",
        serviceLabel = "Keyworker",
      ),
      TestCase(
        serviceName = "offender-case-notes",
        serviceLabel = "Sensitive Case Notes",
      ),
      TestCase(
        serviceName = "court-case-service",
        serviceLabel = "Prepare a Case for Sentence",
      ),
      TestCase(
        serviceName = "hmpps-restricted-patients-api",
        serviceLabel = "Restricted Patients",
      ),
      TestCase(
        serviceName = "hmpps-accredited-programmes-api",
        serviceLabel = "Accredited Programmes",
      ),
      TestCase(
        serviceName = "hmpps-complexity-of-need",
        serviceLabel = "Complexity Of Need",
      ),
      TestCase(
        serviceName = "offender-management-allocation-manager",
        serviceLabel = "Manage Prison Offender Manager Cases",
      ),
      TestCase(
        serviceName = "hmpps-book-secure-move-api",
        serviceLabel = "Book a Secure Move",
      ),
      TestCase(
        serviceName = "hmpps-non-associations-api",
        serviceLabel = "Non-associations",
      ),
      TestCase(
        serviceName = "hmpps-incentives-api",
        serviceLabel = "Incentives",
      ),
      TestCase(
        serviceName = "hmpps-manage-adjudications-api",
        serviceLabel = "Manage Adjudications",
      ),
      TestCase(
        serviceName = "hmpps-offender-categorisation-api",
        serviceLabel = "Categorisation Tool",
      ),
      TestCase(
        serviceName = "hmpps-hdc-api",
        serviceLabel = "Home Detention Curfew",
      ),
      TestCase(
        serviceName = "create-and-vary-a-licence-api",
        serviceLabel = "Create and Vary a Licence",
      ),
      TestCase(
        serviceName = "hmpps-uof-data-api",
        serviceLabel = "Use of Force",
      ),
      TestCase(
        serviceName = "hmpps-activities-management-api",
        serviceLabel = "Manage Activities and Appointments",
      ),
      TestCase(
        serviceName = "hmpps-resettlement-passport-api",
        serviceLabel = "Prepare Someone for Release",
      ),
      TestCase(
        serviceName = "hmpps-approved-premises-api",
        serviceLabel = "Approved Premises",
      ),
      TestCase(
        serviceName = "hmpps-education-employment-api",
        serviceLabel = "Education Employment",
      ),
      TestCase(
        serviceName = "make-recall-decision-api",
        serviceLabel = "Consider a Recall",
      ),
      TestCase(
        serviceName = "hmpps-education-and-work-plan-api",
        serviceLabel = "Personal Learning Plan",
      ),
      TestCase(
        serviceName = "G1",
        serviceLabel = "G1",
      ),
      TestCase(
        serviceName = "G2",
        serviceLabel = "G2",
      ),
      TestCase(
        serviceName = "G3",
        serviceLabel = "G3",
      ),
    )
  }

  data class TestCase(
    val serviceName: String,
    val serviceLabel: String,
    val dataJsonFile: String = "$serviceName-stub.json",
    val referencePdf: String = "$serviceName-reference.pdf",
  ) {
    override fun toString() = "SAR request for '$serviceLabel' data generates the expected PDF"
  }
}
