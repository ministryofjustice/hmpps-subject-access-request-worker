package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2

import com.microsoft.applicationinsights.TelemetryClient
import nu.validator.htmlparser.sax.HtmlParser
import org.springframework.stereotype.Service
import org.xml.sax.InputSource
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.TempDirectoryService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2.incremental.IncrementalHtmlPdfBuilder
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2.incremental.SubjectAccessRequestHtmlHandler
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2.incremental.TempFileImageStore
import java.io.InputStream

@Service
class ItextIncrementalServicePdfRenderer(
  private val tempDirectoryService: TempDirectoryService,
  private val telemetryClient: TelemetryClient,
) : ServicePdfRenderer {

  override suspend fun generateServicePdf(
    pdfRenderRequest: PdfRenderRequest,
    serviceConfiguration: ServiceConfiguration,
    serviceHtml: InputStream,
  ) {
    TempFileImageStore(tempDirectoryService, pdfRenderRequest.subjectAccessRequest).use { imageStore ->
      IncrementalHtmlPdfBuilder(pdfRenderRequest, serviceConfiguration, imageStore).use { builder ->
        val parser = HtmlParser().apply {
          contentHandler = SubjectAccessRequestHtmlHandler(
            builder = builder,
            imageStore = imageStore,
            subjectAccessRequest = pdfRenderRequest.subjectAccessRequest,
            minBufferSize = 50_000,
            telemetryClient = telemetryClient,
          )
        }
        parser.parse(InputSource(serviceHtml))
      }
    }
  }
}
