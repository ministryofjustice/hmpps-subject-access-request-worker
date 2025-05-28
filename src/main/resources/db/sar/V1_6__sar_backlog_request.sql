CREATE TABLE IF NOT EXISTS backlog_request (
   id UUID PRIMARY KEY,
   status TEXT NOT NULL DEFAULT 'PENDING',
   data_held boolean DEFAULT NULL,
   date_from DATE,
   date_to DATE NOT NULL DEFAULT CURRENT_DATE,
   sar_case_reference_number TEXT NOT NULL,
   nomis_id TEXT,
   ndelius_case_reference_id TEXT,
   created_at TIMESTAMP NOT NULL
);

CREATE INDEX backlog_request_status_index ON backlog_request (status);

CREATE TABLE IF NOT EXISTS backlog_request_service_summary (
    id UUID PRIMARY KEY,
    backlog_request_id UUID NOT NULL,
    service_name TEXT NOT NULL,
    data_held boolean NOT NULL DEFAULT False,
    status TEXT NOT NULL DEFAULT 'PENDING',
    constraint fk_backlog_subject_access_request
        foreign key (backlog_request_id)
            REFERENCES backlog_request (id)
);
