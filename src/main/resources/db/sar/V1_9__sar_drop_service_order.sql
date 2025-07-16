ALTER TABLE service_summary DROP CONSTRAINT service_summary_unique;
ALTER TABLE service_summary DROP COLUMN service_order;
ALTER TABLE service_summary ADD CONSTRAINT service_summary_unique UNIQUE(backlog_request_id, service_name);