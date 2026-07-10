package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2

import java.io.InputStream
import java.nio.file.Path

interface ServicePdfRenderer {
  suspend fun generateServicePdf(servicePdfPath: Path, serviceHtml: InputStream)
}
