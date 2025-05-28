-- Insert backlog requests
INSERT INTO backlog_request (date_from, date_to, sar_case_reference_number, nomis_id, ndelius_case_reference_id)
VALUES ('2000-01-01', '2025-01-01', 'test-case-001', 'nomis-1', NULL),
       ('2000-01-01', '2025-01-01', 'test-case-002', 'nomis-2', NULL),
       ('2000-01-01', '2025-01-01', 'test-case-003', 'nomis-3', NULL);

-- insert service summary for each backlog request
INSERT INTO backlog_request_service_summary(backlog_request_id, service_name, data_held)
VALUES ((SELECT br.id FROM backlog_request br WHERE br.sar_case_reference_number = 'test-case-001'),'service-1',false),
       ((SELECT br.id FROM backlog_request br WHERE br.sar_case_reference_number = 'test-case-001'), 'service-2', false ),
       ((SELECT br.id FROM backlog_request br WHERE br.sar_case_reference_number = 'test-case-001'),'service-3',true),
       ((SELECT br.id FROM backlog_request br WHERE br.sar_case_reference_number = 'test-case-002'),'service-1',false),
       ((SELECT br.id FROM backlog_request br WHERE br.sar_case_reference_number = 'test-case-002'), 'service-2', false ),
       ((SELECT br.id FROM backlog_request br WHERE br.sar_case_reference_number = 'test-case-002'),'service-3',false),
       ((SELECT br.id FROM backlog_request br WHERE br.sar_case_reference_number = 'test-case-003'),'service-1',true),
       ((SELECT br.id FROM backlog_request br WHERE br.sar_case_reference_number = 'test-case-003'), 'service-2', true ),
       ((SELECT br.id FROM backlog_request br WHERE br.sar_case_reference_number = 'test-case-003'),'service-3',true);
