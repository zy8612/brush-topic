package com.ey.model.entity.topic;

import com.ey.model.entity.BaseEntity;
import lombok.Data;

@Data
public class TopicCategorySubject extends BaseEntity {

    private Long categoryId;
    private Long subjectId;
}
