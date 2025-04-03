package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.LocationsApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.NomisMappingApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.LocationDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.PrisonDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.UserDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.TemplateHelpers
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.TemplateResources
import java.io.File
import java.io.FileOutputStream

abstract class BaseGeneratePdfTest {
  protected val prisonDetailsRepository: PrisonDetailsRepository = mock()
  protected val userDetailsRepository: UserDetailsRepository = mock()
  protected val locationDetailsRepository: LocationDetailsRepository = mock()
  protected val locationsApiClient: LocationsApiClient = mock()
  protected val nomisMappingApiClient: NomisMappingApiClient = mock()
  protected val templateHelpers = TemplateHelpers(prisonDetailsRepository, userDetailsRepository, locationDetailsRepository, locationsApiClient, nomisMappingApiClient)
  protected val templateResources: TemplateResources = TemplateResources(
    templatesDirectory = "/templates",
    mandatoryServiceTemplates = listOf("G1", "G2", "G3"),
  )
  protected val templateRenderService = TemplateRenderService(templateHelpers, templateResources)
  protected val telemetryClient: TelemetryClient = mock()
  protected val dateService: DateService = mock()
  protected val generatePdfService = GeneratePdfService(templateRenderService, telemetryClient, dateService)

  /**
   * Change to ON_SUCCESS/NEVER if you need to retain the generated PDF files after the tests have run.
   */
  @TempDir(cleanup = CleanupMode.ALWAYS)
  @JvmField
  protected var tempFolder: File? = null

  protected fun generateSubjectAccessRequestPdf(filename: String, serviceList: List<DpsService>) {
    createPdfDocument(filename).use { pdfDocument ->
      Document(pdfDocument).use { document ->
        generatePdfService.addData(pdfDocument, document, serviceList)
      }
    }
  }

  protected fun createPdfDocument(filename: String): PdfDocument = PdfDocument(
    PdfWriter(
      FileOutputStream(
        resolveTempFilePath(filename),
      ),
    ),
  )

  protected fun getGeneratedPdfDocument(filename: String): PdfDocument = PdfDocument(
    PdfReader(
      resolveTempFilePath(filename),
    ),
  )

  protected fun resolveTempFilePath(filename: String): String {
    assertThat(tempFolder).isNotNull()
    assertThat(tempFolder).exists()
    return tempFolder!!.toPath().resolve(filename).toString().also { println("resolved to: $it") }
  }
}
