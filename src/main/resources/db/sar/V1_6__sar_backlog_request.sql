CREATE TABLE IF NOT EXISTS backlog_request (
    id UUID PRIMARY KEY,
    sar_case_reference_number TEXT NOT NULL,
    nomis_id TEXT,
    ndelius_case_reference_id TEXT,
    status TEXT NOT NULL DEFAULT 'PENDING',
    data_held boolean DEFAULT NULL,
    date_from DATE,
    date_to DATE NOT NULL DEFAULT CURRENT_DATE,
    claim_date_time TIMESTAMP DEFAULT NULL,
    created_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP DEFAULT NULL
);

CREATE INDEX backlog_request_status_index ON backlog_request (status);
ALTER TABLE backlog_request ADD CONSTRAINT backlog_request_unique_nomis_id UNIQUE(sar_case_reference_number, nomis_id);
ALTER TABLE backlog_request ADD CONSTRAINT backlog_request_unique_delius_id UNIQUE(sar_case_reference_number, ndelius_case_reference_id);

CREATE TABLE IF NOT EXISTS service_summary (
    id UUID PRIMARY KEY,
    backlog_request_id UUID NOT NULL,
    service_name TEXT NOT NULL,
    service_order INT NOT NULL,
    data_held boolean NOT NULL DEFAULT False,
    status TEXT NOT NULL DEFAULT 'PENDING',
    constraint fk_backlog_subject_access_request foreign key (backlog_request_id) REFERENCES backlog_request (id)
);

CREATE INDEX service_summary_backlog_request_id_index ON service_summary (backlog_request_id);
ALTER TABLE service_summary ADD CONSTRAINT service_summary_unique UNIQUE(backlog_request_id, service_name, service_order);