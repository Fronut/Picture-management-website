package com.imagemanagement.repository;

import com.imagemanagement.entity.Image;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ImageRepository extends JpaRepository<Image, Long>, JpaSpecificationExecutor<Image> {

	@EntityGraph(attributePaths = {"user", "thumbnails"})
	Optional<Image> findWithUserAndThumbnailsById(Long id);

	boolean existsByUser_IdAndContentHash(Long userId, String contentHash);

	Page<Image> findByUser_IdOrderByUploadTimeDesc(Long userId, Pageable pageable);
}