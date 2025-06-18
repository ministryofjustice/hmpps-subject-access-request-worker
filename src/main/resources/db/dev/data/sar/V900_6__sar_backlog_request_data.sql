-- Insert backlog requests
INSERT INTO backlog_request (date_from, date_to, sar_case_reference_number, nomis_id, ndelius_case_reference_id, subject_name, version)
VALUES ('2000-01-01', '2025-01-01', 'test-case-001', 'nomis-001', NULL, 'Homer Simpson', '1'),
       ('2000-01-01', '2025-01-01', 'test-case-002', 'nomis-002', NULL,'Snake Jailbird', '1'),
       ('2000-01-01', '2025-01-01', 'test-case-003', 'nomis-003', NULL, 'Barney Gumble', '1');