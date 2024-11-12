CREATE TABLE IF NOT EXISTS subject_access_request (
   id UUID PRIMARY KEY,
   status TEXT NOT NULL DEFAULT 'Pending',
   date_from DATE,
   date_to DATE NOT NULL DEFAULT CURRENT_DATE,
   sar_case_reference_number TEXT NOT NULL,
   services TEXT NOT NULL,
   nomis_id TEXT,
   ndelius_case_reference_id TEXT,
   requested_by TEXT NOT NULL,
   request_date_time TIMESTAMP with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
   claim_date_time TIMESTAMP with time zone,
   object_url TEXT,
   claim_attempts SMALLINT DEFAULT 0,
   last_downloaded TIMESTAMP with time zone
);

CREATE INDEX status_index ON subject_access_request (status);
CREATE INDEX sar_case_reference_number_index ON subject_access_request (sar_case_reference_number);
CREATE INDEX requested_by_index ON subject_access_request (requested_by);
CREATE INDEX claim_attempts_index ON subject_access_request (claim_attempts);
