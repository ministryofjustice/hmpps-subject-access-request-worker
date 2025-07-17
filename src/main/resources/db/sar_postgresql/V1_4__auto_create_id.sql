CREATE EXTENSION IF NOT EXISTS "pgcrypto";

ALTER TABLE service_configuration ALTER id SET DEFAULT gen_random_uuid();
