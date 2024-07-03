# hmpps-subject-access-request-worker
[![repo standards badge](https://img.shields.io/badge/dynamic/json?color=blue&style=flat&logo=github&label=MoJ%20Compliant&query=%24.result&url=https%3A%2F%2Foperations-engineering-reports.cloud-platform.service.justice.gov.uk%2Fapi%2Fv1%2Fcompliant_public_repositories%2Fhmpps-subject-access-request-worker)](https://operations-engineering-reports.cloud-platform.service.justice.gov.uk/public-github-repositories.html#hmpps-subject-access-request-worker "Link to report")
[![CircleCI](https://circleci.com/gh/ministryofjustice/hmpps-subject-access-request-worker/tree/main.svg?style=svg)](https://circleci.com/gh/ministryofjustice/hmpps-subject-access-request-worker)
[![Docker Repository on Quay](https://quay.io/repository/hmpps/hmpps-subject-access-request-worker/status "Docker Repository on Quay")](https://quay.io/repository/hmpps/hmpps-subject-access-request-worker)
[![API docs](https://img.shields.io/badge/API_docs_-view-85EA2D.svg?logo=swagger)](https://subject-access-request-worker-dev.hmpps.service.justice.gov.uk/swagger-ui/index.html?configUrl=/v3/api-docs)

This is a Spring Boot application, written in Kotlin, used to do the heavy lifting of extracting data from upstream services
and generating a PDF report.
Interacts with the [Subject Access Request api](https://github.com/ministryofjustice/hmpps-subject-access-request-api).

The project Confluence pages can be found [here](https://dsdmoj.atlassian.net/wiki/spaces/SARS/pages/4771479564/Overview).

## Building

To build the project (without tests):
```
./gradlew clean build -x test
```

## Testing

Run:
```
./gradlew test 
```
## Run
To run the hmpps-subject-access-request-worker, first start the required local services using docker-compose:
```
docker-compose up -d
```

Then open the application-local.yaml file and add the 'username' and 'password' fields to the hmpps-auth section, using
the SYSTEM_CLIENT_ID for the username and the SYSTEM_CLIENT_SECRET for the password. These values can be gotten from the Kubernetes secrets for dev namespace.
Then create a Spring Boot run configuration with active profile of 'local'. Run the service in your chosen IDE.

## Common gradle tasks

To list project dependencies, run:

```
./gradlew dependencies
``` 

To check for dependency updates, run:
```
./gradlew dependencyUpdates --warning-mode all
```

To run an OWASP dependency check, run:
```
./gradlew clean dependencyCheckAnalyze --info
```

To upgrade the gradle wrapper version, run:
```
./gradlew wrapper --gradle-version=<VERSION>
```

To automatically update project dependencies, run:
```
./gradlew useLatestVersions
```


To run Ktlint check:
```
./gradlew ktlintCheck
```
## Service Alerting

### Sentry
The service uses [Sentry.IO](https://ministryofjustice.sentry.io/) to raise alerts in Slack and email for job failures. There is a project and team set up in Sentry specifically for this service called `#subject-access-request`. You can log in (and register if need be) with your MoJ github account [here](https://ministryofjustice.sentry.io/).

Rules for alerts can be configured [here](https://ministryofjustice.sentry.io/alerts/rules/).

For Sentry integration to work it requires the environment variable `SENTRY_DSN` which is configured in Kubernetes.
There is a project for each environment, and the DSN values for each is stored in a Kubernetes secret.
This values for this can be found for dev [here](https://ministryofjustice.sentry.io/settings/projects/dev-worker-subject-access-request/keys/), for preprod [here](https://ministryofjustice.sentry.io/settings/projects/preprod-worker-subject-access-request/keys/),
and for prod [here](https://ministryofjustice.sentry.io/settings/projects/prod-worker-subject-access-request/keys/).


### Application Insights Events
The application sends telemetry information to Azure Application Insights which allows log queries and end-to-end request tracing across services.

https://portal.azure.com/#view/AppInsightsExtension