ALTER TABLE backlog_request ALTER id SET DEFAULT random_uuid();
ALTER TABLE service_summary ALTER id SET DEFAULT random_uuid();
ALTER TABLE backlog_request ALTER created_at SET DEFAULT CURRENT_TIMESTAMP();