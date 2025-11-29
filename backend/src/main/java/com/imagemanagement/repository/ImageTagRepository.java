package com.imagemanagement.repository;

import com.imagemanagement.entity.ImageTag;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ImageTagRepository extends JpaRepository<ImageTag, Long> {

    @EntityGraph(attributePaths = "tag")
    @Query("SELECT it FROM ImageTag it WHERE it.image.id = :imageId ORDER BY it.tag.tagName ASC")
    List<ImageTag> findAllByImageId(@Param("imageId") Long imageId);

    @Query("SELECT it FROM ImageTag it WHERE it.image.id = :imageId AND it.tag.id = :tagId")
    Optional<ImageTag> findByImageIdAndTagId(@Param("imageId") Long imageId, @Param("tagId") Long tagId);

    boolean existsByImage_IdAndTag_Id(Long imageId, Long tagId);

    void deleteByImage_IdAndTag_Id(Long imageId, Long tagId);
}
