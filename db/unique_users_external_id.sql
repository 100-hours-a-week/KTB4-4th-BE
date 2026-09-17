-- Existing duplicate external_id values must be resolved before applying this constraint.
ALTER TABLE users ADD CONSTRAINT uq_users_external_id UNIQUE (external_id);
