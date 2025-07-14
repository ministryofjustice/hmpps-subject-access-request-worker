package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration

import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.kernel.pdf.canvas.parser.listener.SimpleTextExtractionStrategy
import org.assertj.core.api.Assertions.assertThat
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.IntegrationTestFixture.Companion.testNomisId
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.LocationDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.PrisonDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.Status
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest

class BaseProcessorIntTest : IntegrationTestBase() {

  protected fun assertRequestClaimedAtLeastOnce(subjectAccessRequest: SubjectAccessRequest) {
    val target = getSubjectAccessRequest(subjectAccessRequest.id)
    assertThat(target.claimDateTime).isNotNull()
    assertThat(target.claimAttempts).isGreaterThanOrEqualTo(1)
  }

  protected fun assertUploadedDocumentMatchesExpectedPdf(serviceName: String) {
    val expected = getPreGeneratedPdfDocument("$serviceName-reference.pdf")
    val actual = getUploadedPdfDocument()

    assertUploadedDocumentMatchesExpectedPdf(actual, expected)
  }

  protected fun assertUploadedDocumentMatchesExpectedPdf(actual: PdfDocument, expected: PdfDocument) {
    assertThat(actual.numberOfPages).isEqualTo(expected.numberOfPages)

    for (i in 1..actual.numberOfPages) {
      val actualPageN = PdfTextExtractor.getTextFromPage(actual.getPage(i), SimpleTextExtractionStrategy())
      val expectedPageN = PdfTextExtractor.getTextFromPage(expected.getPage(i), SimpleTextExtractionStrategy())

      assertThat(actualPageN)
        .isEqualToIgnoringCase(expectedPageN)
        .withFailMessage("actual page: $i did not match expected.")
    }
  }

  protected fun assertAttachmentPageMatchesExpected(actualPdfDoc: PdfDocument, expectedPdfDoc: PdfDocument, pageNumber: Int, attachmentNumber: Int) {
    val expected = actualPdfDoc.getPage(pageNumber)
    val actual = expectedPdfDoc.getPage(pageNumber)
    val actualPageText = PdfTextExtractor.getTextFromPage(actual, SimpleTextExtractionStrategy())

    assertThat(actualPageText).`as`("attachment $attachmentNumber text").contains("Attachment: $attachmentNumber")
    assertThat(actual.contentBytes).`as`("page $pageNumber content bytes").isEqualTo(expected.contentBytes)
  }

  protected fun assertPageMatchesExpected(actualPdfDoc: PdfDocument, expectedPdfDoc: PdfDocument, pageNumber: Int) {
    val expected = actualPdfDoc.getPage(pageNumber)
    val actual = expectedPdfDoc.getPage(pageNumber)
    assertThat(actual.contentBytes).`as`("page $pageNumber content bytes").isEqualTo(expected.contentBytes)
  }

  protected fun assertUploadedDocumentMatchesExpectedNoDataHeldPdf(serviceName: String, serviceLabel: String) {
    val actual = getUploadedPdfDocument()

    assertThat(actual.numberOfPages).isEqualTo(5)
    val actualPageContent = PdfTextExtractor.getTextFromPage(actual.getPage(4), SimpleTextExtractionStrategy())
    val expectedPageContent = contentWhenNoDataHeld(serviceLabel, testNomisId)

    assertThat(actualPageContent)
      .isEqualToIgnoringCase(expectedPageContent)
      .withFailMessage("$serviceName report did not match expected")
  }

  protected fun contentWhenNoDataHeld(serviceLabel: String, nomisId: String): String = StringBuilder("$serviceLabel\n")
    .append("No Data Held")
    .append("\n")
    .append("Name: REACHER, Joe ")
    .append("\n")
    .append("NOMIS ID: $nomisId")
    .append("\n")
    .append("Official Sensitive")
    .toString()

  protected fun insertSubjectAccessRequest(serviceName: String, status: Status): SubjectAccessRequest {
    val sar = createSubjectAccessRequestWithStatus(status, serviceName)
    assertSubjectAccessRequestHasStatus(sar, status)
    return sar
  }

  protected fun requestHasStatus(subjectAccessRequest: SubjectAccessRequest, expectedStatus: Status): Boolean {
    val target = getSubjectAccessRequest(subjectAccessRequest.id)
    return expectedStatus == target.status
  }

  protected fun clearDatabaseData() {
    subjectAccessRequestRepository.deleteAll()
    prisonDetailsRepository.deleteAll()
    locationDetailsRepository.deleteAll()
  }

  protected fun populatePrisonDetails() {
    prisonDetailsRepository.saveAndFlush(PrisonDetail("MDI", "MOORLAND (HMP & YOI)"))
    prisonDetailsRepository.saveAndFlush(PrisonDetail("LEI", "LEEDS (HMP)"))
  }

  protected fun populateLocationDetails() {
    locationDetailsRepository.saveAndFlush(
      LocationDetail(
        "cac85758-380b-49fc-997f-94147e2553ac",
        357591,
        "ASSO A WING",
      ),
    )
    locationDetailsRepository.saveAndFlush(
      LocationDetail(
        "d0763236-c073-4ef4-9592-419bf0cd72cb",
        357592,
        "ASSO B WING",
      ),
    )
    locationDetailsRepository.saveAndFlush(
      LocationDetail(
        "8ac39ebb-499d-4862-ae45-0b091253e89d",
        27187,
        "ADJ",
      ),
    )
  }
}
