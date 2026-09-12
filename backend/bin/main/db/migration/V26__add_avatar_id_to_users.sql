ALTER TABLE users
    ADD COLUMN avatar_id UUID REFERENCES uploaded_files (id) ON DELETE SET NULL;