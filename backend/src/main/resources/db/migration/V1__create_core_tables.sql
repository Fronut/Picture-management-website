CREATE TABLE IF NOT EXISTS users (
    user_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    avatar_url VARCHAR(500),
    registration_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    last_login_time DATETIME,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email),
    INDEX idx_users_username (username),
    INDEX idx_users_email (email)
);

CREATE TABLE IF NOT EXISTS images (
    image_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    stored_filename VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL,
    mime_type VARCHAR(50) NOT NULL,
    upload_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    width INT,
    height INT,
    description TEXT,
    privacy_level VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
    CONSTRAINT fk_images_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
    CONSTRAINT uk_images_stored_filename UNIQUE (stored_filename),
    INDEX idx_images_user_id (user_id),
    INDEX idx_images_upload_time (upload_time)
);

CREATE TABLE IF NOT EXISTS exif_data (
    exif_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    image_id BIGINT NOT NULL,
    camera_make VARCHAR(100),
    camera_model VARCHAR(100),
    taken_time DATETIME,
    exposure_time VARCHAR(50),
    f_number VARCHAR(50),
    iso_speed INT,
    focal_length VARCHAR(50),
    latitude DECIMAL(9, 6),
    longitude DECIMAL(9, 6),
    location_name VARCHAR(200),
    CONSTRAINT fk_exif_image FOREIGN KEY (image_id) REFERENCES images (image_id) ON DELETE CASCADE,
    CONSTRAINT uk_exif_image UNIQUE (image_id),
    INDEX idx_exif_taken_time (taken_time),
    INDEX idx_exif_camera_model (camera_model)
);

CREATE TABLE IF NOT EXISTS tags (
    tag_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tag_name VARCHAR(50) NOT NULL,
    tag_type VARCHAR(20) NOT NULL,
    description VARCHAR(200),
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    usage_count INT DEFAULT 0,
    CONSTRAINT uk_tags_name UNIQUE (tag_name),
    INDEX idx_tags_type (tag_type)
);

CREATE TABLE IF NOT EXISTS image_tags (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    image_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    confidence DECIMAL(3, 2) DEFAULT 1.00,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_image_tags_image FOREIGN KEY (image_id) REFERENCES images (image_id) ON DELETE CASCADE,
    CONSTRAINT fk_image_tags_tag FOREIGN KEY (tag_id) REFERENCES tags (tag_id) ON DELETE CASCADE,
    CONSTRAINT uk_image_tag UNIQUE (image_id, tag_id),
    INDEX idx_image_tags_image_id (image_id),
    INDEX idx_image_tags_tag_id (tag_id)
);

CREATE TABLE IF NOT EXISTS thumbnails (
    thumbnail_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    image_id BIGINT NOT NULL,
    size_type VARCHAR(20) NOT NULL,
    width INT NOT NULL,
    height INT NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_size INT NOT NULL,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_thumbnails_image FOREIGN KEY (image_id) REFERENCES images (image_id) ON DELETE CASCADE,
    INDEX idx_thumbnails_image_id (image_id),
    INDEX idx_thumbnails_size_type (size_type)
);