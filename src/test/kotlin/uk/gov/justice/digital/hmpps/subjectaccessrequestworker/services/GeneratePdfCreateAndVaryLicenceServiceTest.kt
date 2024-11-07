package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.layout.Document
import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.TemplateHelpers
import java.io.FileOutputStream

class GeneratePdfCreateAndVaryLicenceServiceTest {
  private val templateHelpers = TemplateHelpers()
  private val templateRenderService = TemplateRenderService(templateHelpers)
  private val telemetryClient: TelemetryClient = mock()
  private val generatePdfService = GeneratePdfService(templateRenderService, telemetryClient)

  @Test
  fun `generatePdfService renders for Create and Vary a License Service`() {
    val serviceList = listOf(DpsService(name = "create-and-vary-a-licence-api", content = testCreateAndVaryLicenceServiceData))
    val pdfDocument = PdfDocument(PdfWriter(FileOutputStream("dummy-cvl-template.pdf")))
    val document = Document(pdfDocument)
    generatePdfService.addData(pdfDocument, document, serviceList)
    document.close()
    val reader = PdfDocument(PdfReader("dummy-cvl-template.pdf"))
    val text = PdfTextExtractor.getTextFromPage(reader.getPage(2))
    assertThat(text).contains("Create and vary a licence")
  }

  private val testCreateAndVaryLicenceServiceData = mapOf(
    "licences" to arrayListOf(
      mapOf(
        "kind" to "VARIATION",
        "id" to 157,
        "typeCode" to "AP",
        "version" to "2.1",
        "statusCode" to "ACTIVE",
        "nomsId" to "A8272DY",
        "bookingId" to 1201812,
        "appointmentPerson" to "Test",
        "appointmentTime" to null,
        "appointmentTimeType" to "IMMEDIATE_UPON_RELEASE",
        "appointmentAddress" to "Test, , Test, Test, TEST",
        "appointmentContact" to "00000000000",
        "approvedDate" to "26/07/2024 14:49:51",
        "approvedByUsername" to "CVL_ACO",
        "submittedDate" to "26/07/2024 14:47:02",
        "approvedByName" to "Bob User",
        "supersededDate" to null,
        "dateCreated" to "26/07/2024 14:44:07",
        "createdByUsername" to "CVL_COM",
        "dateLastUpdated" to "26/07/2024 14:50:09",
        "updatedByUsername" to "CVL_COM",
        "standardLicenceConditions" to arrayListOf(
          mapOf(
            "code" to "9ce9d594-e346-4785-9642-c87e764bee37",
            "text" to "Be of good behaviour and not behave in a way which undermines the purpose of the licence period.",
          ),
          mapOf(
            "code" to "3b19fdb0-4ca3-4615-9fdd-61fabc1587af",
            "text" to "Not commit any offence.",
          ),
          mapOf(
            "code" to "3361683a-504a-4357-ae22-6aa01b370b4a",
            "text" to "Keep in touch with the supervising officer in accordance with instructions given by the supervising officer.",
          ),
          mapOf(
            "code" to "9fc04065-df29-4bda-9b1d-bced8335c356",
            "text" to "Receive visits from the supervising officer in accordance with any instructions given by the supervising officer.",
          ),
          mapOf(
            "code" to "e670ac69-eda2-4b04-a0a1-a3c8492fe1e6",
            "text" to "Reside permanently at an address approved by the supervising officer and obtain the prior permission of the supervising officer for any stay of one or more nights at a different address.",
          ),
          mapOf(
            "code" to "78A5F860-4791-48F2-B707-D6D4413850EE",
            "text" to "Tell the supervising officer if you use a name which is different to the name or names which appear on your licence.",
          ),
          mapOf(
            "code" to "6FA6E492-F0AB-4E76-B868-63813DB44696",
            "text" to "Tell the supervising officer if you change or add any contact details, including phone number or email.",
          ),
          mapOf(
            "code" to "88069445-08cb-4f16-915f-5a162d085c26",
            "text" to "Not undertake work, or a particular type of work, unless it is approved by the supervising officer and notify the supervising officer in advance of any proposal to undertake work or a particular type of work.",
          ),
          mapOf(
            "code" to "7d416906-0e94-4fde-ae86-8339d339ccb7",
            "text" to "Not travel outside the United Kingdom, the Channel Islands or the Isle of Man except with the prior permission of the supervising officer or for the purposes of immigration deportation or removal.",
          ),
        ),
        "standardPssConditions" to null,
        "additionalLicenceConditions" to arrayListOf(
          mapOf(
            "code" to "5db26ab3-9b6f-4bee-b2aa-53aa3f3be7dd",
            "version" to "2.1",
            "category" to "Residence at a specific place",
            "expandedText" to "You must reside overnight within West Midlands probation region while of no fixed abode, unless otherwise approved by your supervising officer.",
            "data" to arrayListOf(
              mapOf(
                "field" to "probationRegion",
                "value" to "West Midlands",
              ),
            ),
            "uploadSummary" to null,
            "readyToSubmit" to true,
          ),
          mapOf(
            "code" to "b72fdbf2-0dc9-4e7f-81e4-c2ccb5d1bc90",
            "version" to "2.1",
            "category" to "Contact with a person",
            "expandedText" to "Attend all appointments arranged for you with a psychiatrist / psychologist / medical practitioner, unless otherwise approved by your supervising officer.",
            "data" to null,
            "uploadSummary" to null,
            "readyToSubmit" to true,
          ),
        ),
        "additionalPssConditions" to null,
        "bespokeConditions" to arrayListOf(
          mapOf(
            "text" to "test",
          ),
        ),
        "createdByFullName" to "CVL COM",
        "licenceVersion" to "2.0",
      ),
    ),
  )
}
