package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.springframework.web.util.HtmlUtils.htmlEscape
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
      val xhtml = buildXhtmlDocument(
        serviceHtml = rawHtml,
        subjectName = pdfRenderRequest.subjectName,
        prn = pdfRenderRequest.subjectAccessRequest.nomisId,
        crn = pdfRenderRequest.subjectAccessRequest.ndeliusCaseReferenceId,
      )

      FileOutputStream(servicePdfPath.toFile()).use { outputStream ->
        PdfRendererBuilder()
          .useFastMode()
          .withHtmlContent(xhtml, servicePdfPath.parent.toUri().toString())
          .toStream(outputStream)
          .run()
      }
    }
  }

  private fun buildXhtmlDocument(
    serviceHtml: String,
    subjectName: String,
    prn: String?,
    crn: String?,
  ): String {
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

    val subjectIdLabel = when {
      prn != null -> "NOMIS ID:"
      crn != null -> "nDelius ID:"
      else -> ""
    }

    val subjectIdValue = prn ?: crn ?: ""

    val headerRightHtml =
      if (subjectIdLabel.isNotBlank()) {
        """
        <div id="header-right">
          <span class="header-label">Name:</span>
          <span>${htmlEscape(subjectName)}</span>
          <br />
          <span class="header-label">${htmlEscape(subjectIdLabel)}</span>
          <span>${htmlEscape(subjectIdValue)}</span>
        </div>
      """.trimIndent()
      } else {
        """
        <div id="header-right">
          <span class="header-label">Name:</span>
          <span>${htmlEscape(subjectName)}</span>
        </div>
      """.trimIndent()
      }

    return """
      <!DOCTYPE html>
      <html>
        <head>
          <meta charset="UTF-8" />
          <style type="text/css">
          @page {
            size: A4;
            margin: 50pt 35pt 85pt 35pt;
        
            @top-right {
              content: element(header-right);
            }
        
            @bottom-center {
              content: "Official Sensitive";
              font-family: Helvetica, Arial, sans-serif;
              font-size: 10pt;
            }
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
        
          #header-right {
            position: running(header-right);
            text-align: right;
            font-family: Helvetica, Arial, sans-serif;
            font-size: 10pt;
            line-height: 1.3;
            color: #0b0c0c;
          }
        
          .header-label {
            font-weight: bold;
          }
        
          h1,
          h2,
          h3,
          h4,
          h5 {
            display: block;
            font-weight: 700;
            color: #0b0c0c;
            padding: 0;
          }
        
          h1 {
            font-size: 18pt;
            line-height: 1.15;
            margin: 14pt 0 8pt 0;
          }
        
          h1.title {
            font-size: 20pt;
            line-height: 1.2;
            text-align: center;
            margin: 0 0 14pt 0;
          }
        
          h2 {
            font-size: 17pt;
            line-height: 1.15;
            margin: 12pt 0 6pt 0;
          }
        
          h3 {
            font-size: 16pt;
            line-height: 1.15;
            margin: 10pt 0 6pt 0;
          }
        
          h4 {
            font-size: 15pt;
            line-height: 1.15;
            margin: 10pt 0 5pt 0;
          }
        
          h5 {
            font-size: 14pt;
            line-height: 1.15;
            margin: 10pt 0 5pt 0;
          }
        
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
          $headerRightHtml
  
          <main>
            $serviceBodyHtml
          </main>
        </body>
      </html>
    """.trimIndent()
  }
}
