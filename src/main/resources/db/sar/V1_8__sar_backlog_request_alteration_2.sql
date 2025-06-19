ALTER TABLE backlog_request DROP CONSTRAINT backlog_request_unique_nomis_id;
ALTER TABLE backlog_request DROP CONSTRAINT backlog_request_unique_delius_id;

ALTER TABLE backlog_request ADD CONSTRAINT backlog_request_unique_nomis_id UNIQUE(sar_case_reference_number, nomis_id, date_from, date_to, version);
ALTER TABLE backlog_request ADD CONSTRAINT backlog_request_unique_delius_id UNIQUE(sar_case_reference_number, ndelius_case_reference_id, date_from, date_to, version);