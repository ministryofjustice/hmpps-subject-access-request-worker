import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestapi.models.SubjectAccessRequest
import java.time.Duration

@RestController
class SubjectAccessRequestWorkerController() {
  @EventListener(
    ApplicationReadyEvent::class)
  fun startPolling() {
    print("STARTED POLLING")
    val chosenSAR: SubjectAccessRequest = this.pollForNewSubjectAccessRequests()
    val patchResponse = patch('/api-endpoint?id=' + chosenSAR.id.toString())
    if (patchResponse == 200) {
      doReport()
      patch('/api-endpoint?status=completed')
    }
  }
  fun pollForNewSubjectAccessRequests(): SubjectAccessRequest {
    var response: List<SubjectAccessRequest>
    do {
      response = get('/api-endpoint?unclaimed')
      Thread.sleep(Duration.ofSeconds(10))
    } while (response.isEmpty())
    //CHOOSE ONE FROM THE RESPONSE LIST
    return response.first()
  }

  fun doReport() {
    print("Would do report")
  }

}