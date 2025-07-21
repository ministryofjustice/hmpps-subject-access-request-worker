ALTER TABLE service_summary ADD COLUMN IF NOT EXISTS service_configuration_id UUID;

UPDATE service_summary s
SET service_configuration_id = (SELECT id FROM service_configuration cfg WHERE s.service_name = cfg.service_name)
WHERE s.service_configuration_id is NULL;

ALTER TABLE service_summary ALTER COLUMN service_configuration_id SET NOT NULL;
ALTER TABLE service_summary DROP CONSTRAINT IF EXISTS service_summary_unique;
ALTER TABLE service_summary DROP COLUMN IF EXISTS service_order;
ALTER TABLE service_summary DROP COLUMN IF EXISTS service_name;
ALTER TABLE service_summary ADD CONSTRAINT service_summary_unique UNIQUE(backlog_request_id, service_configuration_id);
ALTER TABLE service_summary ADD CONSTRAINT fk_service_configuration_id FOREIGN KEY (service_configuration_id) REFERENCES service_configuration (id);