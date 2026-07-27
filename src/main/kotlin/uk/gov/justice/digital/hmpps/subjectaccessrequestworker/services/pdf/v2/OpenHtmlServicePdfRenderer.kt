package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2

import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.event.PdfDocumentEvent
import com.itextpdf.kernel.utils.PdfMerger
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.TempDirectoryService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.createWritablePdfDocument
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.events.SubjectAccessRequestHeaderAndFooterEventHandler
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.newDocument
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Path

class OpenHtmlServicePdfRenderer(
  private val tempDirectoryService: TempDirectoryService,
) : ServicePdfRenderer {

  override suspend fun generateServicePdf(
    pdfRenderRequest: PdfRenderRequest,
    servicePdfPath: Path,
    serviceHtml: InputStream,
  ) {
    withContext(Dispatchers.IO) {
      val rawHtml = serviceHtml.bufferedReader(Charsets.UTF_8).use { it.readText() }
      val xhtml = buildXhtmlDocument(serviceHtml = rawHtml)

      val tempPath = tempDirectoryService
        .create("${pdfRenderRequest.subjectAccessRequest.id}_openhtml_")
        .resolve("openhtml-service.pdf")
      try {
        FileOutputStream(tempPath.toFile()).use { outputStream ->
          PdfRendererBuilder()
            .useFastMode()
            .withHtmlContent(xhtml, servicePdfPath.parent.toUri().toString())
            .toStream(outputStream)
            .run()
        }

        createWritablePdfDocument(output = servicePdfPath).use { outputPdf ->
          newDocument(outputPdf).use { document ->
            outputPdf.addEventHandler(
              PdfDocumentEvent.END_PAGE,
              SubjectAccessRequestHeaderAndFooterEventHandler(
                document = document,
                subjectName = pdfRenderRequest.subjectName,
                nomisId = pdfRenderRequest.subjectAccessRequest.nomisId,
                ndeliusCaseReferenceId = pdfRenderRequest.subjectAccessRequest.ndeliusCaseReferenceId,
              ),
            )
            PdfDocument(PdfReader(tempPath.toFile())).use { inputPdf ->
              PdfMerger(outputPdf).merge(inputPdf, 1, inputPdf.numberOfPages)
            }
          }
        }
      } finally {
        tempPath.parent.toFile().deleteRecursively()
      }
    }
  }

  private fun buildXhtmlDocument(serviceHtml: String): String {
    val serviceFragment = Jsoup.parseBodyFragment(serviceHtml)

    serviceFragment.outputSettings()
      .syntax(org.jsoup.nodes.Document.OutputSettings.Syntax.xml)
      .escapeMode(org.jsoup.nodes.Entities.EscapeMode.xhtml)
      .charset(Charsets.UTF_8)
      .prettyPrint(false)

    val serviceCss = serviceFragment
      .select("style")
      .joinToString("\n") { it.html() }

    serviceFragment.select("style").remove()

    val serviceBodyHtml = serviceFragment.body().html()

    return """
      <!DOCTYPE html>
      <html>
        <head>
          <meta charset="UTF-8" />
          <style type="text/css">
          @page {
            size: A4;
            margin: 50pt 35pt 70pt 35pt;
          }
        
          body {
            margin: 0;
            font-family: Helvetica, Arial, sans-serif;
            font-size: 12pt;
            color: #0b0c0c;
          }
        
          img {
            max-width: 100%;
          }
        
          .page-break {
            page-break-before: always;
            break-before: page;
          }
        
          $serviceCss
        
          table {
            max-width: 100%;
            border-collapse: collapse;
          }
        
          table.summary-list,
          table.data-table {
            max-width: 100%;
            table-layout: fixed;
          }
        
          td,
          th {
            word-wrap: break-word;
            white-space: normal;
            vertical-align: top;
          }
        </style>
        </head>
        <body>
          <main>
            $serviceBodyHtml
          </main>
        </body>
      </html>
    """.trimIndent()
  }
}
