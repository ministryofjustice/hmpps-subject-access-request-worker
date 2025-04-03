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
import com.google.gson.Gson
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.DocumentStorageClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.fileHash
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import java.math.BigInteger
import java.security.MessageDigest

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

  fun stubUploadFileSuccessWithMetadata(
    subjectAccessRequestId: String,
    fileSize: Int,
    expectedFileContent: ByteArray,
    metadata: Any?,
  ) {
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
            .withBody(
              documentUploadSuccessResponseJsonWithMetadata(
                subjectAccessRequestId,
                fileSize,
                expectedFileContent,
                metadata,
              ),
            ),
        ),
    )
  }

  fun stubUploadFileSuccess(subjectAccessRequest: SubjectAccessRequest) = stubUploadFileSuccess(
    subjectAccessRequest.id.toString(),
  )

  fun stubUploadFileSuccess(subjectAccessRequestId: String) {
    stubFor(
      post(urlPathEqualTo("/documents/SUBJECT_ACCESS_REQUEST_REPORT/$subjectAccessRequestId"))
        .withHeader("Service-Name", equalTo(SERVICE_NAME_HEADER))
        .withMultipartRequestBody(
          aMultipart()
            .withName("file"),
        )
        .willReturn(
          aResponse()
            .withTransformers("response-template")
            .withHeader("Content-Type", "application/json")
            .withStatus(200)
            .withBody(
              """
              {
                "documentUuid": "{{request.pathSegments.2}}",
                "documentType": "Subject Access Request",
                "documentFilename": "Subject Access Request - {{request.pathSegments.2}}",
                "filename": "Subject Access Request - {{request.pathSegments.2}}.pdf",
                "fileExtension": "pdf",
                "fileSize": {{request.parts.file.headers.content-length.0}},
                "fileHash": "12345",
                "mimeType": "pdf",
                "metadata": null,
                "createdTime": "2024-10-29T16:15:58.590Z",
                "createdByServiceName": "DPS-Subject-Access-Requests",
                "createdByUsername": "Robert Bobby III"
              }
              """.trimIndent(),
            ),
        ),
    )
  }

  fun getRequestBodyAsByteArray(): ByteArray {
    assertThat(serveEvents).isNotNull
    assertThat(serveEvents.requests).hasSize(1)
    assertThat(serveEvents.requests[0]).isNotNull
    assertThat(serveEvents.requests[0].request).isNotNull
    assertThat(serveEvents.requests[0].request.body).isNotNull
    return serveEvents.requests[0].request.body
  }

  fun verifyStoreDocumentIsCalled(times: Int, subjectAccessRequestId: String) {
    verify(
      times,
      postRequestedFor(urlPathEqualTo("/documents/SUBJECT_ACCESS_REQUEST_REPORT/$subjectAccessRequestId"))
        .withHeader("Service-Name", equalTo(SERVICE_NAME_HEADER)),
    )
  }

  fun stubUploadFileReturnsInvalidResponseEntity(subjectAccessRequestId: String, expectedFileContent: ByteArray) {
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
            .withBody("[17]"),
        ),
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

  fun stubUploadFileFailsWithStatus(
    subjectAccessRequestId: String,
    status: Int,
  ) {
    stubFor(
      post(
        urlPathEqualTo("/documents/SUBJECT_ACCESS_REQUEST_REPORT/$subjectAccessRequestId"),
      )
        .withHeader("Service-Name", equalTo(SERVICE_NAME_HEADER))
        .withMultipartRequestBody(
          aMultipart()
            .withName("file"),
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
    val fileSize = expectedFileContent.size
    val fileHash = BigInteger(
      1,
      MessageDigest.getInstance("SHA-512")
        .digest(expectedFileContent),
    ).toString(16)

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
            .withBody(
              documentUploadSuccessResponseJson(
                subjectAccessRequestId,
                expectedFileContent.size,
                expectedFileContent,
              ),
            ),
        ),
    )
  }

  fun documentUploadSuccessResponseJson(subjectAccessRequestId: String, fileSize: Int, fileContent: ByteArray) = """
    {
      "documentUuid": "$subjectAccessRequestId",
      "documentType": "Subject Access Request",
      "documentFilename": "Subject Access Request - $subjectAccessRequestId",
      "filename": "Subject Access Request - $subjectAccessRequestId.pdf",
      "fileExtension": "pdf",
      "fileSize": $fileSize,
      "fileHash": "${fileHash(fileContent)}",
      "mimeType": "pdf",
      "metadata": 1,
      "createdTime": "2024-10-29T16:15:58.590Z",
      "createdByServiceName": "$SERVICE_NAME_HEADER",
      "createdByUsername": "Robert Bobby"
    }
  """.trimIndent()

  fun documentUploadSuccessResponseJsonWithMetadata(
    subjectAccessRequestId: String,
    fileSize: Int,
    fileContent: ByteArray,
    metadata: Any?,
  ): String = Gson().toJson(
    DocumentStorageClient.PostDocumentResponse(
      documentUuid = subjectAccessRequestId,
      documentType = "Subject Access Request",
      documentFilename = "Subject Access Request - $subjectAccessRequestId",
      filename = "Subject Access Request - $subjectAccessRequestId.pdf",
      fileExtension = "pdf",
      fileSize = fileSize,
      fileHash = fileHash(fileContent),
      mimeType = "pdf",
      metadata = metadata,
      createdTime = "2024-10-29T16:15:58.590Z",
      createdByServiceName = SERVICE_NAME_HEADER,
      createdByUsername = "Robert Bobby",
    ),
  )

  fun verifyNeverCalled() {
    verify(0, anyRequestedFor(anyUrl()))
  }
}

class DocumentApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val documentApi = DocumentApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext): Unit = documentApi.start()
  override fun beforeEach(context: ExtensionContext): Unit = documentApi.resetAll()
  override fun afterAll(context: ExtensionContext): Unit = documentApi.stop()
}
