package com.imagemanagement.service;

import com.imagemanagement.dto.request.AiTagAssignmentRequest;
import com.imagemanagement.dto.request.TagAssignmentRequest;
import com.imagemanagement.dto.response.ImageTagResponse;
import com.imagemanagement.dto.response.TagResponse;
import com.imagemanagement.entity.Image;
import java.util.List;

public interface TagService {

    List<ImageTagResponse> getTagsForImage(Long imageId);

    List<ImageTagResponse> assignCustomTags(Long userId, Long imageId, TagAssignmentRequest request);

    List<ImageTagResponse> assignAiTags(Long userId, Long imageId, AiTagAssignmentRequest request);

    void removeTag(Long userId, Long imageId, Long tagId);

    List<TagResponse> getPopularTags(int limit);

    void applyAutomaticTags(Image image);
}
