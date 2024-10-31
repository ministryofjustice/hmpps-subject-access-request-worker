package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService

class GeneratePdfServiceBookASecureMoveTest : BasePdfServiceTest() {

  private val expectedPageCount: Int = 5

  @Test
  fun `PDFService renders Book a Secure Move document when no data is held`() {
    val pdfStream = generatorPdfForServices(
      listOf(DpsService(name = "hmpps-book-secure-move-api", content = null)),
    )

    assertPdfContainsExpectedContent(pdfStream) { pdfDocument ->
      assertThat(pdfDocument.numberOfPages)
        .withFailMessage { "expected 5 page document, actual: ${pdfDocument.numberOfPages}" }
        .isEqualTo(expectedPageCount)

      val page = pdfDocument.getPageText(4)
      page.assertContainsTextOnce("Name: $subjectName")
      page.assertContainsTextOnce("NOMIS ID: $nomisId")
      page.assertContainsTextOnce("Book a secure move")
      page.assertContainsTextOnce("No Data Held")

      assertPageOneContainsExpectedContent(
        pdfDocument,
        listOf("hmpps-book-secure-move-api"),
      )
    }
  }

  @Test
  fun `PDFService renders Book a Secure Move API with data missing`() {
    val pdfStream = generatorPdfForServices(
      listOf(DpsService(name = "hmpps-book-secure-move-api", content = bookAMoveWithDataMissing)),
    )

    assertPdfContainsExpectedContent(pdfStream) { pdfDocument ->
      val page = pdfDocument.getPageText(4)
      page.assertContainsTextOnce("Name: $subjectName")
      page.assertContainsTextOnce("NOMIS ID: $nomisId")
      page.assertContainsTextOnce("Book a secure move")
      page.assertContainsTextOnce("Moves")
      page.assertContainsPattern("Additional information\\s+example Court to Prison prison_remand: Huddersfield Youth Court \nto HMP Isle of Wight")
      page.assertContainsPattern("Title\\s+Location\\s+type\\s+Premise\\s+Locality")
      page.assertContainsPattern("Pentonville\\s+\\(HMP\\)\\s+prison\\s+EitWwGgKEitWwGg\\s+Fazakerley")
    }
  }

  @Test
  fun `PDFService renders Book a Secure Move API with a full data response from the API`() {
    val pdfStream = generatorPdfForServices(
      listOf(DpsService(name = "hmpps-book-secure-move-api", content = bookAMoveFullData)),
    )

    assertPdfContainsExpectedContent(pdfStream) { pdfDocument ->
      val page4 = pdfDocument.getPageText(4)
      page4.assertContainsTextOnce("Name: $subjectName")
      page4.assertContainsTextOnce("NOMIS ID: $nomisId")
      page4.assertContainsTextOnce("Book a secure move")
      page4.assertContainsTextOnce("Moves")
      page4.assertContainsPattern("Additional information\\s+example Court to Prison prison_remand: Huddersfield Youth Court \nto HMP Isle of Wight")
      page4.assertContainsPattern("Title\\s+Location\\s+type\\s+Premise\\s+Locality")
      page4.assertContainsPattern("Pentonville\\s+\\(HMP\\)\\s+prison\\s+EitWwGgKEitWwGg\\s+Fazakerley")

      val page5 = pdfDocument.getPageText(5)
      page5.assertContainsPattern("Plymouth Crown Court\\s+court\\s+No Data Held\\s+No Data Held")
      page5.assertContainsPattern("Vehicle registration\\s+AB12 CDE")
    }
  }

