package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aMultipart
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.binaryEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class DocumentApiMockServer : WireMockServer(8084) {

  companion object {
    const val SERVICE_NAME_HEADER = "DPS-Subject-Access-Requests"
  }

  fun stubHealthPing(status: Int) {
    stubFor(
      get("/health/ping").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{"status":"${if (status == 200) "UP" else "DOWN"}"}""")
          .withStatus(status),
      ),
    )
  }

  fun stubUploadFileSuccess(subjectAccessRequestId: String, expectedFileContent: ByteArray) {
    stubFor(
      post(urlPathEqualTo("/documents/SUBJECT_ACCESS_REQUEST_REPORT/$subjectAccessRequestId"))
        .withHeader("Service-Name", equalTo(SERVICE_NAME_HEADER))
        .withMultipartRequestBody(
          aMultipart()
            .withName("report.pdf")
            .withBody(binaryEqualTo(expectedFileContent)),
        )
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200)
            .withBody(documentUploadSuccessResponseJson(subjectAccessRequestId)),
        ),
    )
  }

  fun verifyStoreDocumentIsCalled(times: Int, subjectAccessRequestId: String) {
    verify(
      times,
      postRequestedFor(urlPathEqualTo("/documents/SUBJECT_ACCESS_REQUEST_REPORT/$subjectAccessRequestId"))
        .withHeader("Service-Name", equalTo(SERVICE_NAME_HEADER)),
    )
  }

  fun stubUploadFileFailsWithStatus(
    subjectAccessRequestId: String,
    expectedFileContent: ByteArray,
    status: Int,
  ) {
    stubFor(
      post(
        urlPathEqualTo("/documents/SUBJECT_ACCESS_REQUEST_REPORT/$subjectAccessRequestId"),
      )
        .withHeader("Service-Name", equalTo(SERVICE_NAME_HEADER))
        .withMultipartRequestBody(
          aMultipart()
            .withName("report.pdf")
            .withBody(
              binaryEqualTo(expectedFileContent),
            ),
        ).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status)
            .withBody(
              """
            {
              "status": $status,
              "errorCode": 10001,
              "userMessage": "something went wrong",
              "developerMessage": "something went wrong",
              "moreInfo": "its broken"
            }
              """.trimIndent(),
            ),
        ),
    )
  }

  fun stubUploadFileFailsWithStatusThenSucceedsOnRetry(
    subjectAccessRequestId: String,
    expectedFileContent: ByteArray,
    status: Int,
  ) {
    stubFor(
      post(
        urlPathEqualTo("/documents/SUBJECT_ACCESS_REQUEST_REPORT/$subjectAccessRequestId"),
      ).inScenario("fail first request")
        .withHeader("Service-Name", equalTo(SERVICE_NAME_HEADER))
        .withMultipartRequestBody(
          aMultipart()
            .withName("report.pdf")
            .withBody(
              binaryEqualTo(expectedFileContent),
            ),
        ).willSetStateTo("failed-first-request")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status)
            .withBody(
              """
            {
              "status": $status,
              "errorCode": 10001,
              "userMessage": "something went wrong",
              "developerMessage": "something went wrong",
              "moreInfo": "its broken"
            }
              """.trimIndent(),
            ),
        ),
    )

    stubFor(
      post(
        urlPathEqualTo("/documents/SUBJECT_ACCESS_REQUEST_REPORT/$subjectAccessRequestId"),
      ).inScenario("fail first request")
        .whenScenarioStateIs("failed-first-request")
        .withHeader("Service-Name", equalTo(SERVICE_NAME_HEADER))
        .withMultipartRequestBody(
          aMultipart()
            .withName("report.pdf")
            .withBody(
              binaryEqualTo(expectedFileContent),
            ),
        )
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200)
            .withBody(documentUploadSuccessResponseJson(subjectAccessRequestId)),
        ),
    )
  }

  fun documentUploadSuccessResponseJson(subjectAccessRequestId: String) = """
    {
      "documentUuid": "$subjectAccessRequestId",
      "documentType": "Subject Access Request",
      "documentFilename": "Subject Access Request - $subjectAccessRequestId",
      "filename": ""Subject Access Request - $subjectAccessRequestId.pdf",
      "fileExtension": "pdf",
      "fileSize": 1,
      "fileHash": "1",
      "mimeType": "pdf",
      "metadata": {
        "prisonCode": "KMI",
        "prisonNumber": "C345TDE",
        "court": "Birmingham Magistrates",
        "warrantDate": "2023-11-14"
      },
      "createdTime": "2024-10-29T16:15:58.590Z",
      "createdByServiceName": "$SERVICE_NAME_HEADER",
      "createdByUsername": "Robert Bobby"
    }
  """.trimIndent()

  fun verifyNeverCalled() {
    verify(0, anyRequestedFor(anyUrl()))
  }
}

class DocumentApiExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    @JvmField
    val documentApi = DocumentApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext): Unit = documentApi.start()
  override fun beforeEach(context: ExtensionContext): Unit = documentApi.resetAll()
  override fun afterAll(context: ExtensionContext): Unit = documentApi.stop()
}
