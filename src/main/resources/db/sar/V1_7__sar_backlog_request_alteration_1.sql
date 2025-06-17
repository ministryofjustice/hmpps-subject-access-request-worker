ALTER TABLE backlog_request ADD COLUMN version TEXT NOT NULL default '1';
ALTER TABLE backlog_request ADD COLUMN subject_name TEXT NOT NULL default '';