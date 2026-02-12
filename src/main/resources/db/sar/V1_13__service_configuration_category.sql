ALTER TABLE service_configuration
    ADD COLUMN IF NOT EXISTS category varchar(50);

UPDATE service_configuration
    SET category = 'PRISON'
    WHERE category IS NULL;

ALTER TABLE service_configuration
    ALTER COLUMN category SET NOT NULL;
