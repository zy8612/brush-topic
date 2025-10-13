package com.ey.model.entity.topic;

import com.ey.model.entity.BaseEntity;
import lombok.Data;

@Data
public class TopicLabelTopic extends BaseEntity {
    private Long topicId;
    private Long labelId;
}
