ALTER TABLE service_configuration
    ADD COLUMN template_migrated boolean NOT NULL DEFAULT false;

CREATE TABLE IF NOT EXISTS template_version
(
    id                       UUID PRIMARY KEY,
    service_configuration_id UUID      NOT NULL,
    created_at               TIMESTAMP NOT NULL DEFAULT CURRENT_DATE,
    status                   TEXT      NOT NULL,
    version                  INTEGER   NOT NULL,
    file_hash                TEXT      NOT NULL,
    constraint fk_service_configuration foreign key (service_configuration_id) REFERENCES service_configuration (id)
);

ALTER TABLE template_version
    ADD CONSTRAINT service_version_unique UNIQUE (service_configuration_id, version);