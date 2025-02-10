-- Insert dev env service configuration
INSERT INTO service_configuration (service_name, label, url, list_order)
VALUES ('keyworker-api', 'Keyworker', 'http://localhost:4100', 1),
       ('court-case-service', 'Prepare a Case for Sentence', 'http://localhost:4100', 3),
       ('hmpps-offender-categorisation-api', 'Categorisation Tool', 'http://localhost:4100', 13),
       ('hmpps-resettlement-passport-api', 'Prepare Someone for Release', 'http://localhost:4100', 20),
       ('G1', 'G1', 'G1', 25),
       ('G2', 'G2', 'G2', 26),
       ('G3', 'G3', 'G3', 27);