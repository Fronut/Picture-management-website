package com.imagemanagement.repository;

import com.imagemanagement.entity.Thumbnail;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ThumbnailRepository extends JpaRepository<Thumbnail, Long> {

    List<Thumbnail> findByImageId(Long imageId);
}
