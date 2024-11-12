package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.layout.Document
import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.PrisonDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.TemplateHelpers
import java.io.FileOutputStream

class GeneratePdfPersonalLearningPlanServiceTest {
  private val prisonDetailsRepository: PrisonDetailsRepository = mock()
  private val templateHelpers = TemplateHelpers(prisonDetailsRepository)
  private val templateRenderService = TemplateRenderService(templateHelpers)
  private val telemetryClient: TelemetryClient = mock()
  private val generatePdfService = GeneratePdfService(templateRenderService, telemetryClient)

  @Test
  fun `generatePdfService renders for Personal Learning Plan Service`() {
    val serviceList =
      listOf(DpsService(name = "hmpps-education-and-work-plan-api", content = personalLearningPlanServiceData))
    val pdfDocument = PdfDocument(PdfWriter(FileOutputStream("dummy-template-hmpps-education-and-work-plan-api.pdf")))
    val document = Document(pdfDocument)
    generatePdfService.addData(pdfDocument, document, serviceList)
    document.close()

    val reader = PdfDocument(PdfReader("dummy-template-hmpps-education-and-work-plan-api.pdf"))
    val text = PdfTextExtractor.getTextFromPage(reader.getPage(2))

    assertThat(text).contains("Personal Learning Plan")
  }

