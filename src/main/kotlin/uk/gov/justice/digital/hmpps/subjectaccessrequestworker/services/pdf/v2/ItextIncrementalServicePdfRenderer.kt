package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2.incremental.IncrementalHtmlPdfBuilder
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2.incremental.SubjectAccessRequestHtmlHandler
import java.io.InputStream

@Service
class ItextIncrementalServicePdfRenderer(
  private val telemetryClient: TelemetryClient,
) : ServicePdfRenderer {

  override suspend fun generateServicePdf(
    pdfRenderRequest: PdfRenderRequest,
    serviceConfiguration: ServiceConfiguration,
    serviceHtml: InputStream,
  ) {
    SubjectAccessRequestHtmlHandler(
      builder = IncrementalHtmlPdfBuilder(pdfRenderRequest, serviceConfiguration),
      subjectAccessRequest = pdfRenderRequest.subjectAccessRequest,
      telemetryClient = telemetryClient,
    )
  }
}
