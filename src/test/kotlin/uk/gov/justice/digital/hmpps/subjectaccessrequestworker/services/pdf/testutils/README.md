# TemplateTestingUtil

`TemplateTestingUtil` is a test util class that manually generates a SAR PDF using stubbed data in place of calling out 
to real service APIs - It's intended for local development and testing of templates changes.

## Generating a PDF
Update the configuration file: `src/test/resources/integration-tests/template-testing-util/template-testing-config.yml`
 ```yaml
outputDir: <PATH TO WRITE GENERATED PDF TO>
targetServices:
  - <LIST OF SERVICE NAMES TO INCLUDE IN REPORT>
   ...
services:
  - name: service-x
  businessName: Service X
  order: 1
   ...
 ```
   Where:
   - `outputDir` - is the filepath to write the generated PDF to.
   - `targetServices` - is a list of service names to include in the report. (See `services` list).

   The script will use stubbed data for each `targetServices` you've specified. The stub files are located under 
   ```
   src/test/resources/integration-tests/api-response-stubs
   ``` 
The stub files **must** follow the naming convention or will not be found by the script.
   ```
   ${service.name}-stub.json
   ```
For example:
```
src/test/resources/integration-tests/api-response-stubs/keyworker-api-stub.json
```
Once you've updated the config run the main method of the `TemplateTestingUtil` class to generate your PDF: 
 ```
 src/test/kotlin/uk/gov/justice/digital/hmpps/subjectaccessrequestworker/services/pdf/testutils/TemplateTestingUtil.kt
 ```

If successful the generated the PDF will be written to the `outputDir` location you specified in your config.

## Adding new services
To add a new service update `template-testing-config.yml` appending an entry to the `services` list for your new service:
```yaml
## Example
...
services:
 - name: service-y
   businessName: Service Y
   order: 2
...
```
Create a stub response json file in the `api-response-stubs dir`
```bash
## Example
/resources/integration-tests/api-response-stubs/service-y-api-stub.json
```


