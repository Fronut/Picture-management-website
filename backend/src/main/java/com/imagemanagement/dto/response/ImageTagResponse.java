package com.imagemanagement.dto.response;

import com.imagemanagement.entity.enums.TagType;
import java.math.BigDecimal;

public record ImageTagResponse(
        Long tagId,
        String tagName,
        TagType tagType,
        Integer usageCount,
        BigDecimal confidence
) {
}