  private val personalLearningPlanServiceData: Any = mapOf(
    "induction" to mapOf(
      "reference" to "814ade0a-a3b2-46a3-862f-79211ba13f7b",
      "prisonNumber" to "A1234BC",
      "workOnRelease" to mapOf(
        "reference" to "02aaedd2-3376-4a9b-a981-c932161b97b7",
        "hopingToWork" to "YES",
        "affectAbilityToWork" to arrayListOf(
          "CARING_RESPONSIBILITIES",
          "LACKS_CONFIDENCE_OR_MOTIVATION",
          "OTHER",
        ),
        "affectAbilityToWorkOther" to "Test String",
        "createdBy" to "asmith_gen",
        "createdByDisplayName" to "Alex Smith",
        "createdAt" to "2023-06-19T09:39:44Z",
        "createdAtPrison" to "BXI",
        "updatedBy" to "asmith_gen",
        "updatedByDisplayName" to "Alex Smith",
        "updatedAt" to "2023-06-19T09:39:44Z",
        "updatedAtPrison" to "BXI",
      ),
      "previousQualifications" to mapOf(
        "reference" to "199bd202-cb97-461c-ae88-76fb45ac2707",
        "educationLevel" to "FURTHER_EDUCATION_COLLEGE",
        "qualifications" to arrayListOf(
          mapOf(
            "reference" to "2d4ef7f5-cb09-4dbe-b4b9-f6728bd57647",
            "subject" to "Maths GCSE",
            "level" to "LEVEL_2",
            "grade" to "B",
            "createdBy" to "asmith_gen",
            "createdAt" to "2023-06-19T09:39:44Z",
            "updatedBy" to "asmith_gen",
            "updatedAt" to "2023-06-19T09:39:44Z",
          ),
        ),
      ),
      "previousTraining" to mapOf(
        "reference" to "0bea4b4c-aa04-414d-93cc-b190454d6705",
        "trainingTypes" to arrayListOf(
          "FIRST_AID_CERTIFICATE",
          "FULL_UK_DRIVING_LICENCE",
          "OTHER",
        ),
        "trainingTypeOther" to "Test String",
        "createdBy" to "asmith_gen",
        "createdByDisplayName" to "Alex Smith",
        "createdAt" to "2023-06-19T09:39:44Z",
        "createdAtPrison" to "BXI",
        "updatedBy" to "asmith_gen",
        "updatedByDisplayName" to "Alex Smith",
        "updatedAt" to "2023-06-19T09:39:44Z",
        "updatedAtPrison" to "BXI",
      ),
      "previousWorkExperiences" to mapOf(
        "reference" to "7d34d1ea-746c-438e-b34a-ef16adf2eb50",
        "hasWorkedBefore" to "YES",
        "hasWorkedBeforeNotRelevantReason" to "",
        "experiences" to arrayListOf(
          mapOf(
            "experienceType" to "RETAIL",
            "experienceTypeOther" to "Test String",
            "role" to "Shop Assistant",
            "details" to "Some details here",
          ),
        ),
        "createdBy" to "asmith_gen",
        "createdByDisplayName" to "Alex Smith",
        "createdAt" to "2023-06-19T09:39:44Z",
        "createdAtPrison" to "BXI",
        "updatedBy" to "asmith_gen",
        "updatedByDisplayName" to "Alex Smith",
        "updatedAt" to "2023-06-19T09:39:44Z",
        "updatedAtPrison" to "BXI",
      ),
      "inPrisonInterests" to mapOf(
        "reference" to "474c3291-90e1-43ba-b49c-7db1bd5f2946",
        "inPrisonWorkInterests" to arrayListOf(
          mapOf(
            "workType" to "OTHER",
            "workTypeOther" to "Test Work Interest",
          ),
        ),
        "inPrisonTrainingInterests" to arrayListOf(
          mapOf(
            "trainingType" to "OTHER",
            "trainingTypeOther" to "Test Training Interest",
          ),
        ),
        "createdBy" to "asmith_gen",
        "createdByDisplayName" to "Alex Smith",
        "createdAt" to "2023-06-19T09:39:44Z",
        "createdAtPrison" to "BXI",
        "updatedBy" to "asmith_gen",
        "updatedByDisplayName" to "Alex Smith",
        "updatedAt" to "2023-06-19T09:39:44Z",
        "updatedAtPrison" to "BXI",
      ),
      "personalSkillsAndInterests" to mapOf(
        "reference" to "dd356f91-c2b5-442b-bd25-742388533ee4",
        "skills" to arrayListOf(
          mapOf(
            "skillType" to "OTHER",
            "skillTypeOther" to "Test Personal Skill",
          ),
        ),
        "interests" to arrayListOf(
          mapOf(
            "interestType" to "OTHER",
            "interestTypeOther" to "Test Personal Interest",
          ),
        ),
        "createdBy" to "asmith_gen",
        "createdByDisplayName" to "Alex Smith",
        "createdAt" to "2023-06-19T09:39:44Z",
        "createdAtPrison" to "BXI",
        "updatedBy" to "asmith_gen",
        "updatedByDisplayName" to "Alex Smith",
        "updatedAt" to "2023-06-19T09:39:44Z",
        "updatedAtPrison" to "BXI",
      ),
      "futureWorkInterests" to mapOf(
        "reference" to "84d700ab-cdf8-4823-8879-c4f1c2edeece",
        "interests" to arrayListOf(
          mapOf(
            "workType" to "OTHER",
            "workTypeOther" to "Test Work Type",
            "role" to "Test Role",
          ),
        ),
        "createdBy" to "asmith_gen",
        "createdByDisplayName" to "Alex Smith",
        "createdAt" to "2023-06-19T09:39:44Z",
        "createdAtPrison" to "BXI",
        "updatedBy" to "asmith_gen",
        "updatedByDisplayName" to "Alex Smith",
        "updatedAt" to "2023-06-19T09:39:44Z",
        "updatedAtPrison" to "BXI",
      ),
      "createdBy" to "asmith_gen",
      "createdByDisplayName" to "Alex Smith",
      "createdAt" to "2023-06-19T09:39:44Z",
      "createdAtPrison" to "BXI",
      "updatedBy" to "asmith_gen",
      "updatedByDisplayName" to "Alex Smith",
      "updatedAt" to "2023-06-19T09:39:44Z",
      "updatedAtPrison" to "BXI",
    ),
    "goals" to arrayListOf(
      mapOf(
        "goalReference" to "c88a6c48-97e2-4c04-93b5-98619966447b",
        "title" to "Improve communication skills",
        "targetCompletionDate" to "2023-12-19",
        "status" to "ARCHIVED",
        "steps" to arrayListOf(
          mapOf(
            "stepReference" to "d38a6c41-13d1-1d05-13c2-24619966119b",
            "title" to "Book communication skills course",
            "status" to "COMPLETE",
            "sequenceNumber" to 1,
          ),
          mapOf(
            "stepReference" to "3386612b-3d52-40d5-a00d-3f81c547dc08",
            "title" to "Attend communication skills course",
            "status" to "NOT_STARTED",
            "sequenceNumber" to 2,
          ),
        ),
        "notes" to "Pay close attention to Peter's behaviour.",
        "createdBy" to "asmith_gen",
        "createdByDisplayName" to "Alex Smith",
        "createdAt" to "2023-06-19T09:39:44Z",
        "createdAtPrison" to "BXI",
        "updatedBy" to "asmith_gen",
        "updatedByDisplayName" to "Alex Smith",
        "updatedAt" to "2023-06-19T09:39:44Z",
        "updatedAtPrison" to "BXI",
        "archiveReason" to "PRISONER_NO_LONGER_WANTS_TO_WORK_TOWARDS_GOAL",
        "archiveReasonOther" to "Goal archived at user request",
      ),
    ),
  )
}
