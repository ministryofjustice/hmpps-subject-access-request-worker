package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Path

class OpenHtmlServicePdfRenderer : ServicePdfRenderer {

  override suspend fun generateServicePdf(
    pdfRenderRequest: PdfRenderRequest,
    servicePdfPath: Path,
    serviceHtml: InputStream,
  ) {
    withContext(Dispatchers.IO) {
      val rawHtml = serviceHtml.bufferedReader(Charsets.UTF_8).use { it.readText() }
      val xhtml = buildXhtmlDocument(serviceHtml = rawHtml)

      FileOutputStream(servicePdfPath.toFile()).use { outputStream ->
        PdfRendererBuilder()
          .useFastMode()
          .withHtmlContent(xhtml, servicePdfPath.parent.toUri().toString())
          .toStream(outputStream)
          .run()
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
              margin: 70px 35px 70px 35px;
            }

            body {
              margin: 0;
              font-family: Arial, Helvetica, sans-serif;
              font-size: 12pt;
              color: #0b0c0c;
            }

            img {
              max-width: 100%;
            }

            table {
              border-collapse: collapse;
            }

            .page-break {
              page-break-before: always;
              break-before: page;
            }

            $serviceCss
            
            table {
              width: 100%;
              max-width: 100%;
              border-collapse: collapse;
              table-layout: fixed;
            }
          
            td, th {
              word-wrap: break-word;
              word-break: break-all;
              white-space: normal;
              vertical-align: top;
            }
          
            table.summary-list,
            table.data-table {
              width: 100%;
              max-width: 100%;
              table-layout: fixed;
            }
          
            table.summary-list td,
            table.data-table td {
              word-wrap: break-word;
              word-break: break-all;
              white-space: normal;
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
