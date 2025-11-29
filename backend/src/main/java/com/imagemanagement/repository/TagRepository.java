package com.imagemanagement.repository;

import com.imagemanagement.entity.Tag;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TagRepository extends JpaRepository<Tag, Long> {

    Optional<Tag> findByTagNameIgnoreCase(String tagName);

    @Query("SELECT t FROM Tag t ORDER BY t.usageCount DESC, t.createdTime DESC")
    List<Tag> findTopTags(Pageable pageable);

}

