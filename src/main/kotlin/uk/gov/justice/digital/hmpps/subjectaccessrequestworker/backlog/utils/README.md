# Gradlew importBacklog Util
Command line util for uploading SAR backlog requests to the API from an input CSV.

### Prerequisites
Requires a valid HMPPS Auth token for the target environment. Token must have the necessary SAR auth roles for the backlog 
endpoints and should have IP restrictions allowing it to be used from your IP - ask HAAR team if in doubt.

### Executing the script

```
    ./gradlew importBacklog \
    --importVersion=$version$ \
    --csv=$import_file$ \
    --env=$target_env$ \
    --token=$auth_token$
```

Script links to an errors csv on completion with details of each if any failed request.

#### Command Options:
| Option            | Required | Description                                                                                           |
|-------------------|----------|-------------------------------------------------------------------------------------------------------|
| `importVersion`   | Y        | The version the imported requests will be grouped under - any String value e.g `"1"`.                 |
| `csv`             | Y        | File path of the csv of requests to import. Must be a `.csv` file                                     |
| `env`             | N        | The target environment to create the requests in (`dev`, `preprod`). Defaults to `dev` if unspecified |
| `token`           | y        | A valid HMPPS Auth token for the target environment                                                   |
