-- email_verifications
ALTER TABLE email_verifications DROP CONSTRAINT email_verifications_user_id_fkey;
ALTER TABLE email_verifications
    ADD CONSTRAINT email_verifications_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;