  companion object {
    val bookAMoveWithDataMissing = arrayListOf(
      mapOf(
        "data" to mapOf(
          "id" to "839efad1-fe68-4faf-9d15-b2264a3d3d5b",
          "type" to "people",
          "attributes" to mapOf(
            "first_names" to "Tyson",
            "last_name" to "Kertzmann",
            "date_of_birth" to "2002-11-13",
            "assessment_answers" to arrayListOf(
              mapOf(
                "title" to "Violent",
                "comments" to "does not like marmite",
                "created_at" to "2022-08-26",
                "expires_at" to "2023-08-26",
                "assessment_question_id" to "af8cfc67-757c-4019-9d5e-618017de1617",
                "category" to "risk",
                "key" to "self_harm",
                "nomis_alert_code" to "HA2",
                "nomis_alert_type" to "H",
                "nomis_alert_description" to "ACCT Closed (HMPS)",
                "nomis_alert_type_description" to "Self Harm",
                "imported_from_nomis" to true,
              ),
            ),
            "identifiers" to arrayListOf(
              mapOf(
                "value" to "95/71070V",
                "identifier_type" to "police_national_computer",
              ),
              mapOf(
                "value" to "A1234AA",
                "identifier_type" to "prison_number",
              ),
            ),
            "gender_additional_information" to "example",
          ),
          "relationships" to mapOf(
            "ethnicity" to mapOf(
              "data" to mapOf(
                "id" to "f480eeff-848c-4fcc-bd75-9f61850fa5bf",
                "type" to "ethnicities",
                "attributes" to mapOf(
                  "key" to "m3",
                  "title" to "Mixed: White and Asian",
                  "description" to null,
                  "nomis_code" to "M3",
                  "disabled_at" to null,
                ),
              ),
            ),
            "gender" to mapOf(
              "data" to mapOf(
                "id" to "ffac6763-26d6-4425-8005-6e5d052aed88",
                "type" to "genders",
                "attributes" to mapOf(
                  "key" to "male",
                  "title" to "Male",
                  "description" to null,
                  "disabled_at" to null,
                  "nomis_code" to null,
                ),
              ),
            ),
            "profiles" to arrayListOf(
              mapOf(
                "data" to mapOf(
                  "id" to "1a0cf160-0fd0-4d62-a6c8-a0719730d78c",
                  "type" to "profiles",
                  "attributes" to mapOf(
                    "requires_youth_risk_assessment" to false,
                    "assessment_answers" to arrayListOf(
                      mapOf(
                        "title" to "Must be held separately",
                        "comments" to "MLKZIyaqMLKZIya",
                        "created_at" to "2016-03-02",
                        "expires_at" to "2019-09-11",
                        "assessment_question_id" to "8f38efb0-36c1-4a56-8c66-3b72c9525f92",
                        "category" to "risk",
                        "key" to "hold_separately",
                        "nomis_alert_code" to "CPC",
                        "nomis_alert_type" to "C",
                        "nomis_alert_description" to "PPRC",
                        "nomis_alert_type_description" to "Child Communication Measures",
                        "imported_from_nomis" to true,
                      ),
                    ),
                  ),
                  "relationships" to mapOf(
                    "person" to mapOf(
                      "data" to mapOf(
                        "id" to "839efad1-fe68-4faf-9d15-b2264a3d3d5b",
                        "type" to "people",
                      ),
                    ),
                    "documents" to emptyList<Any?>(),
                    "person_escort_record" to mapOf(
                      "data" to null,
                    ),
                    "youth_risk_assessment" to mapOf(
                      "data" to null,
                    ),
                  ),
                ),
              ),
            ),
            "moves" to arrayListOf(
              mapOf(
                "data" to mapOf(
                  "id" to "033ac336-2af9-4772-81ae-5c7028bbf4dc",
                  "type" to "moves",
                  "attributes" to mapOf(
                    "reference" to "EJM9267N",
                    "status" to "completed",
                    "updated_at" to "2022-08-26T08:01:17+01:00",
                    "created_at" to "2022-08-26T08:01:15+01:00",
                    "time_due" to "2022-08-26T08:01:15+01:00",
                    "date" to "2022-08-26",
                    "move_type" to "prison_remand",
                    "nomis_event_id" to null,
                    "additional_information" to "example Court to Prison prison_remand: Huddersfield Youth Court to HMP Isle of Wight",
                    "rejection_reason" to null,
                    "cancellation_reason" to null,
                    "cancellation_reason_comment" to null,
                    "move_agreed" to null,
                    "move_agreed_by" to null,
                    "date_from" to null,
                    "date_to" to null,
                  ),
                  "relationships" to mapOf(
                    "person" to mapOf(
                      "data" to mapOf(
                        "id" to "839efad1-fe68-4faf-9d15-b2264a3d3d5b",
                        "type" to "people",
                      ),
                    ),
                    "profile" to mapOf(
                      "data" to mapOf(
                        "id" to "839efad1-fe68-4faf-9d15-b2264a3d3d5b",
                        "type" to "profiles",
                      ),
                    ),
                    "from_location" to mapOf(
                      "data" to mapOf(
                        "id" to "839efad1-fe68-4faf-9d15-b2264a3d3d5b",
                        "type" to "locations",
                        "attributes" to mapOf(
                          "key" to "pvi",
                          "title" to "Pentonville (HMP)",
                          "location_type" to "prison",
                          "nomis_agency_id" to "PVI",
                          "can_upload_documents" to false,
                          "young_offender_institution" to false,
                          "premise" to "EitWwGgKEitWwGg",
                          "locality" to "Fazakerley",
                          "city" to "London",
                          "country" to "England",
                          "postcode" to "Q6 6CC",
                          "latitude" to 51.545004,
                          "longitude" to -0.115884,
                          "created_at" to "2019-07-22T15:58:39+01:00",
                          "disabled_at" to "2017-07-21T17:32:28Z",
                          "extradition_capable" to false,
                        ),
                        "relationships" to mapOf(
                          "suppliers" to mapOf(
                            "data" to emptyList<Any>(),
                          ),
                        ),
                      ),
                    ),
                    "to_location" to mapOf(
                      "data" to mapOf(
                        "id" to "839efad1-fe68-4faf-9d15-b2264a3d3d5b",
                        "type" to "locations",
                        "attributes" to mapOf(
                          "key" to "nmi",
                          "title" to "NOTTINGHAM (HMP)",
                          "location_type" to "prison",
                          "nomis_agency_id" to "NMI",
                          "can_upload_documents" to false,
                          "young_offender_institution" to false,
                          "premise" to "EitWwGgKEitWwGg",
                          "locality" to "Fazakerley",
                          "city" to "Nottingham",
                          "country" to "England",
                          "postcode" to "Q6 6CC",
                          "latitude" to 51.545004,
                          "longitude" to -0.115884,
                          "created_at" to "2019-07-22T15:58:39+01:00",
                          "disabled_at" to "2017-07-21T17:32:28Z",
                          "extradition_capable" to false,
                        ),
                        "relationships" to mapOf(
                          "suppliers" to mapOf(
                            "data" to arrayListOf(
                              mapOf(
                                "id" to "3ef88a47-6f1f-5b9b-b2fc-c0fe42cb0c92",
                                "type" to "suppliers",
                              ),
                            ),
                          ),
                        ),
                      ),
                    ),
                    "prison_transfer_reason" to mapOf(
                      "data" to mapOf(
                        "id" to "839efad1-fe68-4faf-9d15-b2264a3d3d5b",
                        "type" to "prison_transfer_reasons",
                        "attributes" to mapOf(
                          "key" to "example",
                          "title" to "example",
                          "disabled_at" to "2017-07-21T17:32:28Z",
                        ),
                      ),
                    ),
                    "documents" to mapOf(
                      "data" to arrayListOf(
                        mapOf(
                          "id" to "3ef88a47-6f1f-5b9b-b2fc-c0fe42cb0c92",
                          "type" to "documents",
                        ),
                      ),
                    ),
                    "court_hearings" to arrayListOf(
                      mapOf(
                        "data" to mapOf(
                          "id" to "b0bd7197-192a-446b-981a-35c551e09075",
                          "type" to "court_hearings",
                          "attributes" to mapOf(
                            "start_time" to "2020-10-20T08:00:00+01:00",
                            "case_start_date" to "2015-11-27",
                            "nomis_case_id" to 1343069,
                            "case_number" to "T32423423423",
                            "nomis_hearing_id" to 4232424,
                            "case_type" to "Adult",
                            "comments" to "example",
                            "saved_to_nomis" to false,
                          ),
                          "relationships" to mapOf(
                            "move" to mapOf(
                              "data" to mapOf(
                                "id" to "f3f4895d-7946-470f-af75-990481343ed2",
                                "type" to "moves",
                              ),
                            ),
                          ),
                        ),
                      ),
                    ),
                    "allocation" to mapOf(
                      "data" to mapOf(
                        "id" to "f3f4895d-7946-470f-af75-990481343ed2",
                        "type" to "allocations",
                      ),
                    ),
                  ),
                  "journeys" to arrayListOf(
                    mapOf(
                      "data" to mapOf(
                        "id" to "b0bd7197-192a-446b-981a-35c551e09075",
                        "type" to "journeys",
                        "attributes" to mapOf(
                          "state" to "completed",
                          "billable" to true,
                          "vehicle" to mapOf(
                            "id" to "12345678ABC",
                            "registration" to "AB12 CDE",
                          ),
                          "date" to "2022-08-26",
                          "number" to 1,
                          "timestamp" to "2022-08-26T08:01:16+01:00",
                        ),
                        "relationships" to mapOf(
                          "from_location" to mapOf(
                            "data" to mapOf(
                              "id" to "b0bd7197-192a-446b-981a-35c551e09075",
                              "type" to "locations",
                              "attributes" to mapOf(
                                "key" to "plymcc",
                                "title" to "Plymouth Crown Court",
                                "location_type" to "court",
                                "nomis_agency_id" to "PLYMCC",
                                "can_upload_documents" to false,
                                "young_offender_institution" to false,
                                "premise" to null,
                                "locality" to null,
                                "city" to null,
                                "country" to null,
                                "postcode" to "PL1 2ER",
                                "latitude" to 50.369194,
                                "longitude" to -4.141694,
                                "created_at" to "2019-07-22T15:58:38+01:00",
                                "disabled_at" to null,
                                "extradition_capable" to null,
                              ),
                              "relationships" to mapOf(
                                "suppliers" to mapOf(
                                  "data" to arrayListOf(
                                    mapOf(
                                      "id" to "bccf4d77-984d-560c-9ffd-9badbb9157ca",
                                      "type" to "suppliers",
                                    ),
                                  ),
                                ),
                              ),
                            ),
                          ),
                        ),
                        "to_location" to mapOf(
                          "data" to mapOf(
                            "id" to "b0bd7197-192a-446b-981a-35c551e09075",
                            "type" to "locations",
                            "attributes" to mapOf(
                              "key" to "plymcc",
                              "title" to "Plymouth Crown Court",
                              "location_type" to "court",
                              "nomis_agency_id" to "PLYMCC",
                              "can_upload_documents" to false,
                              "young_offender_institution" to false,
                              "premise" to null,
                              "locality" to null,
                              "city" to null,
                              "country" to null,
                              "postcode" to "PL1 2ER",
                              "latitude" to 50.369194,
                              "longitude" to -4.141694,
                              "created_at" to "2019-07-22T15:58:38+01:00",
                              "disabled_at" to null,
                              "extradition_capable" to null,
                            ),
                            "relationships" to mapOf(
                              "suppliers" to mapOf(
                                "data" to arrayListOf(
                                  mapOf(
                                    "id" to "bccf4d77-984d-560c-9ffd-9badbb9157ca",
                                    "type" to "suppliers",
                                  ),
                                ),
                              ),
                            ),
                          ),
                        ),
                        "events" to arrayListOf(
                          mapOf(
                            "data" to mapOf(
                              "id" to "b0bd7197-192a-446b-981a-35c551e09075",
                              "type" to "events",
                              "attributes" to mapOf(
                                "event_type" to "JourneyComplete",
                                "classification" to "default",
                                "occurred_at" to "2022-08-26T10:01:16+01:00",
                                "recorded_at" to "2022-08-26T10:01:16+01:00",
                                "notes" to "example note: lorem ipsum dolor sit amet",
                                "created_by" to "Serco",
                                "details" to mapOf(
                                  "vehicle_reg" to "AB12 CDE",
                                ),
                              ),
                              "relationships" to mapOf(
                                "eventable" to mapOf(
                                  "data" to mapOf(
                                    "id" to "f89c7600-6d35-4d40-9527-e34ddfb36d7f",
                                    "type" to "journeys",
                                  ),
                                ),
                                "supplier" to mapOf(
                                  "data" to mapOf(
                                    "id" to "f89c7600-6d35-4d40-9527-e34ddfb36d7f",
                                    "type" to "suppliers",
                                  ),
                                ),
                              ),
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),
                  "events" to arrayListOf(
                    mapOf(
                      "data" to mapOf(
                        "id" to "f89c7600-6d35-4d40-9527-e34ddfb36d7f",
                        "type" to "events",
                        "attributes" to mapOf(
                          "event_type" to "MoveComplete",
                          "classification" to "default",
                          "occurred_at" to "2022-08-26T10:01:16+01:00",
                          "recorded_at" to "2022-08-26T10:01:16+01:00",
                          "notes" to "example",
                          "created_by" to "Serco",
                          "details" to mapOf(
                            "vehicle_reg" to "AB12 CDE",
                          ),
                        ),
                        "relationships" to mapOf(
                          "eventable" to mapOf(
                            "data" to mapOf(
                              "id" to "f89c7600-6d35-4d40-9527-e34ddfb36d7f",
                              "type" to "moves",
                            ),
                          ),
                          "supplier" to mapOf(
                            "data" to mapOf(
                              "id" to "f89c7600-6d35-4d40-9527-e34ddfb36d7f",
                              "type" to "suppliers",
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
          "image" to mapOf(
            "data" to mapOf(
              "id" to "f89c7600-6d35-4d40-9527-e34ddfb36d7f",
              "type" to "images",
              "attributes" to mapOf(
                "url" to "https://cloud-platform-6ac35f1c447e16364485ad5fe3dfdc36.s3.eu-west-2.amazonaws.com/lctdeg50q4753ben5wnoeg2297e7?response-content-disposition=inline%3B%20filename%3D%22ee3a7123-3bdc-441a-95a8-057e415ccaad.jpg%22%3B%20filename%2A%3DUTF-8%27%27ee3a7123-3bdc-441a-95a8-057e415ccaad.jpg\u0026response-content-type=image%2Fjpeg\u0026X-Amz-Algorithm=AWS4-HMAC-SHA256\u0026X-Amz-Credential=ASIA27HJSWAHOZGUKW5B%2F20240905%2Feu-west-2%2Fs3%2Faws4_request\u0026X-Amz-Date=20240905T080408Z\u0026X-Amz-Expires=300\u0026X-Amz-Security-Token=IQoJb3JpZ2luX2VjEND%2F%2F%2F%2F%2F%2F%2F%2F%2F%2FwEaCWV1LXdlc3QtMiJIMEYCIQCgUSWskf9xUfRzU1vKJvd%2B2mK8S%2FwDp%2BiSIduD1f%2FCUAIhAOeYQ4JXINBLfVPc5BBl1SFB3ySiIdEOGnLRw2jpHSH7KsEFCOn%2F%2F%2F%2F%2F%2F%2F%2F%2F%2FwEQBBoMNzU0MjU2NjIxNTgyIgz0FkY0QhJugZZjVJgqlQVyUfTo%2B5rScU2XFDz3RC9Ehp2qNDbKhsS83dDSDUy%2BvCnENMip1oM8PPgIoIHbgmYYTv7eR%2BDoanmHNVNv1x1wHBTOPuvUJCg0H2p9hgoRNHXFzn51nxKLm0fJkdPU4JVcli7ZxjDkhvQiJv62xx9DDk4vbz0UJsnATmsYa0PjYXgJxSxwr6VYnw%2FspzKIfYXPojc5tATmLRPBaJuTNSHNMpjEbgvyf9LVtaHgccgknHn8DWEeDwgOxFa0nWK0D%2BRD528dATQCFKEX8s6vHc2T69vb66dfVVLI2G%2F%2FVpyqqIPor6gy4pAqbEOfN4jGTViuMB9rd9ivwEjbipqrp9MwWMWrAnkKEJURyy1pqOaTKOoqie6A3c7%2BroX%2BBzqbKxtAEuloBwW7v7iHCn5vKcyx4ig3m7RdZmieNVhMUa9F2u4wyu1dtDiYXT2mGMsUHcbUbPMeoKKpsML10rrrnbmbR3j9NYcSH%2BAH7zJ2i0gdf3FFtw1ZV2R%2BxIfSvuZ3oQ55YlVD9Ouh%2BY0EJH064KW2IJ3No720XSG93XHfW5OyJuh%2B%2B1pn5mL4e9IUy8Xl59Qm18z8NuRdTnx2%2FJtBomw7o83AcCV6e5cFk8AUStu%2FA850tO0lq1piIRmyhEnGsfR1oJKeYrWLdk55dnH6V69wCIjsfHYZqDBPczDoDcdydV7q3olSN6hp5It8XA%2BYlOPIGjaaldw%2BEOuhZBVnDJRkjllEPn390xmwUT4Lg8UVEk3T38NM7OT8GqXTaC36LzkLxN6Run%2FW5hGH6NYyxFeo6SXSLit6lN25ukTm5ApjltN592wjrG2SdxgcF%2Bior61J4j1Io9a8zGA6H2yqJGLAULa3m4fKi%2FEA9VIgsuTiA5gjNr1uMPjL5bYGOpoBYURBLLW5WDItyPh15PJ8Vnl7le0lBlsAmF4HikwNgaDGhRLyBR5PtfiQZruNatspcsQ2fPqNG29ggC2zl7RVlAEDiHJLfdQS57vCge9QbLFdHyOztSoEqgdvKYApVme2dC36sXEZOBXSQmj1BXBUsjOUMLmjfxqB8eKzDEZ3P8W0Zw%2BeBbh2t19%2Bmth6Q%2BpWXRVq9Hd7y8HmBA%3D%3D\u0026X-Amz-SignedHeaders=host\u0026X-Amz-Signature=dbc5d0fcb619d221ee80f9c16a0a83267e1d2277d576a0512d501d5934983146",
              ),
            ),
          ),
        ),
      ),
    )

    val bookAMoveFullData = arrayListOf(
      mapOf(
        "data" to mapOf(
          "id" to "839efad1-fe68-4faf-9d15-b2264a3d3d5b",
          "type" to "people",
          "attributes" to mapOf(
            "first_names" to "Tyson",
            "last_name" to "Kertzmann",
            "date_of_birth" to "2002-11-13",
            "assessment_answers" to arrayListOf(
              mapOf(
                "title" to "Violent",
                "comments" to "does not like marmite",
                "created_at" to "2022-08-26",
                "expires_at" to "2023-08-26",
                "assessment_question_id" to "af8cfc67-757c-4019-9d5e-618017de1617",
                "category" to "risk",
                "key" to "self_harm",
                "nomis_alert_code" to "HA2",
                "nomis_alert_type" to "H",
                "nomis_alert_description" to "ACCT Closed (HMPS)",
                "nomis_alert_type_description" to "Self Harm",
                "imported_from_nomis" to true,
              ),
            ),
            "identifiers" to arrayListOf(
              mapOf(
                "value" to "95/71070V",
                "identifier_type" to "police_national_computer",
              ),
              mapOf(
                "value" to "A1234AA",
                "identifier_type" to "prison_number",
              ),
            ),
            "gender_additional_information" to "example",
          ),
          "relationships" to mapOf(
            "ethnicity" to mapOf(
              "data" to mapOf(
                "id" to "f480eeff-848c-4fcc-bd75-9f61850fa5bf",
                "type" to "ethnicities",
                "attributes" to mapOf(
                  "key" to "m3",
                  "title" to "Mixed: White and Asian",
                  "description" to null,
                  "nomis_code" to "M3",
                  "disabled_at" to null,
                ),
              ),
            ),
            "gender" to mapOf(
              "data" to mapOf(
                "id" to "ffac6763-26d6-4425-8005-6e5d052aed88",
                "type" to "genders",
                "attributes" to mapOf(
                  "key" to "male",
                  "title" to "Male",
                  "description" to "example",
                  "disabled_at" to "2017-07-21T17:32:28Z",
                  "nomis_code" to "M",
                ),
              ),
            ),
            "profiles" to arrayListOf(
              mapOf(
                "data" to mapOf(
                  "id" to "1a0cf160-0fd0-4d62-a6c8-a0719730d78c",
                  "type" to "profiles",
                  "attributes" to mapOf(
                    "requires_youth_risk_assessment" to false,
                    "assessment_answers" to arrayListOf(
                      mapOf(
                        "title" to "Must be held separately",
                        "comments" to "MLKZIyaqMLKZIya",
                        "created_at" to "2016-03-02",
                        "expires_at" to "2019-09-11",
                        "assessment_question_id" to "8f38efb0-36c1-4a56-8c66-3b72c9525f92",
                        "category" to "risk",
                        "key" to "hold_separately",
                        "nomis_alert_code" to "CPC",
                        "nomis_alert_type" to "C",
                        "nomis_alert_description" to "PPRC",
                        "nomis_alert_type_description" to "Child Communication Measures",
                        "imported_from_nomis" to true,
                      ),
                    ),
                  ),
                  "relationships" to mapOf(
                    "person" to mapOf(
                      "data" to mapOf(
                        "id" to "839efad1-fe68-4faf-9d15-b2264a3d3d5b",
                        "type" to "people",
                      ),
                    ),
                    "documents" to arrayListOf(
                      mapOf(
                        "data" to mapOf(
                          "id" to "ee3a7123-3bdc-441a-95a8-057e415ccaad",
                          "type" to "documents",
                        ),
                      ),
                    ),
                    "person_escort_record" to mapOf(
                      "data" to mapOf(
                        "id" to "ea5ace8e-e9ad-4ca3-9977-9bf69e3b6154",
                        "type" to "person_escort_records",
                      ),
                    ),
                    "youth_risk_assessment" to mapOf(
                      "data" to mapOf(
                        "id" to "ea5ace8e-e9ad-4ca3-9977-9bf69e3b6154",
                        "type" to "youth_risk_assessments",
                      ),
                    ),
                  ),
                ),
              ),
            ),
            "moves" to arrayListOf(
              mapOf(
                "data" to mapOf(
                  "id" to "033ac336-2af9-4772-81ae-5c7028bbf4dc",
                  "type" to "moves",
                  "attributes" to mapOf(
                    "reference" to "EJM9267N",
                    "status" to "completed",
                    "updated_at" to "2022-08-26T08:01:17+01:00",
                    "created_at" to "2022-08-26T08:01:15+01:00",
                    "time_due" to "2022-08-26T08:01:15+01:00",
                    "date" to "2022-08-26",
                    "move_type" to "prison_remand",
                    "nomis_event_id" to null,
                    "additional_information" to "example Court to Prison prison_remand: Huddersfield Youth Court to HMP Isle of Wight",
                    "rejection_reason" to "example",
                    "cancellation_reason" to "made_in_error",
                    "cancellation_reason_comment" to "example",
                    "move_agreed" to true,
                    "move_agreed_by" to "John Doe",
                    "date_from" to "2020-05-17",
                    "date_to" to "2020-05-17",
                  ),
                  "relationships" to mapOf(
                    "person" to mapOf(
                      "data" to mapOf(
                        "id" to "839efad1-fe68-4faf-9d15-b2264a3d3d5b",
                        "type" to "people",
                      ),
                    ),
                    "profile" to mapOf(
                      "data" to mapOf(
                        "id" to "839efad1-fe68-4faf-9d15-b2264a3d3d5b",
                        "type" to "profiles",
                      ),
                    ),
                    "from_location" to mapOf(
                      "data" to mapOf(
                        "id" to "839efad1-fe68-4faf-9d15-b2264a3d3d5b",
                        "type" to "locations",
                        "attributes" to mapOf(
                          "key" to "pvi",
                          "title" to "Pentonville (HMP)",
                          "location_type" to "prison",
                          "nomis_agency_id" to "PVI",
                          "can_upload_documents" to false,
                          "young_offender_institution" to false,
                          "premise" to "EitWwGgKEitWwGg",
                          "locality" to "Fazakerley",
                          "city" to "London",
                          "country" to "England",
                          "postcode" to "Q6 6CC",
                          "latitude" to 51.545004,
                          "longitude" to -0.115884,
                          "created_at" to "2019-07-22T15:58:39+01:00",
                          "disabled_at" to "2017-07-21T17:32:28Z",
                          "extradition_capable" to false,
                        ),
                        "relationships" to mapOf(
                          "suppliers" to mapOf(
                            "data" to arrayListOf(
                              mapOf(
                                "id" to "3ef88a47-6f1f-5b9b-b2fc-c0fe42cb0c92",
                                "type" to "suppliers",
                              ),
                            ),
                          ),
                        ),
                      ),
                    ),
                    "to_location" to mapOf(
                      "data" to mapOf(
                        "id" to "839efad1-fe68-4faf-9d15-b2264a3d3d5b",
                        "type" to "locations",
                        "attributes" to mapOf(
                          "key" to "nmi",
                          "title" to "NOTTINGHAM (HMP)",
                          "location_type" to "prison",
                          "nomis_agency_id" to "NMI",
                          "can_upload_documents" to false,
                          "young_offender_institution" to false,
                          "premise" to "EitWwGgKEitWwGg",
                          "locality" to "Fazakerley",
                          "city" to "Nottingham",
                          "country" to "England",
                          "postcode" to "Q6 6CC",
                          "latitude" to 51.545004,
                          "longitude" to -0.115884,
                          "created_at" to "2019-07-22T15:58:39+01:00",
                          "disabled_at" to "2017-07-21T17:32:28Z",
                          "extradition_capable" to false,
                        ),
                        "relationships" to mapOf(
                          "suppliers" to mapOf(
                            "data" to arrayListOf(
                              mapOf(
                                "id" to "3ef88a47-6f1f-5b9b-b2fc-c0fe42cb0c92",
                                "type" to "suppliers",
                              ),
                            ),
                          ),
                        ),
                      ),
                    ),
                    "prison_transfer_reason" to mapOf(
                      "data" to mapOf(
                        "id" to "839efad1-fe68-4faf-9d15-b2264a3d3d5b",
                        "type" to "prison_transfer_reasons",
                        "attributes" to mapOf(
                          "key" to "example",
                          "title" to "example",
                          "disabled_at" to "2017-07-21T17:32:28Z",
                        ),
                      ),
                    ),
                    "documents" to mapOf(
                      "data" to arrayListOf(
                        mapOf(
                          "id" to "3ef88a47-6f1f-5b9b-b2fc-c0fe42cb0c92",
                          "type" to "documents",
                        ),
                      ),
                    ),
                    "court_hearings" to arrayListOf(
                      mapOf(
                        "data" to mapOf(
                          "id" to "b0bd7197-192a-446b-981a-35c551e09075",
                          "type" to "court_hearings",
                          "attributes" to mapOf(
                            "start_time" to "2020-10-20T08:00:00+01:00",
                            "case_start_date" to "2015-11-27",
                            "nomis_case_id" to 1343069,
                            "case_number" to "T32423423423",
                            "nomis_hearing_id" to 4232424,
                            "case_type" to "Adult",
                            "comments" to "example",
                            "saved_to_nomis" to false,
                          ),
                          "relationships" to mapOf(
                            "move" to mapOf(
                              "data" to mapOf(
                                "id" to "f3f4895d-7946-470f-af75-990481343ed2",
                                "type" to "moves",
                              ),
                            ),
                          ),
                        ),
                      ),
                    ),
                    "allocation" to mapOf(
                      "data" to mapOf(
                        "id" to "f3f4895d-7946-470f-af75-990481343ed2",
                        "type" to "allocations",
                      ),
                    ),
                  ),
//                        "original_move" to mapOf( // Excluded - could not get clarity on this data object
//                          "data" to null
//                        ),
                  "journeys" to arrayListOf(
                    mapOf(
                      "data" to mapOf(
                        "id" to "b0bd7197-192a-446b-981a-35c551e09075",
                        "type" to "journeys",
                        "attributes" to mapOf(
                          "state" to "completed",
                          "billable" to true,
                          "vehicle" to mapOf(
                            "id" to "12345678ABC",
                            "registration" to "AB12 CDE",
                          ),
                          "date" to "2022-08-26",
                          "number" to 1,
                          "timestamp" to "2022-08-26T08:01:16+01:00",
                        ),
                        "relationships" to mapOf(
                          "from_location" to mapOf(
                            "data" to mapOf(
                              "id" to "b0bd7197-192a-446b-981a-35c551e09075",
                              "type" to "locations",
                              "attributes" to mapOf(
                                "key" to "plymcc",
                                "title" to "Plymouth Crown Court",
                                "location_type" to "court",
                                "nomis_agency_id" to "PLYMCC",
                                "can_upload_documents" to false,
                                "young_offender_institution" to false,
                                "premise" to null,
                                "locality" to null,
                                "city" to null,
                                "country" to null,
                                "postcode" to "PL1 2ER",
                                "latitude" to 50.369194,
                                "longitude" to -4.141694,
                                "created_at" to "2019-07-22T15:58:38+01:00",
                                "disabled_at" to null,
                                "extradition_capable" to null,
                              ),
                              "relationships" to mapOf(
                                "suppliers" to mapOf(
                                  "data" to arrayListOf(
                                    mapOf(
                                      "id" to "bccf4d77-984d-560c-9ffd-9badbb9157ca",
                                      "type" to "suppliers",
                                    ),
                                  ),
                                ),
                              ),
                            ),
                          ),
                        ),
                        "to_location" to mapOf(
                          "data" to mapOf(
                            "id" to "b0bd7197-192a-446b-981a-35c551e09075",
                            "type" to "locations",
                            "attributes" to mapOf(
                              "key" to "plymcc",
                              "title" to "Plymouth Crown Court",
                              "location_type" to "court",
                              "nomis_agency_id" to "PLYMCC",
                              "can_upload_documents" to false,
                              "young_offender_institution" to false,
                              "premise" to null,
                              "locality" to null,
                              "city" to null,
                              "country" to null,
                              "postcode" to "PL1 2ER",
                              "latitude" to 50.369194,
                              "longitude" to -4.141694,
                              "created_at" to "2019-07-22T15:58:38+01:00",
                              "disabled_at" to null,
                              "extradition_capable" to null,
                            ),
                            "relationships" to mapOf(
                              "suppliers" to mapOf(
                                "data" to arrayListOf(
                                  mapOf(
                                    "id" to "bccf4d77-984d-560c-9ffd-9badbb9157ca",
                                    "type" to "suppliers",
                                  ),
                                ),
                              ),
                            ),
                          ),
                        ),
                        "events" to arrayListOf(
                          mapOf(
                            "data" to mapOf(
                              "id" to "b0bd7197-192a-446b-981a-35c551e09075",
                              "type" to "events",
                              "attributes" to mapOf(
                                "event_type" to "JourneyComplete",
                                "classification" to "default",
                                "occurred_at" to "2022-08-26T10:01:16+01:00",
                                "recorded_at" to "2022-08-26T10:01:16+01:00",
                                "notes" to "example note: lorem ipsum dolor sit amet",
                                "created_by" to "Serco",
                                "details" to mapOf(
                                  "vehicle_reg" to "AB12 CDE",
                                ),
                              ),
                              "relationships" to mapOf(
                                "eventable" to mapOf(
                                  "data" to mapOf(
                                    "id" to "f89c7600-6d35-4d40-9527-e34ddfb36d7f",
                                    "type" to "journeys",
                                  ),
                                ),
                                "supplier" to mapOf(
                                  "data" to mapOf(
                                    "id" to "f89c7600-6d35-4d40-9527-e34ddfb36d7f",
                                    "type" to "suppliers",
                                  ),
                                ),
                              ),
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),
                  "events" to arrayListOf(
                    mapOf(
                      "data" to mapOf(
                        "id" to "f89c7600-6d35-4d40-9527-e34ddfb36d7f",
                        "type" to "events",
                        "attributes" to mapOf(
                          "event_type" to "MoveComplete",
                          "classification" to "default",
                          "occurred_at" to "2022-08-26T10:01:16+01:00",
                          "recorded_at" to "2022-08-26T10:01:16+01:00",
                          "notes" to "example",
                          "created_by" to "Serco",
                          "details" to mapOf(
                            "vehicle_reg" to "AB12 CDE",
                          ),
                        ),
                        "relationships" to mapOf(
                          "eventable" to mapOf(
                            "data" to mapOf(
                              "id" to "f89c7600-6d35-4d40-9527-e34ddfb36d7f",
                              "type" to "moves",
                            ),
                          ),
                          "supplier" to mapOf(
                            "data" to mapOf(
                              "id" to "f89c7600-6d35-4d40-9527-e34ddfb36d7f",
                              "type" to "suppliers",
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
          "image" to mapOf(
            "data" to mapOf(
              "id" to "f89c7600-6d35-4d40-9527-e34ddfb36d7f",
              "type" to "images",
              "attributes" to mapOf(
                "url" to "https://cloud-platform-6ac35f1c447e16364485ad5fe3dfdc36.s3.eu-west-2.amazonaws.com/lctdeg50q4753ben5wnoeg2297e7?response-content-disposition=inline%3B%20filename%3D%22ee3a7123-3bdc-441a-95a8-057e415ccaad.jpg%22%3B%20filename%2A%3DUTF-8%27%27ee3a7123-3bdc-441a-95a8-057e415ccaad.jpg\u0026response-content-type=image%2Fjpeg\u0026X-Amz-Algorithm=AWS4-HMAC-SHA256\u0026X-Amz-Credential=ASIA27HJSWAHOZGUKW5B%2F20240905%2Feu-west-2%2Fs3%2Faws4_request\u0026X-Amz-Date=20240905T080408Z\u0026X-Amz-Expires=300\u0026X-Amz-Security-Token=IQoJb3JpZ2luX2VjEND%2F%2F%2F%2F%2F%2F%2F%2F%2F%2FwEaCWV1LXdlc3QtMiJIMEYCIQCgUSWskf9xUfRzU1vKJvd%2B2mK8S%2FwDp%2BiSIduD1f%2FCUAIhAOeYQ4JXINBLfVPc5BBl1SFB3ySiIdEOGnLRw2jpHSH7KsEFCOn%2F%2F%2F%2F%2F%2F%2F%2F%2F%2FwEQBBoMNzU0MjU2NjIxNTgyIgz0FkY0QhJugZZjVJgqlQVyUfTo%2B5rScU2XFDz3RC9Ehp2qNDbKhsS83dDSDUy%2BvCnENMip1oM8PPgIoIHbgmYYTv7eR%2BDoanmHNVNv1x1wHBTOPuvUJCg0H2p9hgoRNHXFzn51nxKLm0fJkdPU4JVcli7ZxjDkhvQiJv62xx9DDk4vbz0UJsnATmsYa0PjYXgJxSxwr6VYnw%2FspzKIfYXPojc5tATmLRPBaJuTNSHNMpjEbgvyf9LVtaHgccgknHn8DWEeDwgOxFa0nWK0D%2BRD528dATQCFKEX8s6vHc2T69vb66dfVVLI2G%2F%2FVpyqqIPor6gy4pAqbEOfN4jGTViuMB9rd9ivwEjbipqrp9MwWMWrAnkKEJURyy1pqOaTKOoqie6A3c7%2BroX%2BBzqbKxtAEuloBwW7v7iHCn5vKcyx4ig3m7RdZmieNVhMUa9F2u4wyu1dtDiYXT2mGMsUHcbUbPMeoKKpsML10rrrnbmbR3j9NYcSH%2BAH7zJ2i0gdf3FFtw1ZV2R%2BxIfSvuZ3oQ55YlVD9Ouh%2BY0EJH064KW2IJ3No720XSG93XHfW5OyJuh%2B%2B1pn5mL4e9IUy8Xl59Qm18z8NuRdTnx2%2FJtBomw7o83AcCV6e5cFk8AUStu%2FA850tO0lq1piIRmyhEnGsfR1oJKeYrWLdk55dnH6V69wCIjsfHYZqDBPczDoDcdydV7q3olSN6hp5It8XA%2BYlOPIGjaaldw%2BEOuhZBVnDJRkjllEPn390xmwUT4Lg8UVEk3T38NM7OT8GqXTaC36LzkLxN6Run%2FW5hGH6NYyxFeo6SXSLit6lN25ukTm5ApjltN592wjrG2SdxgcF%2Bior61J4j1Io9a8zGA6H2yqJGLAULa3m4fKi%2FEA9VIgsuTiA5gjNr1uMPjL5bYGOpoBYURBLLW5WDItyPh15PJ8Vnl7le0lBlsAmF4HikwNgaDGhRLyBR5PtfiQZruNatspcsQ2fPqNG29ggC2zl7RVlAEDiHJLfdQS57vCge9QbLFdHyOztSoEqgdvKYApVme2dC36sXEZOBXSQmj1BXBUsjOUMLmjfxqB8eKzDEZ3P8W0Zw%2BeBbh2t19%2Bmth6Q%2BpWXRVq9Hd7y8HmBA%3D%3D\u0026X-Amz-SignedHeaders=host\u0026X-Amz-Signature=dbc5d0fcb619d221ee80f9c16a0a83267e1d2277d576a0512d501d5934983146",
              ),
            ),
          ),
        ),
      ),
    )
  }
}


