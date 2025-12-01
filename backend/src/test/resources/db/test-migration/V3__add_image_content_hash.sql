ALTER TABLE images
    ADD COLUMN content_hash VARCHAR(128) NULL AFTER file_size;

CREATE INDEX idx_images_content_hash ON images (content_hash);
CREATE UNIQUE INDEX uk_images_user_content_hash ON images (user_id, content_hash);
