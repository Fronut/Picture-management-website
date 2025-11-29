package com.imagemanagement.dto.response;

import com.imagemanagement.entity.enums.TagType;

public record TagResponse(
        Long tagId,
        String tagName,
        TagType tagType,
        Integer usageCount
) {
}
