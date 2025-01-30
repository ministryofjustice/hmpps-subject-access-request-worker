CREATE TABLE IF NOT EXISTS service_configuration (
    id UUID PRIMARY KEY,
    service_name TEXT NOT NULL,
    label TEXT NOT NULL,
    url TEXT NOT NULL,
    list_order integer NOT NULL,
    enabled boolean NOT NULL DEFAULT true,
    UNIQUE(service_name),
    UNIQUE(list_order)
);

CREATE INDEX service_config_service_name_index ON service_configuration(service_name);
