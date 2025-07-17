CREATE EXTENSION IF NOT EXISTS "pgcrypto";

ALTER TABLE backlog_request ALTER id SET DEFAULT gen_random_uuid();
ALTER TABLE service_summary ALTER id SET DEFAULT gen_random_uuid();
ALTER TABLE backlog_request ALTER created_at SET DEFAULT now();