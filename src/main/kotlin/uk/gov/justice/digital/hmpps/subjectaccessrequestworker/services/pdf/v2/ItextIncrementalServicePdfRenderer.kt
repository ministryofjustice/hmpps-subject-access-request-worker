package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2

import java.io.InputStream
import java.nio.file.Path

class ItextIncrementalServicePdfRenderer : ServicePdfRenderer {

  override suspend fun generateServicePdf(
    pdfRenderRequest: PdfRenderRequest,
    servicePdfPath: Path,
    serviceHtml: InputStream,
  ) {
    TODO("Not yet implemented")
  }
}
