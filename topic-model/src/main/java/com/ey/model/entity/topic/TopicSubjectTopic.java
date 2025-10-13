package com.ey.model.entity.topic;

import com.ey.model.entity.BaseEntity;
import lombok.Data;

/**
 * Description: 题目专题与题目的关系表
 */
@Data
public class TopicSubjectTopic extends BaseEntity {
    private Long topicId;
    private Long subjectId;
}
