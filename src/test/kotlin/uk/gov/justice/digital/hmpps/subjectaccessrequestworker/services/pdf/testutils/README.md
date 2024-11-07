# TemplateTestingUtil

TemplateTestingUtil is a test util class for manually generating of SAR PDFs using stubbed data without submitting a 
request to the SAR api - useful for local dev and testing or templates.

1) Update the configuration file: `src/test/resources/pdf/testutil/config/pdf-util-config.json`
    ```
    {
      "outputDir": "<DIRECTORY_PATH_TO_OUTPUT_FILE>", // (1)
      "services": [
        {
          "name": "hmpps-book-secure-move-api" // (2)
        }
      ]
    }
    ```
   Where:
   1) The directory to write the generated PDF to.
   2) The name of service data to load. **Must match the template name**


2) Create a stub data file under `src/test/resources/pdf/testutil/stubs` **The file must follow naming convention
`<SERVICE_NAME>-stub.json`** 
    
   For example: `hmpps-book-secure-move-api-stub.json`. Populate this file with stubbed JSON for this service 


3) Run the main method of the following class to generate your PDF. 
    ```
    src/test/kotlin/uk/gov/justice/digital/hmpps/subjectaccessrequestworker/services/pdf/testutils/TemplateTestingUtil.kt
    ```
   If successful you should have a pdf