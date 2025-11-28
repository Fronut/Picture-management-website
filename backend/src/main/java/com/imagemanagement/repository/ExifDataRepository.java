package com.imagemanagement.repository;

import com.imagemanagement.entity.ExifData;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExifDataRepository extends JpaRepository<ExifData, Long> {
}
