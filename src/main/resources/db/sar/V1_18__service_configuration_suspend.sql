ALTER table service_configuration
    ADD COLUMN suspended BOOLEAN NOT NULL DEFAULT FALSE;

ALTER table service_configuration
    ADD COLUMN suspended_at TIMESTAMP WITH TIME ZONE DEFAULT NULL;