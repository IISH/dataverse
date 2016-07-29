BEGIN;

-- Add an expiration column for BuildinUser
-- https://github.com/IQSS/dataverse/issues/3150
alter table builtinuser ADD COLUMN passwordModificationTime TIMESTAMP default CURRENT_TIMESTAMP ;

COMMIT;