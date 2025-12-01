package com.imagemanagement.repository.specification;

import com.imagemanagement.dto.request.ImageSearchRequest;
import com.imagemanagement.entity.Image;
import com.imagemanagement.entity.enums.ImagePrivacyLevel;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

public final class ImageSpecifications {

    private ImageSpecifications() {
    }

    public static Specification<Image> build(ImageSearchRequest request, Long userId) {
        ImageSearchRequest criteria = request != null ? request : new ImageSearchRequest();
        return (root, query, cb) -> {
            query.distinct(true);
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(buildVisibilityPredicate(criteria, userId, root.get("user").get("id"), root.get("privacyLevel"), cb));

            if (StringUtils.hasText(criteria.getKeyword())) {
                String keyword = "%" + criteria.getKeyword().trim().toLowerCase() + "%";
                    predicates.add(cb.or(
                            cb.like(cb.lower(root.get("originalFilename")), keyword),
                            cb.like(cb.lower(root.get("description")), keyword)
                    ));
            }

            if (criteria.getUploadedFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("uploadTime"), criteria.getUploadedFrom()));
            }

            if (criteria.getUploadedTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("uploadTime"), criteria.getUploadedTo()));
            }

            if (criteria.getMinWidth() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("width"), criteria.getMinWidth()));
            }

            if (criteria.getMaxWidth() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("width"), criteria.getMaxWidth()));
            }

            if (criteria.getMinHeight() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("height"), criteria.getMinHeight()));
            }

            if (criteria.getMaxHeight() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("height"), criteria.getMaxHeight()));
            }

            Join<Image, ?> exifJoin = null;
            if (StringUtils.hasText(criteria.getCameraMake()) || StringUtils.hasText(criteria.getCameraModel())) {
                exifJoin = root.join("exifData", JoinType.LEFT);
            }

            if (StringUtils.hasText(criteria.getCameraMake()) && exifJoin != null) {
                predicates.add(cb.equal(cb.lower(exifJoin.get("cameraMake")), criteria.getCameraMake().trim().toLowerCase()));
            }

            if (StringUtils.hasText(criteria.getCameraModel()) && exifJoin != null) {
                predicates.add(cb.equal(cb.lower(exifJoin.get("cameraModel")), criteria.getCameraModel().trim().toLowerCase()));
            }

            if (!CollectionUtils.isEmpty(criteria.getTags())) {
                Join<Image, ?> imageTagJoin = root.join("imageTags", JoinType.LEFT);
                Join<?, ?> tagJoin = imageTagJoin.join("tag", JoinType.LEFT);
                predicates.add(tagJoin.get("tagName").in(criteria.getTags()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Predicate buildVisibilityPredicate(ImageSearchRequest request,
                                                       Long userId,
                                                       jakarta.persistence.criteria.Path<Long> ownerIdPath,
                                                       jakarta.persistence.criteria.Path<ImagePrivacyLevel> privacyPath,
                                                       jakarta.persistence.criteria.CriteriaBuilder cb) {
        if (userId == null) {
            return cb.equal(privacyPath, ImagePrivacyLevel.PUBLIC);
        }

        if (Boolean.TRUE.equals(request.getOnlyOwn())) {
            return cb.equal(ownerIdPath, userId);
        }

        if (request.getPrivacyLevel() == ImagePrivacyLevel.PRIVATE) {
            // Only return images that are private and owned by the current user
            return cb.and(
                    cb.equal(privacyPath, ImagePrivacyLevel.PRIVATE),
                    cb.equal(ownerIdPath, userId)
            );
        }

        if (request.getPrivacyLevel() == ImagePrivacyLevel.PUBLIC) {
            return cb.equal(privacyPath, ImagePrivacyLevel.PUBLIC);
        }

        return cb.or(
                cb.equal(privacyPath, ImagePrivacyLevel.PUBLIC),
                cb.equal(ownerIdPath, userId)
        );
    }
}
