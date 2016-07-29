BEGIN;

-- Add an expiration column for BuildinUser
-- https://github.com/IQSS/dataverse/issues/3150
alter table builtinuser ADD COLUMN expireTime DATE default CURRENT_DATE;

COMMIT;