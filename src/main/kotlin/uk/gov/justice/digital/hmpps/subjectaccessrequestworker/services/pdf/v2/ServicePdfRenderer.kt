package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2

import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import java.io.InputStream

interface ServicePdfRenderer {
  suspend fun generateServicePdf(
    pdfRenderRequest: PdfRenderRequest,
    serviceConfiguration: ServiceConfiguration,
    serviceHtml: InputStream,
  )
}
