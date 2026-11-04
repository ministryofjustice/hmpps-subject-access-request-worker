# Backlog Processor

## Loading backlog requests

The Gradlew importBacklog Util task is a command line util that allows you to upload SAR backlog requests to the API from an source CSV.

### Prerequisites
- Requires a valid HMPPS Auth token for the target environment. Token must have the necessary SAR auth roles for the backlog 
endpoints and should have IP restrictions allowing it to be used from your IP - ask HAAR team if in doubt.
- The Backlog API must be enabled. [See below](#enabling-the-backlog-processing)  

### Executing the task
```
    ./gradlew importBacklog \
    --importVersion=$version$ \
    --csv=$import_file$ \
    --env=$target_env$ \
    --token=$auth_token$
```

Task links to an errors csv on completion with details of each if any failed request.

#### Command Options:
| Option            | Required | Description                                                                                           |
|-------------------|----------|-------------------------------------------------------------------------------------------------------|
| `importVersion`   | Y        | A version the imported requests will be grouped under - any String value e.g `"1"`.                   |
| `csv`             | Y        | File path of the csv of requests to import. Must be a `.csv` file                                     |
| `env`             | N        | The target environment to create the requests in (`dev`, `preprod`). Defaults to `dev` if unspecified |
| `token`           | y        | A valid HMPPS Auth token for the target environment                                                   |

## Processing Requests
The backlog processor is a Spring scheduled task that runs on a configurable interval. To process requests simply enable this feature via the [deployment environment vars](#enabling-the-backlog-processing).

## Enabling the backlog processing
The Backlog feature is configured by the following environment vars:

```yaml
    - name: BACKLOG_REQUEST_API_ENABLED
      value: "true"
    - name: BACKLOG_REQUEST_PROCESSOR_BACKOFF_THRESHOLD_MINS
      value: "10"
    - name: BACKLOG_REQUEST_PROCESSOR_ENABLED
      value: "true"
    - name: BACKLOG_REQUEST_PROCESSOR_INITIAL_DELAY
      value: "5"
    - name: BACKLOG_REQUEST_PROCESSOR_INTERVAL
      value: "15"
    - name: BACKLOG_REQUEST_PROCESSOR_POOL_SIZE
      value: "5"
```

Edit the deployment info directly adjust the config as required.

View config
```
kubectl get deployment hmpps-subject-access-request-worker -o yaml -n hmpps-subject-access-request-$ENV
```

Edit config
```
kubectl edit deployment hmpps-subject-access-request-worker -o yaml -n hmpps-subject-access-request-$ENV
```

By default the backlog processor will be disabled by default. To enable/disabled it update `BACKLOG_REQUEST_PROCESSOR_ENABLED` to `"true"`/`"false"`. 
The processor can be started/stopped at any time simply modify the config. When enabled the processor will work out the next PENDING request and continue processing where it left off.

You can use the API endpoint to query the status of the backlog requests: See `sar-backlog-api-requests.http` for examples.

If necessary you can also query the DB directly, below are some useful queries:

#### Query for requests not completed.
```sql
SELECT * FROM backlog_request WHERE status != 'COMPLETE';
```

If a request(s) is stuck in PENDING and not completing you can use the following query to work out which services have 
not yet been queried. With the service name/s you can check the application logs to see if there are any errors coming back in the request.

```sql
SELECT cfg.service_name FROM service_configuration cfg
WHERE NOT EXISTS(
    SELECT s.service_configuration_id FROM backlog_request b 
        INNER JOIN service_summary s ON s.backlog_request_id = b.id
        WHERE b.id = '<BACKLOG_REQ_ID>'
        AND s.service_configuration_id = cfg.id AND s.status = 'COMPLETE'
)
AND cfg.enabled is TRUE;
```

